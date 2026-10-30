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

import java.util.List;

/**
 * Immutable consumer default and explicit eligible text fields. No Spring registration is
 * needed.
 *
 * @param defaultMode concrete mode used by inherited requests
 * @param textFields root String properties eligible for plain text
 * @param maxTextPredicates maximum words times fields, positive to bound generated work
 * @author Gunnar Hillert
 */
public record SearchConfiguration(SearchMode defaultMode, List<String> textFields, int maxTextPredicates) {
	/**
	 * Copies fields and rejects unsupported configuration before executing queries.
	 * @param defaultMode concrete default mode
	 * @param textFields root property names
	 * @param maxTextPredicates positive generated predicate budget
	 */
	public SearchConfiguration {
		java.util.Objects.requireNonNull(defaultMode);
		textFields = List.copyOf(textFields);
		if (defaultMode == SearchMode.INHERIT || maxTextPredicates <= 0) {
			throw new IllegalArgumentException("A concrete default mode and positive predicate budget are required");
		}
		if (textFields.stream().distinct().count() != textFields.size()
				|| textFields.stream().anyMatch((field) -> !field.matches("[A-Za-z][A-Za-z0-9]*"))) {
			throw new IllegalArgumentException("Text fields must be unique root property names");
		}
	}

	/**
	 * Returns the library default using the original expression syntax.
	 * @return STANDARD mode with no text fields and a 1024 predicate budget
	 */
	public static SearchConfiguration defaults() {
		return new SearchConfiguration(SearchMode.STANDARD, List.of(), 1024);
	}
}
