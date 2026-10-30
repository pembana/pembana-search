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

import java.util.Set;

import com.pembana.search.core.ProfileOperation;

/**
 * Immutable logical value type, permissions and factory for a registered field.
 *
 * @param type logical value type, including for a virtual field with no entity attribute
 * @param operations permitted operations
 * @param factory trusted thread-safe factory
 * @author Gunnar Hillert
 */
public record PredicateRegistration(Class<?> type, Set<ProfileOperation> operations, PredicateFactory factory) {
	/**
	 * Copies permissions and validates required configuration.
	 * @param type logical Java type
	 * @param operations allowed operations
	 * @param factory predicate callback
	 */
	public PredicateRegistration {
		java.util.Objects.requireNonNull(type);
		operations = Set.copyOf(operations);
		java.util.Objects.requireNonNull(factory);
	}
}
