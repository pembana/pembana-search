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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies immutable request choices without adding transport or persistence
 * dependencies.
 *
 * @author Gunnar Hillert
 */
class SearchOptionsTest {

	/** Valid wire values are explicit, lowercase and distinct from a boolean. */
	@Test
	void parsesWireValues() {
		for (CaseSensitivity choice : CaseSensitivity.values()) {
			assertEquals(choice, CaseSensitivity.parse(choice.wireValue()));
		}
		assertEquals(CaseSensitivity.INHERIT, CaseSensitivity.parse("inherit"));
		assertEquals(CaseSensitivity.SENSITIVE, CaseSensitivity.parse("sensitive"));
		assertEquals(CaseSensitivity.INSENSITIVE, CaseSensitivity.parse("insensitive"));
		for (String value : List.of("", "false", "true", "INSENSITIVE", " insensitive", "other")) {
			var error = assertThrows(QueryParseException.class, () -> CaseSensitivity.parse(value));
			assertEquals(DiagnosticCode.INVALID_SEARCH_OPTIONS, error.getErrorCode());
		}
	}

	/**
	 * Omitting options retains annotation behavior rather than selecting a new default.
	 */
	@Test
	void defaultsInherit() {
		SearchOptions options = SearchOptions.defaults();
		assertTrue(options.isEmpty());
		assertEquals(CaseSensitivity.INHERIT, options.resolve("name"));
		assertFalse(options.hasFieldOverride("name"));
	}

	/** Property choices override globals; inherit falls through without masking false. */
	@Test
	void propertyOverridesGlobal() {
		SearchOptions options = new SearchOptions(CaseSensitivity.INSENSITIVE,
				Map.of("code", CaseSensitivity.SENSITIVE, "name", CaseSensitivity.INHERIT));
		assertEquals(CaseSensitivity.SENSITIVE, options.resolve("code"));
		assertEquals(CaseSensitivity.INSENSITIVE, options.resolve("name"));
		assertEquals(CaseSensitivity.INSENSITIVE, options.resolve("other"));
		assertTrue(options.hasFieldOverride("code"));
		assertFalse(options.hasFieldOverride("name"));
		assertFalse(options.isEmpty());
	}

	/** Inherited global settings still permit a property to request explicit folding. */
	@Test
	void propertyOverridesConfiguredFallback() {
		var options = new SearchOptions(CaseSensitivity.INHERIT, Map.of("name", CaseSensitivity.INSENSITIVE));
		assertFalse(options.isEmpty());
		assertEquals(CaseSensitivity.INSENSITIVE, options.resolve("name"));
		assertEquals(CaseSensitivity.INHERIT, options.resolve("code"));
	}

	/** Defensive copying prevents a client from changing a translated query's policy. */
	@Test
	void optionsAreImmutable() {
		Map<String, CaseSensitivity> fields = new HashMap<>();
		fields.put("name", CaseSensitivity.INSENSITIVE);
		var options = new SearchOptions(CaseSensitivity.SENSITIVE, fields);
		fields.clear();
		assertEquals(CaseSensitivity.INSENSITIVE, options.resolve("name"));
		assertThrows(UnsupportedOperationException.class, () -> options.fieldCaseSensitivity().clear());
	}

	/** Validation sees fields in conjunctions, membership and negated subtrees. */
	@Test
	void validatesCompleteTreeWithoutChangingGrouping() {
		Query query = new Comparison("name", ComparisonOperator.EQUALS, "Ada")
			.and(new Membership("code", false, List.of("A")))
			.or(new Comparison("author.name", ComparisonOperator.EQUALS, "Writer").not());
		new SearchOptions(CaseSensitivity.INHERIT, Map.of("name", CaseSensitivity.SENSITIVE, "code",
				CaseSensitivity.INHERIT, "author.name", CaseSensitivity.INSENSITIVE))
			.validate(query);
		var error = assertThrows(QueryParseException.class,
				() -> new SearchOptions(CaseSensitivity.INHERIT, Map.of("missing", CaseSensitivity.INSENSITIVE))
					.validate(query));
		assertEquals(DiagnosticCode.INVALID_SEARCH_OPTIONS, error.getErrorCode());
		assertEquals("missing", error.getField());
	}

}
