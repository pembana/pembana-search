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

/**
 * Explicit case choice; inheritance retains the configured adapter policy.
 *
 * @author Gunnar Hillert
 */
public enum CaseSensitivity {

	/** Use the enclosing request or configured annotation policy. */
	INHERIT,
	/** Compare string values without explicit case folding. */
	SENSITIVE,
	/** Fold string fields and values before comparison. */
	INSENSITIVE;

	/**
	 * Returns the canonical request value for HTTP clients and serializers.
	 * @return lowercase wire value
	 */
	public String wireValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Reads the exact lowercase wire values without boolean or string coercion.
	 * @param value request option
	 * @return corresponding case choice
	 * @throws QueryParseException if the value is not supported
	 */
	public static CaseSensitivity parse(String value) {
		return switch (value) {
			case "inherit" -> INHERIT;
			case "sensitive" -> SENSITIVE;
			case "insensitive" -> INSENSITIVE;
			default -> throw new QueryParseException(DiagnosticCode.INVALID_SEARCH_OPTIONS, null,
					"Case sensitivity must be sensitive, insensitive, or inherit");
		};
	}

}
