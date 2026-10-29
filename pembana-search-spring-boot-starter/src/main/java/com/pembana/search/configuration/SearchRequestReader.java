/*
 * Copyright 2026 Pembana Search contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pembana.search.configuration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import com.pembana.search.InvalidSearchQueryException;
import com.pembana.search.QueryErrorCode;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.QueryParseException;
import com.pembana.search.core.SearchOptions;

/**
 * Strict GET/JSON envelope binding, separate from parsing and JPA type resolution.
 *
 * @author Gunnar Hillert
 */
final class SearchRequestReader {

	private static final String GLOBAL = "caseSensitivity";

	private static final String FIELDS = "fieldCaseSensitivity";

	private final ObjectReader jsonReader;

	/**
	 * Retains application mapper constraints while rejecting duplicates/trailing JSON.
	 * @param mapper application mapper, or an explicitly constructed default mapper
	 */
	SearchRequestReader(JsonMapper mapper) {
		this.jsonReader = mapper.reader()
			.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}

	/**
	 * Binds GET/form parameters or a non-GET JSON envelope; never merges both sources.
	 * @param request current request
	 * @param searchParam configured GET expression parameter name
	 * @param jsonBody whether the argument explicitly owns a JSON search body
	 * @return immutable bound expression/options
	 */
	BoundSearch read(NativeWebRequest request, String searchParam, boolean jsonBody) {
		HttpServletRequest servlet = request.getNativeRequest(HttpServletRequest.class);
		if (jsonBody) {
			if (servlet == null || "GET".equals(servlet.getMethod()) || !isJson(servlet.getContentType())) {
				throw invalid(null, "JSON search body binding requires a non-GET application/json request");
			}
			for (String name : request.getParameterMap().keySet()) {
				if (name.equals(searchParam) || name.equals("search") || name.equals(GLOBAL)
						|| name.startsWith(FIELDS)) {
					throw invalid(null, "JSON search options cannot be combined with search query parameters");
				}
			}
			return readJson(servlet);
		}
		Map<String, CaseSensitivity> fields = new LinkedHashMap<>();
		for (String name : request.getParameterMap().keySet()) {
			if (name.startsWith(FIELDS)) {
				if (!name.startsWith(FIELDS + "[") || !name.endsWith("]")) {
					throw invalid(null, "Property case options must use fieldCaseSensitivity[property]");
				}
				String field = name.substring(FIELDS.length() + 1, name.length() - 1);
				if (field.isEmpty() || field.contains("[") || field.contains("]")) {
					throw invalid(field, "Property case options require a non-empty property path");
				}
				fields.put(field, choice(requiredParameter(request, name), field));
			}
		}
		String global = parameter(request, GLOBAL);
		return new BoundSearch(parameter(request, searchParam),
				new SearchOptions((global == null) ? CaseSensitivity.INHERIT : choice(global, null), fields));
	}

	/**
	 * Checks JSON and application/*+json types while preserving ordinary GET handling.
	 * @param contentType content type, possibly absent
	 * @return whether the request carries a JSON envelope
	 */
	private boolean isJson(@Nullable String contentType) {
		if (contentType == null) {
			return false;
		}
		try {
			MediaType type = MediaType.parseMediaType(contentType);
			return "application".equals(type.getType())
					&& ("json".equals(type.getSubtype()) || type.getSubtype().endsWith("+json"));
		}
		catch (IllegalArgumentException exception) {
			throw invalid(null, "Invalid search request content type");
		}
	}

	/**
	 * Reads one JSON object without coercing nulls, booleans, arrays or numbers.
	 * @param request servlet request whose body belongs to the search resolver
	 * @return immutable bound envelope
	 */
	private BoundSearch readJson(HttpServletRequest request) {
		JsonNode body;
		try {
			body = this.jsonReader.readTree(request.getInputStream());
		}
		catch (JacksonException | IOException exception) {
			throw invalid(null, "Invalid JSON search request");
		}
		if (body == null || !body.isObject()) {
			throw invalid(null, "JSON search request must be an object");
		}
		Set<String> allowed = Set.of("search", GLOBAL, FIELDS);
		for (var property : body.properties()) {
			if (!allowed.contains(property.getKey())) {
				throw invalid(null, "Unknown search request property: " + property.getKey());
			}
		}
		JsonNode search = body.get("search");
		JsonNode global = body.get(GLOBAL);
		JsonNode fieldValues = body.get(FIELDS);
		Map<String, CaseSensitivity> fields = new LinkedHashMap<>();
		if (fieldValues != null) {
			if (!fieldValues.isObject()) {
				throw invalid(null, "fieldCaseSensitivity must be an object");
			}
			for (var property : fieldValues.properties()) {
				String field = property.getKey();
				if (field.isEmpty()) {
					throw invalid(field, "Property case options require a non-empty property path");
				}
				fields.put(field, choice(text(property.getValue(), field), field));
			}
		}
		return new BoundSearch((search == null) ? null : text(search, null), new SearchOptions(
				(global == null) ? CaseSensitivity.INHERIT : choice(text(global, null), null), fields));
	}

	/**
	 * Accepts only a JSON string, including when a null token was supplied explicitly.
	 * @param value JSON value
	 * @param field property path for diagnostic metadata, or null
	 * @return uncoerced string
	 */
	private String text(JsonNode value, @Nullable String field) {
		if (!value.isString()) {
			throw invalid(field, "Search and case option values must be strings");
		}
		return value.stringValue();
	}

	/**
	 * Rejects repeated reserved parameters instead of choosing a value silently.
	 * @param request current request
	 * @param name parameter name
	 * @return singleton value, or null if absent
	 */
	private @Nullable String parameter(NativeWebRequest request, String name) {
		String[] values = request.getParameterValues(name);
		if (values == null) {
			return null;
		}
		if (values.length != 1) {
			throw invalid(null, "Search options and expression parameters must occur once");
		}
		return values[0];
	}

	/**
	 * Reads a property parameter discovered in the request map.
	 * @param request current request
	 * @param name parameter name
	 * @return required singleton value
	 */
	private String requiredParameter(NativeWebRequest request, String name) {
		String value = parameter(request, name);
		if (value == null) {
			throw invalid(null, "Missing property case option value");
		}
		return value;
	}

	/**
	 * Adds field metadata to the transport-independent option validation result.
	 * @param value exact wire value
	 * @param field property path, or null for the global choice
	 * @return parsed case choice
	 */
	private CaseSensitivity choice(String value, @Nullable String field) {
		try {
			return CaseSensitivity.parse(value);
		}
		catch (QueryParseException exception) {
			throw invalid(field, exception.getReason());
		}
	}

	/**
	 * Creates a sanitized structured bad-request diagnostic.
	 * @param field property path, or null for an envelope-wide error
	 * @param reason human-readable explanation without raw JSON payloads
	 * @return rejection
	 */
	private InvalidSearchQueryException invalid(@Nullable String field, String reason) {
		return new InvalidSearchQueryException(QueryErrorCode.INVALID_SEARCH_OPTIONS, field, reason);
	}

	/**
	 * Immutable internal binding result; its layout is not a JSON serialization API.
	 *
	 * @param search expression, or null if omitted
	 * @param options parsed choices
	 * @author Gunnar Hillert
	 */
	record BoundSearch(@Nullable String search, SearchOptions options) {
	}

}
