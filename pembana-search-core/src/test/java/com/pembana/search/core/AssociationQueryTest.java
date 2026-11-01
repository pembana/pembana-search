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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies additive association structure while preserving legacy parsing and query
 * projections.
 *
 * @author Gunnar Hillert
 */
class AssociationQueryTest {

	/**
	 * Collection range provenance survives parsing while its validation projection equals
	 * the legacy tree.
	 */
	@Test
	void parsingAndProjection() {
		var parser = new AssociationExpressionParser(ParserOptions.defaults());
		for (String input : List.of("children.price BETWEEN 5 AND 10", "children.price NOT BETWEEN -5 AND 10",
				"(children.color:red OR title:Orphan) AND children.owner.name IS NULL", "children.color IN [red,blue]",
				"children.color!*red*", "children.color IS NOT NULL", "children.price BETWEEN '5' AND '10'")) {
			assertEquals(new ExpressionParser().parse(input), parser.parse(input).query());
		}
		assertInstanceOf(AssociationQuery.Range.class, parser.parse("children.price BETWEEN 5 AND 10"));
		assertInstanceOf(AssociationQuery.Not.class, parser.parse("children.price NOT BETWEEN 5 AND 10"));
		Query orphan = new Comparison("title", ComparisonOperator.EQUALS, "Orphan");
		Query red = new Comparison("children.color", ComparisonOperator.EQUALS, "red");
		Query large = new Comparison("children.size", ComparisonOperator.EQUALS, "large");
		assertEquals(orphan.or(red.and(large)),
				parser.parse("title:Orphan OR children.color:red AND children.size:large").query());
		assertEquals(orphan.or(red).and(large),
				parser.parse("(title:Orphan OR children.color:red) AND children.size:large").query());
		var range = AssociationQuery.between("children.price", "5", "10");
		assertEquals(AssociationQuery.where(orphan).or(range.and(AssociationQuery.where(large))),
				parser.parse("title:Orphan OR children.price BETWEEN 5 AND 10 AND children.size:large"));
		assertEquals(AssociationQuery.where(orphan).or(range.not().and(AssociationQuery.where(large))),
				parser.parse("title:Orphan OR children.price NOT BETWEEN 5 AND 10 AND children.size:large"));
		assertThrows(QueryParseException.class, () -> parser.parse("broken"));
		assertThrows(QueryParseException.class,
				() -> new AssociationExpressionParser(new ParserOptions(true, List.of(), List.of(), 3, 1))
					.parse("title:red"));
		assertThrows(QueryParseException.class,
				() -> new AssociationExpressionParser(new ParserOptions(true, List.of("title"), List.of(), 0, 0))
					.parse("title:red"));
	}

	/**
	 * Explicit child scopes compose without adding a variant to the existing sealed Query
	 * interface.
	 */
	@Test
	void explicitScopesAndGrouping() {
		Query red = new Comparison("children.color", ComparisonOperator.EQUALS, "red");
		var scope = AssociationQuery.any("children", AssociationQuery.where(red));
		var range = AssociationQuery.between("children.price", "5", "10");
		var combined = scope.and(range).or(AssociationQuery.where(red.not())).not();
		assertEquals(scope.query().and(range.query()).or(red.not()).not(), combined.query());
		assertThrows(IllegalArgumentException.class, () -> AssociationQuery.any("children..bad", scope));
	}

}
