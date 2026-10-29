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
 * An ordered Boolean pair whose tree structure retains expression grouping.
 *
 * @param left left operand
 * @param operator Boolean operation
 * @param right right operand
 * @author Gunnar Hillert
 */
public record Junction(Query left, BooleanOperator operator, Query right) implements Query {
	/**
	 * Creates an immutable junction.
	 * @param left left operand
	 * @param operator Boolean operation
	 * @param right right operand
	 * @throws NullPointerException if a required component is null
	 */
	public Junction {
		java.util.Objects.requireNonNull(left);
		java.util.Objects.requireNonNull(operator);
		java.util.Objects.requireNonNull(right);
	}
}
