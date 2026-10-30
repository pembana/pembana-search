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
package com.pembana.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;

import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.ProfileOperation;

/**
 * Query-local context for a trusted factory. Never retain it across executions.
 *
 * @param root current root
 * @param query current query, possibly absent in caller contexts
 * @param builder criteria builder
 * @param field resolved canonical or registered virtual field
 * @param operation validated operation
 * @param value converted scalar/list; null for explicit NULL checks
 * @param type declared Java value type
 * @param caseSensitivity explicit matching choice, possibly INHERIT
 * @param legacyCaseSensitive annotation fallback when the choice is INHERIT
 * @author Gunnar Hillert
 */
public record PredicateContext(Root<?> root, @Nullable CriteriaQuery<?> query, CriteriaBuilder builder, String field,
		ProfileOperation operation, @Nullable Object value, Class<?> type, CaseSensitivity caseSensitivity,
		boolean legacyCaseSensitive) {
}
