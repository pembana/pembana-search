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

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Immutable literal-contains or whole-value star pattern, shared by memory and SQL
 * adapters. Only stars in glob patterns have operator meaning. SQL parameters use '!' as
 * escape character.
 *
 * @param value literal value or glob source
 * @param glob whether stars are wildcard tokens
 * @author Gunnar Hillert
 */
public record TextPattern(String value, boolean glob) {
	/**
	 * Requires a non-null source.
	 * @param value source text
	 * @param glob whether to interpret stars
	 */
	public TextPattern {
		java.util.Objects.requireNonNull(value);
	}

	/**
	 * Generates bound LIKE parameter data; SQL construction belongs to the adapter.
	 * @param sensitive whether to preserve case
	 * @return escaped parameter, for use with ESCAPE '!'
	 */
	public String sqlLike(boolean sensitive) {
		String source = sensitive ? this.value : this.value.toLowerCase(Locale.ROOT);
		StringBuilder result = new StringBuilder();
		if (!this.glob) {
			result.append('%');
		}
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (this.glob && c == '*') {
				if (i == 0 || source.charAt(i - 1) != '*') {
					result.append('%');
				}
			}
			else {
				if (c == '%' || c == '_' || c == '!') {
					result.append('!');
				}
				result.append(c);
			}
		}
		if (!this.glob) {
			result.append('%');
		}
		return result.toString();
	}

	/**
	 * Matches without regular expressions or recursive backtracking using bounded dynamic
	 * programming.
	 * @param candidate nullable field value
	 * @param sensitive whether to preserve case
	 * @return whether the non-null value matches
	 */
	public boolean matches(@Nullable String candidate, boolean sensitive) {
		if (candidate == null) {
			return false;
		}
		String text = sensitive ? candidate : candidate.toLowerCase(Locale.ROOT);
		String pattern = sensitive ? this.value : this.value.toLowerCase(Locale.ROOT);
		if (!this.glob) {
			return text.contains(pattern);
		}
		boolean[] previous = new boolean[text.length() + 1];
		previous[0] = true;
		for (int i = 0; i < pattern.length(); i++) {
			char token = pattern.charAt(i);
			boolean[] next = new boolean[text.length() + 1];
			next[0] = token == '*' && previous[0];
			for (int j = 1; j <= text.length(); j++) {
				next[j] = (token == '*') ? previous[j] || next[j - 1] : previous[j - 1] && token == text.charAt(j - 1);
			}
			previous = next;
		}
		return previous[text.length()];
	}
}
