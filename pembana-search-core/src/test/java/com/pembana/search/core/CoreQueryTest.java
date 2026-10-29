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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares parsed queries with directly constructed trees without Spring or a database.
 *
 * @author Gunnar Hillert
 */
class CoreQueryTest {

	private final ExpressionParser parser = new ExpressionParser();

	/** Verifies equality uses the same immutable scalar model. */
	@Test
	void equality() {
		assertEquals(new Comparison("name", ComparisonOperator.EQUALS, "Ada"), this.parser.parse("name:Ada"));
	}

	/** Verifies inequality uses the same immutable scalar model. */
	@Test
	void inequality() {
		assertEquals(new Comparison("name", ComparisonOperator.NOT_EQUALS, "Ada"), this.parser.parse("name!Ada"));
	}

	/** Verifies greaterThan uses the same immutable scalar model. */
	@Test
	void greaterThan() {
		assertEquals(new Comparison("age", ComparisonOperator.GREATER_THAN, "3"), this.parser.parse("age>3"));
	}

	/** Verifies lessThan uses the same immutable scalar model. */
	@Test
	void lessThan() {
		assertEquals(new Comparison("age", ComparisonOperator.LESS_THAN, "3"), this.parser.parse("age<3"));
	}

	/** Verifies atLeast uses the same immutable scalar model. */
	@Test
	void atLeast() {
		assertEquals(new Comparison("age", ComparisonOperator.GREATER_THAN_EQUALS, "3"), this.parser.parse("age>:3"));
	}

	/** Verifies atMost uses the same immutable scalar model. */
	@Test
	void atMost() {
		assertEquals(new Comparison("age", ComparisonOperator.LESS_THAN_EQUALS, "3"), this.parser.parse("age<:3"));
	}

	/** Verifies startsWith uses the same immutable scalar model. */
	@Test
	void startsWith() {
		assertEquals(new Comparison("name", ComparisonOperator.STARTS_WITH, "Ada"), this.parser.parse("name:Ada*"));
	}

	/** Verifies endsWith uses the same immutable scalar model. */
	@Test
	void endsWith() {
		assertEquals(new Comparison("name", ComparisonOperator.ENDS_WITH, "Ada"), this.parser.parse("name:*Ada"));
	}

	/** Verifies contains uses the same immutable scalar model. */
	@Test
	void contains() {
		assertEquals(new Comparison("name", ComparisonOperator.CONTAINS, "Ada"), this.parser.parse("name:*Ada*"));
	}

	/** Verifies doesNotStartWith uses the same immutable scalar model. */
	@Test
	void doesNotStartWith() {
		assertEquals(new Comparison("name", ComparisonOperator.DOESNT_START_WITH, "Ada"),
				this.parser.parse("name!Ada*"));
	}

	/** Verifies doesNotEndWith uses the same immutable scalar model. */
	@Test
	void doesNotEndWith() {
		assertEquals(new Comparison("name", ComparisonOperator.DOESNT_END_WITH, "Ada"), this.parser.parse("name!*Ada"));
	}

	/** Verifies doesNotContain uses the same immutable scalar model. */
	@Test
	void doesNotContain() {
		assertEquals(new Comparison("name", ComparisonOperator.DOESNT_CONTAIN, "Ada"), this.parser.parse("name!*Ada*"));
	}

	/** Verifies nullCheck uses the same immutable scalar model. */
	@Test
	void nullCheck() {
		assertEquals(new Comparison("name", ComparisonOperator.IS, "NULL"), this.parser.parse("name IS NULL"));
	}

	/** Verifies notNullCheck uses the same immutable scalar model. */
	@Test
	void notNullCheck() {
		assertEquals(new Comparison("name", ComparisonOperator.IS_NOT, "NULL"), this.parser.parse("name IS NOT NULL"));
	}

	/** Verifies emptyCollection uses the same immutable scalar model. */
	@Test
	void emptyCollection() {
		assertEquals(new Comparison("books", ComparisonOperator.IS, "EMPTY"), this.parser.parse("books IS EMPTY"));
	}

	/** Verifies nonEmptyCollection uses the same immutable scalar model. */
	@Test
	void nonEmptyCollection() {
		assertEquals(new Comparison("books", ComparisonOperator.IS_NOT, "EMPTY"),
				this.parser.parse("books IS NOT EMPTY"));
	}

	/** Keeps quoted membership values, ordering and explicit exclusion. */
	@Test
	void membership() {
		assertEquals(new Membership("name", false, List.of("Ada", "Beta")), this.parser.parse("name IN ['Ada',Beta]"));
		assertEquals(new Membership("age", true, List.of("1", "2")), this.parser.parse("age NOT IN [1,2]"));
		assertEquals(new Membership("name", false, List.of()), this.parser.parse("name IN []"));
	}

	/** Keeps inclusive range expansion and whole-range negation. */
	@Test
	void ranges() {
		Query range = new Comparison("age", ComparisonOperator.GREATER_THAN_EQUALS, "1")
			.and(new Comparison("age", ComparisonOperator.LESS_THAN_EQUALS, "3"));
		assertEquals(range, this.parser.parse("age BETWEEN 1 AND 3"));
		assertEquals(range.not(), this.parser.parse("age NOT BETWEEN 1 AND 3"));
		Query text = new Comparison("name", ComparisonOperator.GREATER_THAN_EQUALS, "Ada")
			.and(new Comparison("name", ComparisonOperator.LESS_THAN_EQUALS, "Zoe"));
		assertEquals(text, this.parser.parse("name BETWEEN 'Ada' AND 'Zoe'"));
	}

