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
 * Immutable query tree shared by parsed expressions and programmatic construction. Scalar
 * values retain their text until an adapter resolves the entity attribute type.
 *
 * @author Gunnar Hillert
 */
public sealed interface Query permits Comparison, Membership, Junction, Negation {

	/**
	 * Combines this query with another using AND, preserving grouping.
	 * @param right right operand
	 * @return conjunction
	 */
	default Query and(Query right) {
		return new Junction(this, BooleanOperator.AND, right);
	}

	/**
	 * Combines this query with another using OR, preserving grouping.
	 * @param right right operand
	 * @return disjunction
	 */
	default Query or(Query right) {
		return new Junction(this, BooleanOperator.OR, right);
	}

	/**
	 * Negates this complete query, including any grouping.
	 * @return negated query
	 */
	default Query not() {
		return new Negation(this);
	}

}
