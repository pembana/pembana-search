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
import java.util.Objects;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.ExpressionParser;
import com.pembana.search.core.SearchOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises explicit case choices on real attributes without requiring the starter.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class CaseOptionsJpaTest {

	@Autowired
	UsersRepository users;

	@Autowired
	AuthorRepository authors;

	@Autowired
	EntityManager entityManager;

	/** Creates case variants, nullable text and independent mandatory-scope values. */
	@BeforeEach
	void seed() {
		this.users.save(new Users().withUserFirstName("Ada").withUserLastName("One").withUserAddress("A"));
		this.users.save(new Users().withUserFirstName("ADA").withUserLastName("oNe").withUserAddress("B"));
		this.users.save(new Users().withUserFirstName("Beta").withUserLastName("Two").withUserAddress("A"));
		this.users.save(new Users().withUserFirstName("Gamma").withUserLastName(null).withUserAddress("B"));
	}

	/** Existing no-options defaults remain case-sensitive. */
	@Test
	void defaultsRemainUnchanged() {
		assertEquals(List.of(), names("userFirstName:ada", SearchOptions.defaults(), true));
		assertEquals(List.of("Ada"), names("userFirstName:Ada", SearchOptions.defaults(), true));
		assertEquals(List.of(), names("userFirstName IN [ada]", SearchOptions.defaults(), false));
	}

	/** Explicit global choices override both true and false configured fallback flags. */
	@Test
	void globalOverridesConfiguredFlag() {
		assertEquals(List.of("ADA", "Ada"), names("userFirstName:aDa", options(CaseSensitivity.INSENSITIVE), true));
		assertEquals(List.of(), names("userFirstName:aDa", options(CaseSensitivity.SENSITIVE), false));
		assertEquals(List.of("ADA", "Ada"), names("userFirstName:*AD*", options(CaseSensitivity.INSENSITIVE), true));
		assertEquals(List.of("ADA"), names("userFirstName:*AD*", options(CaseSensitivity.SENSITIVE), false));
	}

	/**
	 * Property choices win over globals while other properties retain the global choice.
	 */
	@Test
	void propertiesOverrideGlobal() {
		SearchOptions options = new SearchOptions(CaseSensitivity.INSENSITIVE,
				Map.of("userLastName", CaseSensitivity.SENSITIVE));
		assertEquals(List.of("Ada"), names("userFirstName:ada AND userLastName:One", options, true));
		options = new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of("userFirstName", CaseSensitivity.SENSITIVE));
		assertEquals(List.of("ADA"), names("userFirstName:ADA AND userLastName:One", options, true));
		options = new SearchOptions(CaseSensitivity.SENSITIVE, Map.of("userFirstName", CaseSensitivity.INSENSITIVE));
		assertEquals(List.of("Ada"), names("userFirstName:ada AND userLastName:One", options, true));
	}

	/**
	 * Inherit selects a global choice, or the old annotation policy if global is
	 * inherited.
	 */
	@Test
	void inheritanceFallsThrough() {
		var fields = Map.of("userFirstName", CaseSensitivity.INHERIT);
		assertEquals(List.of("ADA", "Ada"),
				names("userFirstName:ada", new SearchOptions(CaseSensitivity.INSENSITIVE, fields), true));
		assertEquals(List.of(), names("userFirstName:ada", new SearchOptions(CaseSensitivity.INHERIT, fields), true));
		assertEquals(List.of("ADA", "Ada"),
				names("userFirstName:ada", new SearchOptions(CaseSensitivity.INHERIT, fields), false));
	}

	/**
	 * A property choice applies to every occurrence without flattening OR/AND grouping.
	 */
	@Test
	void repeatedPropertiesAndGrouping() {
		SearchOptions choices = new SearchOptions(CaseSensitivity.INSENSITIVE,
				Map.of("userFirstName", CaseSensitivity.SENSITIVE));
		assertEquals(List.of("ADA"),
				names("(userFirstName:ADA OR userFirstName:ada) AND userLastName:ONE", choices, true));
		assertEquals(List.of("ADA", "Beta"),
				names("userFirstName:ADA OR (userFirstName:Beta AND userLastName:TWO)", choices, true));
	}

	/**
	 * Explicit insensitive membership folds both fields and list items, including empty
	 * lists.
	 */
	@Test
	void membershipHonorsExplicitChoices() {
		SearchOptions insensitive = options(CaseSensitivity.INSENSITIVE);
		assertEquals(List.of("ADA", "Ada"), names("userFirstName IN [ada]", insensitive, true));
		assertEquals(List.of("Beta", "Gamma"), names("userFirstName NOT IN [ada]", insensitive, true));
		assertEquals(List.of(), names("userFirstName IN []", insensitive, true));
		assertEquals(4, names("userFirstName NOT IN []", insensitive, true).size());
		assertEquals(List.of("ADA"), names("userFirstName IN [ADA]", options(CaseSensitivity.SENSITIVE), false));
		assertEquals(List.of("Ada", "Beta", "Gamma"),
				names("userFirstName NOT IN [ADA]", options(CaseSensitivity.SENSITIVE), false));
	}

	/**
	 * Explicit choices use consistent folding for all ordering operators and range
	 * expansion.
	 */
	@Test
	void orderingAndRangesHonorExplicitChoices() {
		SearchOptions insensitive = options(CaseSensitivity.INSENSITIVE);
		assertEquals(List.of("ADA", "Ada"), names("userFirstName<beta", insensitive, true));
		assertEquals(List.of("ADA", "Ada", "Beta"), names("userFirstName<:beta", insensitive, true));
		assertEquals(List.of("Gamma"), names("userFirstName>beta", insensitive, true));
		assertEquals(List.of("Beta", "Gamma"), names("userFirstName>:beta", insensitive, true));
		assertEquals(List.of("ADA", "Ada", "Beta"), names("userFirstName BETWEEN 'ada' AND 'beta'", insensitive, true));
		assertEquals(List.of("Gamma"), names("userFirstName NOT BETWEEN 'ada' AND 'beta'", insensitive, true));
	}

	/**
	 * Null checks and membership keep SQL null semantics rather than fabricating text
	 * nulls.
	 */
	@Test
	void nullableFieldsAndNegations() {
		SearchOptions insensitive = options(CaseSensitivity.INSENSITIVE);
		assertEquals(List.of("Gamma"), names("userLastName IS NULL", insensitive, true));
		assertEquals(List.of("ADA", "Ada", "Beta"), names("userLastName IS NOT NULL", insensitive, true));
		assertEquals(List.of("Beta"), names("userLastName NOT IN [one]", insensitive, true));
		assertEquals(List.of("Beta"), names("userLastName!ONE", insensitive, true));
		assertEquals(List.of("Beta"), names("userLastName!*ON*", insensitive, true));
	}

	/** Parsed and programmatic paths resolve the same immutable choices. */
	@Test
	void programmaticAndParsedPathsAgree() {
		SearchOptions choices = new SearchOptions(CaseSensitivity.SENSITIVE,
				Map.of("userFirstName", CaseSensitivity.INSENSITIVE));
		String expression = "userFirstName IN [ada,beta] AND userLastName IS NOT NULL";
		SearchSpec settings = settings(true);
		var built = new SpecificationsBuilder<Users>(settings, choices)
			.withQuery(new ExpressionParser().parse(expression))
			.build();
		assertEquals(names(expression, choices, true), names(built));
	}

	/**
	 * Case options cannot bypass allowed-field/blacklist restrictions or mandatory
	 * scopes.
	 */
	@Test
	void restrictionsAndMandatoryScopesRemainOutsideUserOr() {
		SearchOptions choices = options(CaseSensitivity.INSENSITIVE);
		var restricted = new CriteriaParser<Users>(
				SearchTestFixtures.searchSpec("search", true, new String[] { "userFirstName" }), choices);
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED,
				assertThrows(InvalidSearchQueryException.class, () -> restricted.parse("userFirstName:ada"))
					.getErrorCode());
		var query = new CriteriaParser<Users>(settings(true), choices).parse("userFirstName:ada OR userFirstName:beta");
		Specification<Users> scope = (root, criteria, builder) -> builder.equal(root.get("userAddress"), "A");
		assertEquals(List.of("Ada", "Beta"), names(scope.and(query)));
	}

	/**
	 * Extra option keys and unknown entity attributes are actionable errors, even without
	 * strict mode.
	 */
	@Test
	void rejectsUnknownOptionProperties() {
		var choices = new SearchOptions(CaseSensitivity.INHERIT, Map.of("missing", CaseSensitivity.INSENSITIVE));
		var error = assertThrows(InvalidSearchQueryException.class,
				() -> new CriteriaParser<Users>(settings(true), choices).parse("userFirstName:Ada"));
		assertEquals(QueryErrorCode.INVALID_SEARCH_OPTIONS, error.getErrorCode());
		assertEquals("missing", error.getField());
		var missing = new CriteriaParser<Users>(settings(true), choices).parse("missing:abc");
		assertEquals(QueryErrorCode.UNKNOWN_FIELD,
				assertThrows(InvalidSearchQueryException.class, () -> this.users.findAll(missing)).getErrorCode());
	}

	/**
	 * Globals apply only to text; explicit property choices on other types are rejected.
	 */
	@Test
	void rejectsNonTextPropertyOverrides() {
		var choices = new SearchOptions(CaseSensitivity.INHERIT,
				Map.of("userChildrenNumber", CaseSensitivity.SENSITIVE));
		var query = new CriteriaParser<Users>(settings(true), choices).parse("userChildrenNumber:3");
		assertEquals(QueryErrorCode.INVALID_SEARCH_OPTIONS,
				assertThrows(InvalidSearchQueryException.class, () -> this.users.findAll(query)).getErrorCode());
		assertEquals(4, this.users.count(new CriteriaParser<Users>(settings(true), options(CaseSensitivity.INSENSITIVE))
			.parse("userChildrenNumber:3")));
		assertEquals(4, this.users.count(
				new CriteriaParser<Users>(settings(true), options(CaseSensitivity.INSENSITIVE)).parse("active:true")));
		assertEquals(4, this.users.count(new CriteriaParser<Users>(settings(true),
				new SearchOptions(CaseSensitivity.INHERIT, Map.of("userChildrenNumber", CaseSensitivity.INHERIT)))
			.parse("userChildrenNumber:3")));
	}

	/**
	 * Dotted option keys refer to the complete property path, preserving existing joins.
	 */
	@Test
	void nestedStringProperty() {
		Author author = new Author();
		author.name = "Writer";
		Book book = new Book();
		book.title = "One";
		author.addBook(book);
		this.authors.save(author);
		this.entityManager.flush();
		SearchOptions choices = new SearchOptions(CaseSensitivity.SENSITIVE,
				Map.of("author.name", CaseSensitivity.INSENSITIVE));
		var specification = new CriteriaParser<Book>(settings(true), choices).parse("author.name:WRITER");
		var builder = this.entityManager.getCriteriaBuilder();
		var query = builder.createQuery(Book.class);
		var root = query.from(Book.class);
		query.where(Objects.requireNonNull(specification.toPredicate(root, query, builder)));
		assertEquals(1, this.entityManager.createQuery(query).getResultList().size());
	}

	/**
	 * Builds legacy annotation defaults independently of request options.
	 * @param sensitive configured case flag
	 * @return annotation policy
	 */
	private SearchSpec settings(boolean sensitive) {
		return SearchTestFixtures.searchSpec("search", sensitive, new String[0]);
	}

	/**
	 * Builds a global-only request choice.
	 * @param choice explicit or inherited global policy
	 * @return immutable options
	 */
	private SearchOptions options(CaseSensitivity choice) {
		return new SearchOptions(choice, Map.of());
	}

	/**
	 * Evaluates an expression under independently configured fallback/request settings.
	 * @param expression query to evaluate
	 * @param options request choices
	 * @param sensitive annotation fallback
	 * @return sorted first names
	 */
	private List<String> names(String expression, SearchOptions options, boolean sensitive) {
		return names(new CriteriaParser<Users>(settings(sensitive), options).parse(expression));
	}

	/**
	 * Removes database row-order differences from matching comparisons.
	 * @param specification query to execute
	 * @return sorted first names
	 */
	private List<String> names(Specification<Users> specification) {
		return this.users.findAll(specification).stream().map((user) -> user.userFirstName).sorted().toList();
	}

}
