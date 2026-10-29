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

/**
 * An immutable membership predicate; empty lists retain adapter-specific legacy
 * semantics.
 *
 * @param field entity attribute path
 * @param negated whether this represents NOT IN
 * @param values unconverted item texts, copied defensively
 * @author Gunnar Hillert
 */
public record Membership(String field, boolean negated, java.util.List<String> values) implements Query {
	/**
	 * Creates an immutable membership.
	 * @param field entity attribute path
	 * @param negated whether this represents NOT IN
	 * @param values unconverted item texts, copied defensively
	 * @throws NullPointerException if a required component is null
	 */
	public Membership {
		java.util.Objects.requireNonNull(field);
		values = java.util.List.copyOf(values);
	}
}
