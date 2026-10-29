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
 * Immutable parser policy; matching and entity-type conversion remain adapter concerns.
 *
 * @param strictValidation whether lexer errors and empty scalars are rejected explicitly
 * @param blackListedFields forbidden paths, copied defensively
 * @param allowedFields allowed paths, or empty for legacy unrestricted access
 * @param maxQueryLength maximum characters, or zero for unlimited input
 * @param maxNestingDepth maximum parentheses depth, or zero for unlimited nesting
 * @author Gunnar Hillert
 */
public record ParserOptions(boolean strictValidation, List<String> blackListedFields, List<String> allowedFields,
		int maxQueryLength, int maxNestingDepth) {
	/**
	 * Copies field policies and validates expression limits.
	 * @param strictValidation whether to enable strict lexical and scalar checks
	 * @param blackListedFields forbidden paths
	 * @param allowedFields allowed paths
	 * @param maxQueryLength maximum characters, or zero
	 * @param maxNestingDepth maximum nesting, or zero
	 * @throws IllegalArgumentException if either limit is negative
	 */
	public ParserOptions {
		if (maxQueryLength < 0 || maxNestingDepth < 0) {
			throw new IllegalArgumentException("Search query limits must be non-negative");
		}
		blackListedFields = List.copyOf(blackListedFields);
		allowedFields = List.copyOf(allowedFields);
	}

	/**
	 * Returns the characterized legacy parser policy.
	 * @return default policy
	 */
	public static ParserOptions defaults() {
		return new ParserOptions(false, List.of(), List.of(), 0, 0);
	}

	/**
	 * Verifies a path using blacklist precedence and exact allowlist matching.
	 * @param field entity path
	 * @throws QueryParseException if the field is forbidden
	 */
	public void verifyField(String field) {
		if (this.blackListedFields.contains(field)) {
			throw new QueryParseException(DiagnosticCode.FIELD_NOT_ALLOWED, field,
					"Field " + field + " is blacklisted");
		}
		if (!this.allowedFields.isEmpty() && !this.allowedFields.contains(field)) {
			throw new QueryParseException(DiagnosticCode.FIELD_NOT_ALLOWED, field,
					"Field " + field + " is not in the allowed fields");
		}
	}
}
