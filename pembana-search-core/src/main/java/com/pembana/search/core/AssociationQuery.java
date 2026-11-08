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
 * Opt-in association query structure preserving ranges and explicit same-child scopes.
 * Existing sealed Query variants remain unchanged. Scoped conditions use full root paths
 * or public profile aliases; the collection path itself is canonical.
 *
 * @author Gunnar Hillert
 */
public sealed interface AssociationQuery permits AssociationQuery.Filter, AssociationQuery.Group, AssociationQuery.Not,
		AssociationQuery.Scope, AssociationQuery.Range {

	/**
	 * Wraps an existing tree; its separate leaves retain independent-child semantics.
	 * @param query immutable existing query
	 * @return association query
	 */
	static AssociationQuery where(Query query) {
		return new Filter(query);
	}

	/**
	 * Requires one actual child to satisfy the complete condition.
	 * @param collection canonical collection path
	 * @param condition child condition using full paths or profile aliases
	 * @return explicit same-child existence scope
	 */
	static AssociationQuery any(String collection, AssociationQuery condition) {
		return new Scope(collection, condition);
	}

	/**
	 * Creates an inclusive range whose bounds always apply to one value.
	 * @param field public or canonical field
	 * @param lower inclusive lower bound
	 * @param upper inclusive upper bound
	 * @return range query
	 */
	static AssociationQuery between(String field, String lower, String upper) {
		return new Range(field, lower, upper);
	}

	/**
	 * Combines conditions with AND, preserving each scope.
	 * @param right right condition
	 * @return conjunction
	 */
	default AssociationQuery and(AssociationQuery right) {
		return new Group(this, BooleanOperator.AND, right);
	}

	/**
	 * Combines conditions with OR, preserving each scope.
	 * @param right right condition
	 * @return disjunction
	 */
	default AssociationQuery or(AssociationQuery right) {
		return new Group(this, BooleanOperator.OR, right);
	}

	/**
	 * Negates the complete condition; negating a scope means no matching child exists.
	 * @return negation
	 */
	default AssociationQuery not() {
		return new Not(this);
	}

	/**
	 * Projects leaves for field/case validation without executing or losing the original
	 * structure.
	 * @return ordinary immutable query tree
	 */
	Query query();

	/**
	 * Existing query tree.
	 *
	 * @param query ordinary query
	 * @author Gunnar Hillert
	 */
	record Filter(Query query) implements AssociationQuery {
		/**
		 * Requires an immutable ordinary query.
		 * @param query existing query tree
		 */
		public Filter {
			java.util.Objects.requireNonNull(query);
		}
	}

	/**
	 * Boolean group preserving explicit scopes.
	 *
	 * @param left left condition
	 * @param operator Boolean operator
	 * @param right right condition
	 * @author Gunnar Hillert
	 */
	record Group(AssociationQuery left, BooleanOperator operator, AssociationQuery right) implements AssociationQuery {
		/**
		 * Requires both operands and the operator.
		 * @param left left condition
		 * @param operator Boolean operator
		 * @param right right condition
		 */
		public Group {
			java.util.Objects.requireNonNull(left);
			java.util.Objects.requireNonNull(operator);
			java.util.Objects.requireNonNull(right);
		}

		/** {@inheritDoc} */
		@Override
		public Query query() {
			return new Junction(this.left.query(), this.operator, this.right.query());
		}
	}

	/**
	 * Complete condition negation.
	 *
	 * @param condition negated condition
	 * @author Gunnar Hillert
	 */
	record Not(AssociationQuery condition) implements AssociationQuery {
		/**
		 * Requires a condition.
		 * @param condition negated condition
		 */
		public Not {
			java.util.Objects.requireNonNull(condition);
		}

		/** {@inheritDoc} */
		@Override
		public Query query() {
			return this.condition.query().not();
		}
	}

	/**
	 * Explicit same-child scope.
	 *
	 * @param collection canonical collection path
	 * @param condition complete condition on one child
	 * @author Gunnar Hillert
	 */
	record Scope(String collection, AssociationQuery condition) implements AssociationQuery {
		/**
		 * Requires a canonical path and condition.
		 * @param collection collection path
		 * @param condition child condition
		 */
		public Scope {
			if (!collection.matches("[A-Za-z0-9]++(?:\\.[A-Za-z0-9]++)*+")) {
				throw new IllegalArgumentException("Invalid collection scope: " + collection);
			}
			java.util.Objects.requireNonNull(condition);
		}

		/** {@inheritDoc} */
		@Override
		public Query query() {
			return this.condition.query();
		}
	}

	/**
	 * Inclusive range kept as one condition.
	 *
	 * @param field field path or alias
	 * @param lower lower bound
	 * @param upper upper bound
	 * @author Gunnar Hillert
	 */
	record Range(String field, String lower, String upper) implements AssociationQuery {
		/**
		 * Requires a field and both bounds.
		 * @param field field path or alias
		 * @param lower lower bound
		 * @param upper upper bound
		 */
		public Range {
			java.util.Objects.requireNonNull(field);
			java.util.Objects.requireNonNull(lower);
			java.util.Objects.requireNonNull(upper);
		}

		/** {@inheritDoc} */
		@Override
		public Query query() {
			return new Comparison(this.field, ComparisonOperator.GREATER_THAN_EQUALS, this.lower)
				.and(new Comparison(this.field, ComparisonOperator.LESS_THAN_EQUALS, this.upper));
		}
	}

}
