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
package com.pembana.search.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable named, opt-in public field policy. Alias targets reference registered public
 * names; canonical paths are exposed only through explicitly registered names.
 *
 * @author Gunnar Hillert
 */
public final class SearchProfile {

	private final String name;

	private final Map<String, SearchField> fields;

	private final List<String> textFields;

	private final SearchOptions defaults;

	/**
	 * Resolves aliases and validates configuration eagerly, without inspecting entity
	 * metadata.
	 * @param name nonblank descriptive profile name
	 * @param fields public names mapped directly to canonical targets and permissions
	 * @param aliases additional names mapped to registered public names or other aliases
	 * @param textFields default SIMPLE public fields; empty uses the consumer's
	 * configured fields
	 * @param defaults profile case defaults; explicit request choices take priority
	 * @throws IllegalArgumentException for duplicate, cyclic, unresolved or incompatible
	 * mappings
	 */
	public SearchProfile(String name, Map<String, SearchField> fields, Map<String, String> aliases,
			List<String> textFields, SearchOptions defaults) {
		if (name.isBlank()) {
			throw new IllegalArgumentException("Profile name must not be blank");
		}
		this.name = name;
		Map<String, SearchField> resolved = new HashMap<>(Map.copyOf(fields));
		Map<String, String> links = Map.copyOf(aliases);
		for (String key : resolved.keySet()) {
			verifyName(key);
			if (links.containsKey(key)) {
				throw new IllegalArgumentException("Duplicate profile name: " + key);
			}
		}
		for (String alias : links.keySet()) {
			verifyName(alias);
			String target = alias;
			Set<String> visited = new HashSet<>();
			while (!resolved.containsKey(target)) {
				if (!visited.add(target) || !links.containsKey(target)) {
					throw new IllegalArgumentException("Cyclic or unresolved alias: " + alias);
				}
				target = links.get(target);
			}
			resolved.put(alias, resolved.get(target));
		}
		this.fields = Map.copyOf(resolved);
		this.textFields = List.copyOf(textFields);
		Set<String> targets = new HashSet<>();
		for (String field : this.textFields) {
			String path = field(field).path();
			if (path.contains(".") || !targets.add(path)) {
				throw new IllegalArgumentException("SIMPLE defaults require unique root fields");
			}
		}
		this.defaults = new SearchOptions(defaults.caseSensitivity(), normalize(defaults.fieldCaseSensitivity()));
	}

	/**
	 * Returns the profile name.
	 * @return descriptive profile name
	 */
	public String name() {
		return this.name;
	}

	private static void verifyName(String name) {
		if (!name.matches("[A-Za-z0-9.]+")) {
			throw new IllegalArgumentException("Invalid public field name: " + name);
		}
	}

	private SearchField field(String name) {
		SearchField result = this.fields.get(name);
		if (result == null) {
			throw new QueryParseException(DiagnosticCode.FIELD_NOT_ALLOWED, name,
					"Field is not exposed by profile " + this.name + ": " + name);
		}
		return result;
	}

	private SearchField permitted(String name, ProfileOperation operation, ParserOptions policy) {
		SearchField result = field(name);
		policy.verifyField(result.path());
		if (!result.operations().contains(operation)) {
			throw new QueryParseException(DiagnosticCode.FIELD_NOT_ALLOWED, name,
					"Operation " + operation + " is not permitted by profile " + this.name + " for " + name);
		}
		return result;
	}

	/**
	 * Resolves an immutable query while preserving grouping, negation and values.
	 * @param query submitted public-name query
	 * @param policy canonical path restrictions
	 * @return equivalent canonical query
	 */
	public Query resolve(Query query, ParserOptions policy) {
		if (query instanceof Comparison comparison) {
			String path = permitted(comparison.field(), ProfileOperation.valueOf(comparison.operator().name()), policy)
				.path();
			return new Comparison(path, comparison.operator(), comparison.value());
		}
		if (query instanceof Membership membership) {
			String path = permitted(membership.field(),
					membership.negated() ? ProfileOperation.NOT_IN : ProfileOperation.IN, policy)
				.path();
			return new Membership(path, membership.negated(), membership.values());
		}
		if (query instanceof Junction junction) {
			return new Junction(resolve(junction.left(), policy), junction.operator(),
					resolve(junction.right(), policy));
		}
		return new Negation(resolve(((Negation) query).query(), policy));
	}

