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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes literal text, anchored globs, neutral input and bounded composition.
 *
 * @author Gunnar Hillert
 */
class PlainTextTest {

	/**
	 * Stars match entire values including line breaks; other punctuation stays literal.
	 */
	@Test
	void patternSemantics() {
		assertTrue(new TextPattern("aba", false).matches("Sabal", false));
		assertFalse(new TextPattern("aba", false).matches("SABAL", true));
		assertFalse(new TextPattern("aba*", true).matches("Sabal", false));
		assertTrue(new TextPattern("*bal", true).matches("Sabal", false));
		assertTrue(new TextPattern("sab**al", true).matches("Sab\nal", false));
		assertFalse(new TextPattern("sab*al", true).matches("XSabal", false));
		assertTrue(new TextPattern("*", true).matches("", true));
		assertFalse(new TextPattern("*", true).matches(null, true));
		assertFalse(new TextPattern("sab* min*", true).matches("Sabal", false));
		assertTrue(new TextPattern("sab* min*", true).matches("Sabal minor", false));
		assertTrue(new TextPattern("a.b+", false).matches("a.b+", true));
		assertFalse(new TextPattern("a.b+", false).matches("axb", true));
		assertEquals("%100!%!_!!%", new TextPattern("100%_!", false).sqlLike(true));
		assertEquals("sab%al", new TextPattern("SAB**AL", true).sqlLike(false));
		assertEquals("a*", new TextPattern("a*", false).sqlLike(true).substring(1, 3));
		assertFalse(new TextPattern("*a".repeat(100) + "z", true).matches("a".repeat(100), true));
	}

	/** Whitespace words compose independently, whereas stars preserve internal spaces. */
	@Test
	void compositionAndLimits() {
		var fields = new ArrayList<>(List.of("title", "note"));
		var config = new SearchConfiguration(SearchMode.SIMPLE, fields, 4);
		fields.clear();
		assertEquals(2, config.textFields().size());
		assertEquals(List.of(new TextPattern("minor", false), new TextPattern("sabal", false)),
				PlainTextQuery.compose("  minor\t sabal  ", config, ParserOptions.defaults()).patterns());
		assertEquals(1, PlainTextQuery.compose("sab* min*", config, ParserOptions.defaults()).patterns().size());
		for (String text : List.of("", " ", "\u2003")) {
			assertTrue(PlainTextQuery.compose(text, config, ParserOptions.defaults()).patterns().isEmpty());
		}
		assertTrue(PlainTextQuery.compose(null, config, ParserOptions.defaults()).patterns().isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> PlainTextQuery.compose("x", SearchConfiguration.defaults(), ParserOptions.defaults()));
		assertThrows(QueryParseException.class,
				() -> PlainTextQuery.compose("a b c", config, ParserOptions.defaults()));
		assertThrows(QueryParseException.class,
				() -> PlainTextQuery.compose("long", config, new ParserOptions(false, List.of(), List.of(), 3, 0)));
		assertThrows(QueryParseException.class,
				() -> PlainTextQuery.compose("x", config, new ParserOptions(false, List.of("title"), List.of(), 0, 0)));
		assertThrows(IllegalArgumentException.class, () -> new SearchConfiguration(SearchMode.INHERIT, List.of(), 1));
		assertThrows(IllegalArgumentException.class, () -> new SearchConfiguration(SearchMode.STANDARD, List.of(), 0));
		assertThrows(IllegalArgumentException.class,
				() -> new SearchConfiguration(SearchMode.SIMPLE, List.of("title", "title"), 1));
		var nested = new SearchConfiguration(SearchMode.SIMPLE, List.of("author.name", "children.label"), 4);
		assertEquals(nested.textFields(), PlainTextQuery.compose("a b", nested, ParserOptions.defaults()).fields());
		assertThrows(QueryParseException.class,
				() -> PlainTextQuery.compose("a b c", nested, ParserOptions.defaults()));
		for (String field : List.of(".name", "author.", "author..name", "author.bad_name", "author.0name")) {
			assertThrows(IllegalArgumentException.class,
					() -> new SearchConfiguration(SearchMode.SIMPLE, List.of(field), 1));
		}
	}

}
