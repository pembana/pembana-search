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

import com.pembana.search.grammar.QueryBaseVisitor;
import com.pembana.search.grammar.QueryParser;

/**
 * Opt-in parser preserving BETWEEN as one range with shared AND-before-OR grouping,
 * value decoding, field rules and input limits.
 *
 * @author Gunnar Hillert
 */
public final class AssociationExpressionParser {

	private final ParserOptions options;

	/**
	 * Selects the existing parser policy.
	 * @param options immutable input/field policy
	 */
	public AssociationExpressionParser(ParserOptions options) {
		this.options = options;
	}

	/**
	 * Parses without changing existing ExpressionParser behavior or Query variants.
	 * @param input STANDARD expression
	 * @return structured association query
	 */
	public AssociationQuery parse(String input) {
		return new Visitor(this.options).visit(new ExpressionParser(this.options).parseTree(input));
	}

	private static final class Visitor extends QueryBaseVisitor<AssociationQuery> {

		private final QueryModelVisitor leaves;

		private Visitor(ParserOptions options) {
			this.leaves = new QueryModelVisitor(options);
		}

		@Override
		public AssociationQuery visitInput(QueryParser.InputContext ctx) {
			return visit(ctx.query());
		}

		@Override
		public AssociationQuery visitPriorityQuery(QueryParser.PriorityQueryContext ctx) {
			return visit(ctx.query());
		}

		@Override
		public AssociationQuery visitAtomQuery(QueryParser.AtomQueryContext ctx) {
			return visit(ctx.criteria());
		}

		@Override
		public AssociationQuery visitOpQuery(QueryParser.OpQueryContext ctx) {
			return new AssociationQuery.Group(visit(ctx.left),
					"AND".equals(ctx.logicalOp.getText()) ? BooleanOperator.AND : BooleanOperator.OR, visit(ctx.right));
		}

		@Override
		public AssociationQuery visitOpCriteria(QueryParser.OpCriteriaContext ctx) {
			return AssociationQuery.where(this.leaves.visitOpCriteria(ctx));
		}

		@Override
		public AssociationQuery visitEqArrayCriteria(QueryParser.EqArrayCriteriaContext ctx) {
			return AssociationQuery.where(this.leaves.visitEqArrayCriteria(ctx));
		}

		@Override
		public AssociationQuery visitIsCriteria(QueryParser.IsCriteriaContext ctx) {
			return AssociationQuery.where(this.leaves.visitIsCriteria(ctx));
		}

		@Override
		public AssociationQuery visitBetweenCriteria(QueryParser.BetweenCriteriaContext ctx) {
			Query legacy = this.leaves.visitBetweenCriteria(ctx);
			Junction range = (Junction) ((legacy instanceof Negation negation) ? negation.query() : legacy);
			Comparison lower = (Comparison) range.left();
			Comparison upper = (Comparison) range.right();
			AssociationQuery result = AssociationQuery.between(lower.field(), lower.value(), upper.value());
			return (ctx.BETWEEN() == null) ? result.not() : result;
		}

	}

}