	/**
	 * Composes SIMPLE input using the profile's defaults or the configured public fields.
	 * @param input nullable text
	 * @param configuration mode and predicate budget
	 * @param policy canonical field and input-length restrictions
	 * @return composed canonical text query
	 */
	public PlainTextQuery composeText(@org.jspecify.annotations.Nullable String input,
			SearchConfiguration configuration, ParserOptions policy) {
		List<String> names = this.textFields.isEmpty() ? configuration.textFields() : this.textFields;
		List<String> paths = names.stream().map((name) -> field(name).path()).toList();
		var selected = new SearchConfiguration(configuration.defaultMode(), paths, configuration.maxTextPredicates());
		PlainTextQuery text = PlainTextQuery.compose(input, selected, policy);
		for (var pattern : text.patterns()) {
			for (String name : names) {
				permitted(name, pattern.glob() ? ProfileOperation.GLOB : ProfileOperation.CONTAINS, policy);
			}
		}
		return text;
	}

	/**
	 * Applies request choices and profile defaults to a resolved query.
	 * @param query canonical query returned by resolve
	 * @param request explicit public-name case choices
	 * @return canonical case choices
	 */
	public SearchOptions optionsFor(Query query, SearchOptions request) {
		Set<String> used = new HashSet<>();
		collect(query, used);
		return optionsForFields(used, request);
	}

	/**
	 * Applies request choices and profile defaults to composed SIMPLE fields.
	 * @param query canonical composed text
	 * @param request explicit public-name case choices
	 * @return canonical case choices
	 */
	public SearchOptions textOptions(PlainTextQuery query, SearchOptions request) {
		return optionsForFields(Set.copyOf(query.fields()), request);
	}

	private SearchOptions optionsForFields(Set<String> used, SearchOptions request) {
		Map<String, CaseSensitivity> requested = normalize(request.fieldCaseSensitivity());
		if (!used.containsAll(requested.keySet())) {
			throw new QueryParseException(DiagnosticCode.INVALID_SEARCH_OPTIONS, null,
					"Case option property is not present in the query");
		}
		Map<String, CaseSensitivity> choices = new HashMap<>();
		for (String path : used) {
			CaseSensitivity choice = requested.getOrDefault(path, CaseSensitivity.INHERIT);
			if (choice == CaseSensitivity.INHERIT && request.caseSensitivity() == CaseSensitivity.INHERIT) {
				choice = this.defaults.fieldCaseSensitivity().getOrDefault(path, CaseSensitivity.INHERIT);
			}
			if (choice != CaseSensitivity.INHERIT) {
				choices.put(path, choice);
			}
		}
		CaseSensitivity global = (request.caseSensitivity() == CaseSensitivity.INHERIT)
				? this.defaults.caseSensitivity() : request.caseSensitivity();
		return new SearchOptions(global, choices);
	}

	private Map<String, CaseSensitivity> normalize(Map<String, CaseSensitivity> options) {
		Map<String, CaseSensitivity> result = new HashMap<>();
		for (var entry : options.entrySet()) {
			String path = field(entry.getKey()).path();
			CaseSensitivity previous = result.putIfAbsent(path, entry.getValue());
			if (previous != null && previous != entry.getValue()) {
				throw new QueryParseException(DiagnosticCode.INVALID_SEARCH_OPTIONS, path,
						"Conflicting case options for " + path);
			}
		}
		return result;
	}

	private static void collect(Query query, Set<String> fields) {
		if (query instanceof Comparison comparison) {
			fields.add(comparison.field());
		}
		else if (query instanceof Membership membership) {
			fields.add(membership.field());
		}
		else if (query instanceof Junction junction) {
			collect(junction.left(), fields);
			collect(junction.right(), fields);
		}
		else {
			collect(((Negation) query).query(), fields);
		}
	}

}
