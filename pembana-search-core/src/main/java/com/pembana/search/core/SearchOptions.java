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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable case choices kept separate from query values and Boolean structure.
 *
 * @param caseSensitivity request-wide choice, or INHERIT for configured defaults
 * @param fieldCaseSensitivity exact query-property choices, copied defensively
 * @author Gunnar Hillert
 */
public record SearchOptions(CaseSensitivity caseSensitivity, Map<String, CaseSensitivity> fieldCaseSensitivity) {
	/**
	 * Creates immutable options without retaining a caller-owned map.
	 * @param caseSensitivity request-wide choice
	 * @param fieldCaseSensitivity per-property choices
	 * @throws NullPointerException if any required component or map entry is null
	 */
	public SearchOptions {
		Objects.requireNonNull(caseSensitivity);
		fieldCaseSensitivity = Map.copyOf(fieldCaseSensitivity);
	}

	/**
	 * Returns options that preserve the existing annotation behavior.
	 * @return inherited case policy and no property choices
	 */
	public static SearchOptions defaults() {
		return new SearchOptions(CaseSensitivity.INHERIT, Map.of());
	}

	/**
	 * Resolves an explicit property choice before the request-wide choice.
	 * @param field exact entity path
	 * @return effective choice, possibly INHERIT for the existing annotation policy
	 */
	public CaseSensitivity resolve(String field) {
		CaseSensitivity choice = this.fieldCaseSensitivity.getOrDefault(field, CaseSensitivity.INHERIT);
		return (choice == CaseSensitivity.INHERIT) ? this.caseSensitivity : choice;
	}

	/**
	 * Identifies explicit per-property choices for entity-type validation.
	 * @param field exact entity path
	 * @return true when the property itself requests sensitive or insensitive matching
	 */
	public boolean hasFieldOverride(String field) {
		return this.fieldCaseSensitivity.getOrDefault(field, CaseSensitivity.INHERIT) != CaseSensitivity.INHERIT;
	}

	/**
	 * Identifies whether legacy no-options behavior is selected.
	 * @return true for inherited global policy and no property entries
	 */
	public boolean isEmpty() {
		return this.caseSensitivity == CaseSensitivity.INHERIT && this.fieldCaseSensitivity.isEmpty();
	}

	/**
	 * Rejects property-option typos and unused property choices before translation.
	 * Property choices must name an exact path present in the query tree.
	 * @param query parsed or programmatically constructed query
	 * @throws QueryParseException if a choice names a property absent from the query
	 */
	public void validate(Query query) {
		Set<String> fields = new HashSet<>();
		collectFields(query, fields);
		for (String field : this.fieldCaseSensitivity.keySet()) {
			if (!fields.contains(field)) {
				throw new QueryParseException(DiagnosticCode.INVALID_SEARCH_OPTIONS, field,
						"Case option property is not present in search: " + field);
			}
		}
	}

	/**
	 * Collects exact property paths without flattening or changing query grouping.
	 * @param query current node
	 * @param fields destination set
	 */
	private static void collectFields(Query query, Set<String> fields) {
		if (query instanceof Comparison comparison) {
			fields.add(comparison.field());
		}
		else if (query instanceof Membership membership) {
			fields.add(membership.field());
		}
		else if (query instanceof Junction junction) {
			collectFields(junction.left(), fields);
			collectFields(junction.right(), fields);
		}
		else {
			collectFields(((Negation) query).query(), fields);
		}
	}
}
