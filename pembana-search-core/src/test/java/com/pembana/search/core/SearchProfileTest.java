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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies immutable profile resolution, access rules and case defaults without
 * frameworks.
 *
 * @author Gunnar Hillert
 */
class SearchProfileTest {

	private SearchProfile profile() {
		return new SearchProfile("public",
				Map.of("name", new SearchField("title", EnumSet.allOf(ProfileOperation.class))),
				Map.of("label", "name", "caption", "label"), List.of("name"),
				new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
	}

	/**
	 * Parsed and programmatic trees retain grouping, membership and expanded range
	 * negation.
	 */
	@Test
	void resolvesTrees() {
		var profile = profile();
		assertEquals("public", profile.name());
		Query query = new ExpressionParser().parse("caption IN [A,B] OR name NOT BETWEEN A AND Z");
		Query expected = new Membership("title", false, List.of("A", "B"))
			.or(new Comparison("title", ComparisonOperator.GREATER_THAN_EQUALS, "A")
				.and(new Comparison("title", ComparisonOperator.LESS_THAN_EQUALS, "Z"))
				.not());
		assertEquals(expected, profile.resolve(query, ParserOptions.defaults()));
		assertEquals(new Membership("title", true, List.of()),
				profile.resolve(new Membership("label", true, List.of()), ParserOptions.defaults()));
		assertThrows(QueryParseException.class, () -> profile
			.resolve(new Comparison("title", ComparisonOperator.EQUALS, "A"), ParserOptions.defaults()));
		assertThrows(QueryParseException.class,
				() -> profile.resolve(query, new ParserOptions(false, List.of("title"), List.of("title"), 0, 0)));
		assertEquals(expected, profile.resolve(query, new ParserOptions(false, List.of(), List.of("title"), 0, 0)));
	}

	/**
	 * Explicit request fields and global choices override profile defaults
	 * deterministically.
	 */
	@Test
	void casePrecedenceAndConflicts() {
		var profile = new SearchProfile("cases",
				Map.of("name", new SearchField("title", EnumSet.allOf(ProfileOperation.class))),
				Map.of("label", "name"), List.of(),
				new SearchOptions(CaseSensitivity.SENSITIVE, Map.of("name", CaseSensitivity.INSENSITIVE)));
		var query = new Comparison("title", ComparisonOperator.EQUALS, "A");
		assertEquals(CaseSensitivity.INSENSITIVE, profile.optionsFor(query, SearchOptions.defaults()).resolve("title"));
		assertEquals(CaseSensitivity.SENSITIVE,
				profile.optionsFor(query, new SearchOptions(CaseSensitivity.SENSITIVE, Map.of())).resolve("title"));
		assertEquals(CaseSensitivity.INSENSITIVE,
				profile
					.optionsFor(query,
							new SearchOptions(CaseSensitivity.SENSITIVE, Map.of("label", CaseSensitivity.INSENSITIVE)))
					.resolve("title"));
		assertEquals(CaseSensitivity.SENSITIVE,
				profile
					.optionsFor(query,
							new SearchOptions(CaseSensitivity.SENSITIVE, Map.of("label", CaseSensitivity.INHERIT)))
					.resolve("title"));
		assertThrows(QueryParseException.class,
				() -> profile.optionsFor(query, new SearchOptions(CaseSensitivity.INHERIT,
						Map.of("name", CaseSensitivity.SENSITIVE, "label", CaseSensitivity.INSENSITIVE))));
		assertThrows(QueryParseException.class,
				() -> profile.optionsFor(new Comparison("other", ComparisonOperator.EQUALS, "A"),
						new SearchOptions(CaseSensitivity.INHERIT, Map.of("name", CaseSensitivity.SENSITIVE))));
		assertEquals(CaseSensitivity.INSENSITIVE,
				profile
					.optionsFor(query,
							new SearchOptions(CaseSensitivity.INHERIT,
									Map.of("name", CaseSensitivity.INSENSITIVE, "label", CaseSensitivity.INSENSITIVE)))
					.resolve("title"));
	}

	/**
	 * SIMPLE defaults and consumer fallback fields use the same target permissions and
	 * options.
	 */
	@Test
	void textComposition() {
		var profile = profile();
		var text = profile.composeText("Sabal", SearchConfiguration.defaults(), ParserOptions.defaults());
		assertEquals(List.of("title"), text.fields());
		assertEquals(CaseSensitivity.INSENSITIVE, profile.textOptions(text, SearchOptions.defaults()).resolve("title"));
		assertEquals(1,
				profile.composeText("sab*al", SearchConfiguration.defaults(), ParserOptions.defaults())
					.patterns()
					.size());
		assertEquals(List.of(),
				profile.composeText(null, SearchConfiguration.defaults(), ParserOptions.defaults()).patterns());
		var fallback = new SearchProfile("fallback",
				Map.of("name", new SearchField("title", Set.of(ProfileOperation.CONTAINS))), Map.of(), List.of(),
				SearchOptions.defaults());
		var configuration = new SearchConfiguration(SearchMode.SIMPLE, List.of("name"), 8);
		assertEquals(List.of("title"), fallback.composeText("Sabal", configuration, ParserOptions.defaults()).fields());
		assertThrows(QueryParseException.class,
				() -> fallback.composeText("*", configuration, ParserOptions.defaults()));
		assertThrows(QueryParseException.class, () -> fallback
			.resolve(new Comparison("name", ComparisonOperator.EQUALS, "x"), ParserOptions.defaults()));
	}

	/**
	 * Nested SIMPLE targets retain alias permissions, canonical policy and case options.
	 */
	@Test
	void nestedTextProfiles() {
		var profile = new SearchProfile("nested",
				Map.of("writer", new SearchField("author.name", Set.of(ProfileOperation.CONTAINS))),
				Map.of("alias", "writer"), List.of("alias"),
				new SearchOptions(CaseSensitivity.INHERIT, Map.of("writer", CaseSensitivity.SENSITIVE)));
		var text = profile.composeText("Alice", SearchConfiguration.defaults(), ParserOptions.defaults());
		assertEquals(List.of("author.name"), text.fields());
		assertEquals(CaseSensitivity.SENSITIVE,
				profile.textOptions(text, SearchOptions.defaults()).resolve("author.name"));
		assertEquals(CaseSensitivity.INSENSITIVE,
				profile
					.textOptions(text,
							new SearchOptions(CaseSensitivity.INHERIT, Map.of("alias", CaseSensitivity.INSENSITIVE)))
					.resolve("author.name"));
		assertThrows(QueryParseException.class,
				() -> profile.composeText("*", SearchConfiguration.defaults(), ParserOptions.defaults()));
		assertThrows(QueryParseException.class, () -> profile.composeText("Alice", SearchConfiguration.defaults(),
				new ParserOptions(false, List.of("author.name"), List.of("author.name"), 0, 0)));
		assertThrows(IllegalArgumentException.class,
				() -> new SearchProfile("duplicate", Map.of("writer", new SearchField("author.name", Set.of())),
						Map.of("alias", "writer"), List.of("writer", "alias"), SearchOptions.defaults()));
		var fallback = new SearchProfile("fallback",
				Map.of("writer", new SearchField("children.name", Set.of(ProfileOperation.CONTAINS))), Map.of(),
				List.of(), SearchOptions.defaults());
		assertEquals(List.of("children.name"),
				fallback
					.composeText("Alice", new SearchConfiguration(SearchMode.SIMPLE, List.of("writer"), 1),
							ParserOptions.defaults())
					.fields());
	}

	/**
	 * Invalid links, duplicate public names and incompatible text defaults fail during
	 * construction.
	 */
	@Test
	void configurationFailures() {
		var fields = Map.of("name", new SearchField("title", Set.of()));
		assertThrows(IllegalArgumentException.class, () -> new SearchField("invalid..path", Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new SearchProfile(" ", fields, Map.of(), List.of(), SearchOptions.defaults()));
		for (Map<String, String> links : List.of(Map.of("name", "name"), Map.of("a", "b", "b", "a"),
				Map.of("a", "missing"), Map.of("bad_name", "name"))) {
			assertThrows(IllegalArgumentException.class,
					() -> new SearchProfile("bad", fields, links, List.of(), SearchOptions.defaults()));
		}
		assertThrows(IllegalArgumentException.class, () -> new SearchProfile("bad",
				Map.of("bad_name", new SearchField("title", Set.of())), Map.of(), List.of(), SearchOptions.defaults()));
		assertThrows(IllegalArgumentException.class, () -> new SearchProfile("bad", fields, Map.of("alias", "name"),
				List.of("name", "alias"), SearchOptions.defaults()));

		assertThrows(IllegalArgumentException.class,
				() -> new SearchProfile("bad", fields, Map.of(), List.of("missing"), SearchOptions.defaults()));
	}

	/** Canonical profile paths can contain many segments, including numeric ones. */
	@Test
	void acceptsDeepCanonicalFieldPath() {
		String path = "child.2.".repeat(5_000) + "leaf";
		assertEquals(path, new SearchField(path, Set.of()).path());
	}

	/**
	 * Transformation targets require exposed canonical operations and retain canonical
	 * request/default precedence.
	 */
	@Test
	void extensionTargetsAndOptions() {
		var profile = profile();
		profile.verifyTarget("title", ProfileOperation.EQUALS, ParserOptions.defaults());
		assertThrows(QueryParseException.class,
				() -> profile.verifyTarget("hidden", ProfileOperation.EQUALS, ParserOptions.defaults()));
		assertThrows(QueryParseException.class, () -> profile.verifyTarget("title", ProfileOperation.EQUALS,
				new ParserOptions(false, List.of("title"), List.of(), 0, 0)));
		var query = new Comparison("title", ComparisonOperator.EQUALS, "Ada");
		var canonical = profile
			.canonicalOptions(new SearchOptions(CaseSensitivity.INHERIT, Map.of("label", CaseSensitivity.SENSITIVE)));
		assertEquals(CaseSensitivity.SENSITIVE, canonical.fieldCaseSensitivity().get("title"));
		assertEquals(CaseSensitivity.SENSITIVE, profile.targetOptions(query, canonical).resolve("title"));
		assertEquals(CaseSensitivity.INSENSITIVE,
				profile.targetOptions(query, SearchOptions.defaults()).resolve("title"));
		assertEquals(CaseSensitivity.SENSITIVE,
				profile.targetOptions(query, new SearchOptions(CaseSensitivity.SENSITIVE, Map.of())).resolve("title"));
		var fieldDefaults = new SearchProfile("defaults",
				Map.of("name", new SearchField("title", Set.of(ProfileOperation.EQUALS))), Map.of(), List.of(),
				new SearchOptions(CaseSensitivity.INHERIT, Map.of("name", CaseSensitivity.SENSITIVE)));
		assertEquals(CaseSensitivity.SENSITIVE,
				fieldDefaults.targetOptions(query, SearchOptions.defaults()).resolve("title"));
		assertThrows(QueryParseException.class,
				() -> fieldDefaults.verifyTarget("title", ProfileOperation.CONTAINS, ParserOptions.defaults()));
	}

	/**
	 * Conversion and transformation callbacks work without a JPA or Spring dependency.
	 */
	@Test
	void frameworkIndependentCallbacks() {
		var permissions = java.util.EnumSet.of(ProfileOperation.EQUALS);
		var conversion = new ValueConversion(Long.class, permissions, (text) -> {
			if (text.equals("CAT-2")) {
				return java.util.Optional.of(2L);
			}
			if (text.equals("bad")) {
				throw new InvalidSearchValueException("Invalid catalog code");
			}
			return java.util.Optional.empty();
		});
		permissions.clear();
		assertEquals(Set.of(ProfileOperation.EQUALS), conversion.operations());
		assertEquals(Long.class, conversion.type());
		assertEquals(java.util.Optional.of(2L), conversion.converter().convert("CAT-2"));
		assertEquals(java.util.Optional.empty(), conversion.converter().convert("2"));
		assertEquals("Invalid catalog code",
				assertThrows(InvalidSearchValueException.class, () -> conversion.converter().convert("bad"))
					.getMessage());
		CriterionTransformer transform = (criterion) -> java.util.Optional
			.of(criterion.or(new Comparison("note", ComparisonOperator.EQUALS, "Ada")));
		Query original = new Comparison("title", ComparisonOperator.EQUALS, "Ada");
		assertEquals(java.util.Optional.of(original.or(new Comparison("note", ComparisonOperator.EQUALS, "Ada"))),
				transform.transform(original));
	}

	/**
	 * Defensive copies and parallel resolution keep consumers isolated without request
	 * state.
	 */
	@Test
	void immutableAndConcurrent() {
		var fields = new HashMap<>(Map.of("name", new SearchField("title", EnumSet.allOf(ProfileOperation.class))));
		var aliases = new HashMap<>(Map.of("label", "name"));
		var profile = new SearchProfile("copy", fields, aliases, List.of(), SearchOptions.defaults());
		fields.clear();
		aliases.clear();
		var query = new Comparison("label", ComparisonOperator.EQUALS, "A");
		var expected = new Comparison("title", ComparisonOperator.EQUALS, "A");
		IntStream.range(0, 100)
			.parallel()
			.forEach((index) -> assertEquals(expected, profile.resolve(query, ParserOptions.defaults())));
	}

}
