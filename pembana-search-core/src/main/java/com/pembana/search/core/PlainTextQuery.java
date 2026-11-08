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

import org.jspecify.annotations.Nullable;

/**
 * Framework-independent composition: AND patterns, OR fields for each pattern. Empty
 * patterns explicitly mean no text restriction. Existing Query variants are unchanged.
 *
 * @param fields eligible String paths
 * @param patterns all required patterns
 * @author Gunnar Hillert
 */
public record PlainTextQuery(List<String> fields, List<TextPattern> patterns) {
	/**
	 * Copies the composed operands.
	 * @param fields eligible fields
	 * @param patterns required patterns
	 */
	public PlainTextQuery {
		fields = List.copyOf(fields);
		patterns = List.copyOf(patterns);
		if (!patterns.isEmpty() && fields.isEmpty()) {
			throw new IllegalArgumentException("Nonblank plain text requires eligible fields");
		}
	}

	/**
	 * Composes without reparsing values as advanced expressions. Whitespace follows
	 * Java's Unicode whitespace class.
	 * @param text nullable raw input
	 * @param configuration immutable consumer policy
	 * @param policy existing field and input-length restrictions
	 * @return composed query, possibly neutral
	 */
	public static PlainTextQuery compose(@Nullable String text, SearchConfiguration configuration,
			ParserOptions policy) {
		if (text != null && policy.maxQueryLength() > 0 && text.length() > policy.maxQueryLength()) {
			throw new QueryParseException(DiagnosticCode.QUERY_LIMIT_EXCEEDED, null,
					"Plain text exceeds maximum length");
		}
		String trimmed = (text == null) ? "" : trimWhitespace(text);
		if (trimmed.isEmpty()) {
			return new PlainTextQuery(configuration.textFields(), List.of());
		}
		List<TextPattern> patterns = trimmed.contains("*") ? List.of(new TextPattern(trimmed, true))
				: java.util.Arrays.stream(trimmed.split("(?U)\\s+"))
					.map((word) -> new TextPattern(word, false))
					.toList();
		if ((long) patterns.size() * configuration.textFields().size() > configuration.maxTextPredicates()) {
			throw new QueryParseException(DiagnosticCode.QUERY_LIMIT_EXCEEDED, null,
					"Plain text exceeds predicate budget");
		}
		configuration.textFields().forEach(policy::verifyField);
		return new PlainTextQuery(configuration.textFields(), patterns);
	}

	private static String trimWhitespace(String text) {
		int start = 0;
		int end = text.length();
		while (start < end && isUnicodeWhitespace(text.charAt(start))) {
			start++;
		}
		while (end > start && isUnicodeWhitespace(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(start, end);
	}

	private static boolean isUnicodeWhitespace(char character) {
		// Unicode White_Space adds these controls to the separator categories.
		return (character >= '\t' && character <= '\r') || character == '\u0085' || Character.isSpaceChar(character);
	}
}
