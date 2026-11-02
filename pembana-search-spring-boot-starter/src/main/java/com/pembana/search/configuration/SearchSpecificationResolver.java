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
/*
 * Derived from Spring Search, copyright (c) 2019-present Woody Rousseau
 * and other contributors, under the MIT license. See LICENSE.txt and NOTICE.
 */
package com.pembana.search.configuration;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

import com.pembana.search.InvalidSearchQueryException;
import com.pembana.search.QueryErrorCode;
import com.pembana.search.SpecificationsBuilder;
import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;

/**
 * Resolves annotated Spring MVC search parameters without imposing consumer authorization
 * scopes.
 *
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author Gunnar Hillert
 */
public final class SearchSpecificationResolver implements HandlerMethodArgumentResolver {

	/** Creates a SearchSpecificationResolver with the inherited default configuration. */
	public SearchSpecificationResolver() {
		this(JsonMapper.builder().build());
	}

	private final SearchRequestReader requestReader;

	private final Map<String, SearchProfile> profiles;

	/**
	 * Creates request binding using the application's Jackson 3 mapper constraints.
	 * @param mapper configured JSON mapper
	 */
	public SearchSpecificationResolver(JsonMapper mapper) {
		this(mapper, List.of());
	}

	/**
	 * Creates binding with immutable named profiles selected by endpoint annotations.
	 * @param mapper configured JSON mapper
	 * @param profiles application profiles with unique SearchProfile names
	 * @throws IllegalArgumentException if multiple profiles have the same name
	 */
	public SearchSpecificationResolver(JsonMapper mapper, Collection<SearchProfile> profiles) {
		this.requestReader = new SearchRequestReader(mapper);
		Map<String, SearchProfile> registered = new HashMap<>();
		for (SearchProfile profile : profiles) {
			if (registered.putIfAbsent(profile.name(), profile) != null) {
				throw new IllegalArgumentException("Duplicate search profile name: " + profile.name());
			}
		}
		this.profiles = Map.copyOf(registered);
	}

	private static final Logger logger = LoggerFactory.getLogger(SearchSpecificationResolver.class);

	/** {@inheritDoc} */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == Specification.class
				&& parameter.hasParameterAnnotation(SearchSpec.class);
	}

	/**
	 * {@inheritDoc}
	 * @return the parsed specification, or null for an absent or empty search parameter
	 * @throws NullPointerException if called directly with a parameter lacking
	 * {@link SearchSpec}; MVC checks {@link #supportsParameter} before resolution
	 */
	@Override
	public @Nullable Specification<?> resolveArgument(MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
			@Nullable WebDataBinderFactory binderFactory) throws Exception {
		SearchSpec def = Objects.requireNonNull(parameter.getParameterAnnotation(SearchSpec.class));
		SearchProfile profile = this.profiles.get(def.profile());
		if (!def.profile().isEmpty() && profile == null) {
			throw new IllegalStateException("Unknown search profile: " + def.profile());
		}
		SearchRequestReader.BoundSearch bound = this.requestReader.read(webRequest, def.searchParam(),
				parameter.hasParameterAnnotation(SearchRequestBody.class));
		return buildSpecification(parameter.getGenericParameterType().getClass(), bound.search(), def, bound.options(),
				profile);
	}

	private <T> @Nullable Specification<T> buildSpecification(Class<T> specClass, @Nullable String search,
			SearchSpec annotation, SearchOptions options, @Nullable SearchProfile profile) {
		logger.debug("Building specification for class {}", specClass);
		logger.debug("Search value found is {}", search);
		if (search == null || search.isEmpty()) {
			if (!options.fieldCaseSensitivity().isEmpty()) {
				throw new InvalidSearchQueryException(QueryErrorCode.INVALID_SEARCH_OPTIONS, null,
						"Property case options require a non-empty search");
			}
			return null;
		}
		var builder = new SpecificationsBuilder<T>(annotation, options);
		return ((profile == null) ? builder.withSearch(search) : builder.withSearch(search, profile)).build();
	}

}
