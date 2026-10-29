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
/*
 * Derived from Spring Search, copyright (c) 2019-present Woody Rousseau
 * and other contributors, under the MIT license. See LICENSE.txt and NOTICE.
 */
package com.pembana.search.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pembana.search.grammar.QueryBaseVisitor;
import com.pembana.search.grammar.QueryParser;

/**
 * Translates the generated parse tree using the inherited advanced-expression semantics.
 *
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public final class QueryModelVisitor extends QueryBaseVisitor<Query> {

	private final ParserOptions options;

	private final Pattern valueRegExp = Pattern.compile("^(?<prefix>\\*?)(?<value>.+?)(?<suffix>\\*?)$");

	/**
	 * Supplies immutable field-access and validation settings.
	 * @param options immutable parser policy
	 */
	public QueryModelVisitor(ParserOptions options) {
		this.options = options;
	}

	/** {@inheritDoc} */
	@Override
	public Query visitOpQuery(QueryParser.OpQueryContext ctx) {
		Query left = visit(ctx.left);
		Query right = visit(ctx.right);
		return "OR".equals(ctx.logicalOp.getText()) ? left.or(right) : left.and(right);
	}

	/** {@inheritDoc} */
	@Override
	public Query visitPriorityQuery(QueryParser.PriorityQueryContext ctx) {
		return visit(ctx.query());
	}

	/** {@inheritDoc} */
	@Override
	public Query visitAtomQuery(QueryParser.AtomQueryContext ctx) {
		return visit(ctx.criteria());
	}

	/** {@inheritDoc} */
	@Override
	public Query visitInput(QueryParser.InputContext ctx) {
		return visit(ctx.query());
	}

	/** {@inheritDoc} */
	@Override
	public Query visitIsCriteria(QueryParser.IsCriteriaContext ctx) {
		String key = ctx.key().getText();
		verifyBlackList(key);
		return toComparison(key, (ctx.IS() != null) ? ComparisonOperator.IS : ComparisonOperator.IS_NOT,
				ctx.is_value().getText());
	}

	/** {@inheritDoc} */
	@Override
	public Query visitEqArrayCriteria(QueryParser.EqArrayCriteriaContext ctx) {
		String key = ctx.key().getText();
		verifyBlackList(key);
		List<String> values = ctx.array()
			.value()
			.stream()
			.map((it) -> (it.STRING() != null) ? clearString(it.getText()) : it.getText())
			.toList();
		return new Membership(key, ctx.IN() == null, values);
	}

	/** {@inheritDoc} */
	@Override
	public Query visitBetweenCriteria(QueryParser.BetweenCriteriaContext ctx) {
		String key = ctx.key().getText();
		verifyBlackList(key);
		String leftValue = (ctx.left.STRING() != null) ? clearString(ctx.left.getText()) : ctx.left.getText();
		String rightValue = (ctx.right.STRING() != null) ? clearString(ctx.right.getText()) : ctx.right.getText();
		Query left = toComparison(key, ComparisonOperator.GREATER_THAN_EQUALS, leftValue);
		Query right = toComparison(key, ComparisonOperator.LESS_THAN_EQUALS, rightValue);
		return (ctx.BETWEEN() != null) ? left.and(right) : left.and(right).not();
	}

	/**
	 * Creates a scalar leaf without adapter-specific type conversion.
	 * @param key entity path
	 * @param op comparison operation
	 * @param value unconverted text
	 * @return immutable comparison
	 */
	private Query toComparison(String key, ComparisonOperator op, String value) {
		return new Comparison(key, op, value);
	}

	/** {@inheritDoc} */
	@Override
	public Query visitOpCriteria(QueryParser.OpCriteriaContext ctx) {
		String key = ctx.key().getText();
		String value = (ctx.value().STRING() != null) ? clearString(ctx.value().getText()) : ctx.value().getText();
		verifyBlackList(key);
		Matcher match = this.valueRegExp.matcher(value);
		ComparisonOperator op = switch (ctx.op().getText()) {
			case ":" -> ComparisonOperator.EQUALS;
			case "!" -> ComparisonOperator.NOT_EQUALS;
			case ">" -> ComparisonOperator.GREATER_THAN;
			case "<" -> ComparisonOperator.LESS_THAN;
			case ">:" -> ComparisonOperator.GREATER_THAN_EQUALS;
			case "<:" -> ComparisonOperator.LESS_THAN_EQUALS;
			default -> throw new IllegalArgumentException("Invalid operation");
		};
		if (!match.find()) {
			if (this.options.strictValidation()) {
				throw new QueryParseException(DiagnosticCode.INVALID_VALUE, key,
						"Empty scalar values are not supported for field " + key);
			}
			throw new NullPointerException();
		}
		boolean starts = match.group("prefix").contains("*");
		boolean ends = match.group("suffix").contains("*");
		if (op == ComparisonOperator.EQUALS) {
			op = (starts && ends) ? ComparisonOperator.CONTAINS
					: starts ? ComparisonOperator.ENDS_WITH : ends ? ComparisonOperator.STARTS_WITH : op;
		}
		if (op == ComparisonOperator.NOT_EQUALS) {
			op = (starts && ends) ? ComparisonOperator.DOESNT_CONTAIN
					: starts ? ComparisonOperator.DOESNT_END_WITH : ends ? ComparisonOperator.DOESNT_START_WITH : op;
		}
		return new Comparison(key, op, match.group("value"));
	}

	/**
	 * Enforces configured paths before creating a model leaf.
	 * @param key entity path
	 */
	private void verifyBlackList(String key) {
		this.options.verifyField(key);
	}

	/**
	 * Retains the inherited quote removal and escaping rules.
	 * @param value token text
	 * @return unquoted text
	 */
	private String clearString(String value) {
		if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
			value = value.substring(1, value.length() - 1);
		}
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1);
		}
		return value.replace("\\\"", "\"").replace("\\'", "'");
	}

}
