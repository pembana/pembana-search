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

import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.ComparisonOperator;
import com.pembana.search.core.Membership;
import com.pembana.search.core.Query;
import com.pembana.search.grammar.QueryLexer;
import com.pembana.search.grammar.QueryParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies programmatic and parsed queries on the selected database without the starter.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class JpaModelEquivalenceTest {

	private static final SearchSpec SETTINGS = SearchTestFixtures.searchSpec("search", false, new String[0]);

	@Autowired
	UsersRepository users;

	/** Creates identical typed and nullable fixtures for both database profiles. */
	@BeforeEach
	void seed() {
		this.users.save(new Users().withUserFirstName("Ada").withUserAddress("A").withActive(true));
		this.users.save(new Users().withUserFirstName("Beta").withUserAddress("B").withActive(false));
		this.users.save(new Users().withUserFirstName("Ada OR userId:2").withUserAddress("A").withActive(null));
	}

	/**
	 * Both construction paths execute the same predicates, including ranges and nulls.
	 */
	@Test
	void programmaticQueriesMatchParsedExpressions() {
		Query range = new Comparison("userChildrenNumber", ComparisonOperator.GREATER_THAN_EQUALS, "1")
			.and(new Comparison("userChildrenNumber", ComparisonOperator.LESS_THAN_EQUALS, "4"));
		Map<String, Query> cases = Map.ofEntries(
				Map.entry("userFirstName:Ada", new Comparison("userFirstName", ComparisonOperator.EQUALS, "Ada")),
				Map.entry("userFirstName:*AD*", new Comparison("userFirstName", ComparisonOperator.CONTAINS, "AD")),
				Map.entry("userChildrenNumber BETWEEN 1 AND 4", range),
				Map.entry("userChildrenNumber NOT BETWEEN 1 AND 4", range.not()),
				Map.entry("userFirstName IN ['Ada','Beta']",
						new Membership("userFirstName", false, List.of("Ada", "Beta"))),
				Map.entry("userFirstName NOT IN ['Ada']", new Membership("userFirstName", true, List.of("Ada"))),
				Map.entry("active IS NULL", new Comparison("active", ComparisonOperator.IS, "NULL")),
				Map.entry("active:true OR active:false", new Comparison("active", ComparisonOperator.EQUALS, "true")
					.or(new Comparison("active", ComparisonOperator.EQUALS, "false"))));
		cases.forEach((expression, model) -> assertEquals(names(new CriteriaParser<Users>(SETTINGS).parse(expression)),
				names(new SpecificationsBuilder<Users>(SETTINGS).withQuery(model).build()), expression));
	}

	/** Programmatic values remain literal data and mandatory scopes enclose user ORs. */
	@Test
	void literalValuesAndMandatoryScopes() {
		Query literal = new Comparison("userFirstName", ComparisonOperator.EQUALS, "Ada OR userId:2");
		assertEquals(List.of("Ada OR userId:2"), names(new JpaQueryTranslator<Users>(SETTINGS).translate(literal)));
		Query choice = new Comparison("userFirstName", ComparisonOperator.EQUALS, "Ada")
			.or(new Comparison("userFirstName", ComparisonOperator.EQUALS, "Beta"));
		Specification<Users> mandatory = (root, query, builder) -> builder.equal(root.get("userAddress"), "A");
		assertEquals(List.of("Ada"), names(mandatory.and(new JpaQueryTranslator<Users>(SETTINGS).translate(choice))));
	}

	/**
	 * Programmatic construction must not bypass configured searchable-field restrictions.
	 */
	@Test
	void programmaticFieldsRemainRestricted() {
		SearchSpec restricted = SearchTestFixtures.searchSpec("search", false, new String[] { "userAddress" });
		var translator = new JpaQueryTranslator<Users>(restricted);
		var error = assertThrows(InvalidSearchQueryException.class,
				() -> translator.translate(new Comparison("userAddress", ComparisonOperator.EQUALS, "A")));
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED, error.getErrorCode());
		assertEquals("Field userAddress is blacklisted", error.getReason());
	}

	/** Retained visitor entry points delegate to the same model and JPA translation. */
	@Test
	void legacyVisitorEntryPoints() {
		QueryVisitorImpl<Users> visitor = new QueryVisitorImpl<>(SETTINGS);
		for (String expression : List.of("userFirstName:Ada", "(userFirstName:Ada)",
				"userFirstName:Ada OR userFirstName:Beta", "active IS NULL", "userFirstName IN ['Ada']",
				"userChildrenNumber BETWEEN 1 AND 4")) {
			QueryParser parser = new QueryParser(
					new CommonTokenStream(new QueryLexer(CharStreams.fromString(expression))));
			QueryParser.InputContext input = parser.input();
			List<String> expected = names(new CriteriaParser<Users>(SETTINGS).parse(expression));
			assertEquals(expected, names(visitor.visit(input)), expression);
			assertEquals(expected, names(visitor.visit(input.query())), expression);
			if (input.query() instanceof QueryParser.AtomQueryContext atom) {
				assertEquals(expected, names(visitor.visit(atom.criteria())), expression);
			}
		}
		QueryParser blocked = new QueryParser(
				new CommonTokenStream(new QueryLexer(CharStreams.fromString("secret:1"))));
		QueryVisitorImpl<Users> restricted = new QueryVisitorImpl<>(
				SearchTestFixtures.searchSpec("search", false, new String[] { "secret" }));
		assertThrows(InvalidSearchQueryException.class, () -> restricted.visit(blocked.input()));
	}

	/** Proves this integration runs with neither MVC binding nor the optional starter. */
	@Test
	void noStarterOnClasspath() {
		assertThrows(ClassNotFoundException.class,
				() -> Class.forName("com.pembana.search.configuration.ResolverConf"));
	}

	/**
	 * Removes database-dependent ordering from predicate comparisons.
	 * @param specification predicate to execute
	 * @return sorted first names
	 */
	private List<String> names(Specification<Users> specification) {
		return this.users.findAll(specification).stream().map((user) -> user.userFirstName).sorted().toList();
	}

}