	/** Retains grouping, operand order and the original grammar's Boolean precedence. */
	@Test
	void booleanComposition() {
		Query a = new Comparison("a", ComparisonOperator.EQUALS, "1");
		Query b = new Comparison("b", ComparisonOperator.EQUALS, "2");
		Query c = new Comparison("c", ComparisonOperator.EQUALS, "3");
		assertEquals(a.or(b).and(c), this.parser.parse("a:1 OR b:2 AND c:3"));
		assertEquals(a.or(b.and(c)), this.parser.parse("a:1 OR (b:2 AND c:3)"));
		assertEquals(a.and(b).or(c), this.parser.parse("a:1 AND b:2 OR c:3"));
	}

	/** Retains spaces, punctuation and legacy quote unescaping in scalar values. */
	@Test
	void quotedScalars() {
		assertEquals(new Comparison("name", ComparisonOperator.EQUALS, "Ada Beta"),
				this.parser.parse("name:'Ada Beta'"));
		assertEquals(new Comparison("name", ComparisonOperator.EQUALS, "Ada Beta"),
				this.parser.parse("name:\"Ada Beta\""));
		assertEquals(new Comparison("name", ComparisonOperator.EQUALS, "O'Reilly"),
				this.parser.parse("name:'O\\'Reilly'"));
		assertEquals(new Comparison("name", ComparisonOperator.EQUALS, "A\"B"), this.parser.parse("name:\"A\\\"B\""));
	}

	/** Defensive copies prevent caller mutation from changing a query or field policy. */
	@Test
	void immutableCollections() {
		var values = new ArrayList<>(List.of("Ada"));
		Membership membership = new Membership("name", false, values);
		ParserOptions options = new ParserOptions(false, values, values, 0, 0);
		values.clear();
		assertEquals(List.of("Ada"), membership.values());
		assertEquals(List.of("Ada"), options.allowedFields());
		assertEquals(List.of("Ada"), options.blackListedFields());
		assertThrows(UnsupportedOperationException.class, () -> membership.values().add("Beta"));
		assertThrows(UnsupportedOperationException.class, () -> options.allowedFields().clear());
		assertThrows(UnsupportedOperationException.class, () -> options.blackListedFields().clear());
	}

	/** Field restrictions retain exact path matching and blacklist precedence. */
	@Test
	void fieldRestrictions() {
		ExpressionParser restricted = new ExpressionParser(
				new ParserOptions(false, List.of("secret"), List.of("name", "secret"), 0, 0));
		QueryParseException blacklisted = assertThrows(QueryParseException.class, () -> restricted.parse("secret:1"));
		assertEquals(DiagnosticCode.FIELD_NOT_ALLOWED, blacklisted.getErrorCode());
		assertEquals("secret", blacklisted.getField());
		assertEquals("Field secret is blacklisted", blacklisted.getReason());
		QueryParseException disallowed = assertThrows(QueryParseException.class, () -> restricted.parse("other:1"));
		assertEquals("Field other is not in the allowed fields", disallowed.getReason());
		assertEquals(this.parser.parse("name:1"), restricted.parse("name:1"));
	}

	/**
	 * Size/depth boundaries retain their exact inclusive limits and negative validation.
	 */
	@Test
	void complexityLimits() {
		assertThrows(IllegalArgumentException.class, () -> new ParserOptions(false, List.of(), List.of(), -1, 0));
		assertThrows(IllegalArgumentException.class, () -> new ParserOptions(false, List.of(), List.of(), 0, -1));
		var sized = new ExpressionParser(new ParserOptions(false, List.of(), List.of(), 3, 0));
		assertEquals(this.parser.parse("a:1"), sized.parse("a:1"));
		var error = assertThrows(QueryParseException.class, () -> sized.parse("a:12"));
		assertEquals(DiagnosticCode.QUERY_LIMIT_EXCEEDED, error.getErrorCode());
		assertNull(error.getField());
		assertEquals(-1, error.getLine());
		assertEquals(-1, error.getColumn());
		var nested = new ExpressionParser(new ParserOptions(false, List.of(), List.of(), 0, 1));
		assertEquals(this.parser.parse("a:1"), nested.parse("(a:1)"));
		assertThrows(QueryParseException.class, () -> nested.parse("((a:1))"));
	}

	/**
	 * Syntax errors carry transport-independent source locations and original reason
	 * text.
	 */
	@Test
	void syntaxLocations() {
		var error = assertThrows(QueryParseException.class, () -> this.parser.parse("name:"));
		assertEquals(DiagnosticCode.SYNTAX_ERROR, error.getErrorCode());
		assertEquals(1, error.getLine());
		assertEquals(5, error.getColumn());
		assertTrue(error.getReason().startsWith("Invalid search query: [line 1:5"));
		assertThrows(QueryParseException.class, () -> this.parser.parse(""));
	}

	/** Empty scalar failures remain explicit only when strict validation is requested. */
	@Test
	void emptyScalars() {
		assertThrows(NullPointerException.class, () -> this.parser.parse("name:''"));
		var strict = new ExpressionParser(new ParserOptions(true, List.of(), List.of(), 0, 0));
		var error = assertThrows(QueryParseException.class, () -> strict.parse("name:''"));
		assertEquals(DiagnosticCode.INVALID_VALUE, error.getErrorCode());
		assertEquals("name", error.getField());
		assertFalse(error.getReason().isEmpty());
		assertEquals(this.parser.parse("name:Ada"), strict.parse("name:Ada"));
	}

	/**
	 * The core test classpath itself must exclude Spring, JPA, JDBC drivers and Kotlin.
	 */
	@Test
	void independentClasspath() {
		for (String type : List.of("org.springframework.context.ApplicationContext", "jakarta.persistence.Entity",
				"org.h2.Driver", "org.postgresql.Driver", "kotlin.Unit")) {
			assertThrows(ClassNotFoundException.class, () -> Class.forName(type), type);
		}
	}

}
