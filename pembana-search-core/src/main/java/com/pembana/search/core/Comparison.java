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
 * A scalar comparison, including explicit NULL or EMPTY state tokens.
 *
 * @param field entity attribute path
 * @param operator explicit comparison operation
 * @param value unconverted scalar text; wildcard operations use their literal body
 * @author Gunnar Hillert
 */
public record Comparison(String field, ComparisonOperator operator, String value) implements Query {
	/**
	 * Creates an immutable comparison.
	 * @param field entity attribute path
	 * @param operator explicit comparison operation
	 * @param value unconverted scalar text; wildcard operations use their literal body
	 * @throws NullPointerException if a required component is null
	 */
	public Comparison {
		java.util.Objects.requireNonNull(field);
		java.util.Objects.requireNonNull(operator);
		java.util.Objects.requireNonNull(value);
	}
}
