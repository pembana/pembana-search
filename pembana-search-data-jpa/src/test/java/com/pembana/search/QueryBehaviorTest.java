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

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.pembana.search.annotation.SearchSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-backed characterization and validation checks shared by both matrix profiles.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class QueryBehaviorTest {

	@Autowired
	UsersRepository users;

	@Autowired
	AuthorRepository authors;

	@Autowired
	EntityManager entityManager;

	/** Defines the unchanged matching policy through a real annotation instance. */
	private void legacy(@SearchSpec Specification<Users> query) {
	}

	/** Enables validation without changing matching. */
	private void strict(@SearchSpec(strictValidation = true) Specification<Users> query) {
	}

	/** Captures the legacy case-insensitive policy. */
	private void insensitive(@SearchSpec(caseSensitiveFlag = false) Specification<Users> query) {
	}

	/** Restricts exactly one field even without enabling strict type checks. */
	private void allowed(@SearchSpec(allowedFields = "userFirstName") Specification<Users> query) {
	}

	/** Ensures a blacklist takes priority over an allowlist. */
	private void denied(@SearchSpec(allowedFields = "userFirstName",
			blackListedFields = "userFirstName") Specification<Users> query) {
	}

	/** Sets a boundary equal to the length of userFirstName:Ada. */
	private void sized(@SearchSpec(maxQueryLength = 17) Specification<Users> query) {
	}

	/** Limits parentheses, independently of strict type validation. */
	private void nested(@SearchSpec(maxNestingDepth = 1) Specification<Users> query) {
	}

	/** Supplies an invalid character limit as a consumer configuration error. */
	private void invalidLength(@SearchSpec(maxQueryLength = -1) Specification<Users> query) {
	}

	/** Supplies an invalid depth limit as a consumer configuration error. */
	private void invalidDepth(@SearchSpec(maxNestingDepth = -1) Specification<Users> query) {
	}

	/**
	 * Creates deterministic tenant, nullable Boolean and text fixtures for each
	 * transaction.
	 */
	@BeforeEach
	void seed() {
		this.users
			.save(new Users().withUserFirstName("Ada").withUserLastName("One").withUserAddress("A").withActive(true));
		this.users
			.save(new Users().withUserFirstName("Beta").withUserLastName("Two").withUserAddress("A").withActive(false));
		this.users.save(
				new Users().withUserFirstName("Gamma").withUserLastName("Three").withUserAddress("B").withActive(true));
		this.users
			.save(new Users().withUserFirstName("Delta").withUserLastName(null).withUserAddress("B").withActive(null));
	}

	/** Checks the annotation defaults as well as the manual compatibility factory. */
	@Test
	void defaultsRemainLegacyAndAnnotationFactoryKeepsItsContract() throws Exception {
		SearchSpec actual = settings("legacy");
		SearchSpec manual = SearchTestFixtures.searchSpec("search", true, new String[0]);
		assertEquals(actual, manual);
		assertEquals(manual, actual);
		assertEquals(actual.hashCode(), manual.hashCode());
		assertFalse(actual.strictValidation());
		assertEquals(0, actual.maxQueryLength());
		assertEquals(0, actual.maxNestingDepth());
		assertEquals(0, actual.allowedFields().length);
	}

	/**
	 * Records that Boolean.valueOf converts arbitrary input to false in compatibility
	 * mode.
	 */
	@Test
	void legacyInvalidBooleanStillMeansFalse() throws Exception {
		assertEquals(List.of("Beta"), names(spec("legacy", "active:garbage")));
	}

	/**
	 * Records that an unsupported Boolean comparison previously contributes no predicate.
	 */
	@Test
	void legacyBooleanOrderingStillHasNoPredicate() throws Exception {
		assertEquals(4, this.users.count(spec("legacy", "active>true")));
	}

	/** Records the historical distinction between wildcard and equality case handling. */
	@Test
	void insensitivePatternsFoldCaseButEqualityDoesNot() throws Exception {
		assertEquals(List.of("Ada"), names(spec("insensitive", "userFirstName:*ADA*")));
		assertEquals(List.of(), names(spec("insensitive", "userFirstName:ADA")));
		assertEquals(List.of("Ada"), names(spec("insensitive", "userFirstName:Ada")));
		assertEquals(0, this.users.count(spec("insensitive", "userFirstName IN [ADA]")));
	}

	/**
	 * Records that SQL percent and underscore remain wildcards inside legacy patterns.
	 */
	@Test
	void sqlMetacharactersStillBroadenLegacyPatterns() throws Exception {
		assertEquals(4, this.users.count(spec("legacy", "userFirstName:'*%*'")));
		assertEquals(List.of("Ada"), names(spec("legacy", "userFirstName:'A_a*'")));
	}

	/** Records that only outer asterisks are translated into SQL wildcards. */
	@Test
	void internalAsterisksRemainLiteral() throws Exception {
		assertEquals(0, this.users.count(spec("legacy", "userFirstName:'A*a'")));
	}

	/** Locks the grammar's equal-precedence left-associative Boolean composition. */
	@Test
	void booleanOperatorsSharePrecedenceAndParenthesesOverrideIt() throws Exception {
		assertEquals(0,
				this.users.count(spec("legacy", "userFirstName:Ada OR userFirstName:Beta AND userFirstName:Gamma")));
		assertEquals(List.of("Ada"),
				names(spec("legacy", "userFirstName:Ada OR (userFirstName:Beta AND userFirstName:Gamma)")));
	}

	/** Checks empty membership semantics including SQL NULL rows. */
	@Test
	void emptyMembershipAndNullableBooleanChecksAreStable() throws Exception {
		assertEquals(0, this.users.count(spec("legacy", "active IN []")));
		assertEquals(4, this.users.count(spec("legacy", "active NOT IN []")));
		assertEquals(List.of("Delta"), names(spec("strict", "active IS NULL")));
		assertEquals(3, this.users.count(spec("strict", "active IS NOT NULL")));
		assertEquals(3, this.users.count(spec("strict", "active IN [true,false]")));
	}

	/**
	 * Ensures caller-owned tenant restrictions remain outside user-controlled OR
	 * expressions.
	 */
	@Test
	void mandatoryScopeComposesOutsideUserOr() throws Exception {
		Specification<Users> tenant = (root, query, builder) -> builder.equal(root.get("userAddress"), "A");
		Specification<Users> search = spec("legacy", "userFirstName:Ada OR userFirstName:Gamma");
		assertEquals(List.of("Ada"), names(tenant.and(search)));
	}

	/**
	 * Executes sorted pages and total counts rather than relying on unspecified row
	 * order.
	 */
	@Test
	void sortedPaginationAndCountsRemainStable() throws Exception {
		Specification<Users> search = spec("strict", "userFirstName:*a*");
		var first = this.users.findAll(search, PageRequest.of(0, 2, Sort.by("userFirstName")));
		var second = this.users.findAll(search, PageRequest.of(1, 2, Sort.by("userFirstName")));
		assertEquals(4, first.getTotalElements());
		assertEquals(4, second.getTotalElements());
		assertEquals(List.of("Ada", "Beta"), first.map((user) -> user.userFirstName).getContent());
		assertEquals(List.of("Delta", "Gamma"), second.map((user) -> user.userFirstName).getContent());
		assertEquals(this.users.count(search), first.getTotalElements());
	}

	/**
	 * Verifies collection-existence OR queries without multiplying rows for two child
	 * records.
	 */
	@Test
	void collectionOrPreservesCountsAndPages() throws Exception {
		Author empty = new Author();
		empty.name = "Empty";
		this.authors.save(empty);
		Author full = new Author();
		full.name = "Full";
		full.addBook(new Book());
		full.addBook(new Book());
		this.authors.save(full);
		Specification<Author> search = new SpecificationsBuilder<Author>(settings("strict"))
			.withSearch("books IS NOT EMPTY OR name:Empty")
			.build();
		var page = this.authors.findAll(search, PageRequest.of(0, 1, Sort.by("name")));
		assertEquals(2, page.getTotalElements());
		assertEquals(2, this.authors.count(search));
		assertEquals("Empty", page.getContent().get(0).name);
	}

	/**
	 * Records the implicit inner join that removes an orphan even from the other OR
	 * branch.
	 */
	@Test
	void nestedAssociationOrRetainsLegacyInnerJoinSemantics() throws Exception {
		Author author = new Author();
		author.name = "Writer";
		Book attached = new Book();
		attached.title = "Attached";
		author.addBook(attached);
		this.authors.save(author);
		Book orphan = new Book();
		orphan.title = "Orphan";
		this.entityManager.persist(orphan);
		var builder = this.entityManager.getCriteriaBuilder();
		var query = builder.createQuery(Book.class);
		var root = query.from(Book.class);
		Specification<Book> search = new SpecificationsBuilder<Book>(settings("strict"))
			.withSearch("author.name:Writer OR title:Orphan")
			.build();
		query.select(root).where(search.toPredicate(root, query, builder));
		assertEquals(List.of(attached), this.entityManager.createQuery(query).getResultList());
	}

	/** Rejects Boolean coercion and validates every item in a membership list. */
	@Test
	void strictBooleanValidationRejectsInvalidScalarAndListItems() throws Exception {
		rejected("active:garbage", QueryErrorCode.INVALID_VALUE, "active");
		rejected("active IN [true,garbage]", QueryErrorCode.INVALID_VALUE, "active");
		assertEquals(List.of("Beta"), names(spec("strict", "active:false")));
	}

	/** Prevents silent predicate loss for unsupported typed comparisons. */
	@Test
	void strictValidationRejectsBooleanAndEnumOrdering() throws Exception {
		rejected("active>true", QueryErrorCode.UNSUPPORTED_OPERATION, "active");
		rejected("type>TEAM_MEMBER", QueryErrorCode.UNSUPPORTED_OPERATION, "type");
		rejected("uuid>'00000000-0000-0000-0000-000000000000'", QueryErrorCode.UNSUPPORTED_OPERATION, "uuid");
	}

	/** Prevents provider-dependent LIKE predicates over numeric fields. */
	@Test
	void strictValidationRejectsNumericWildcards() throws Exception {
		rejected("userChildrenNumber:*3*", QueryErrorCode.UNSUPPORTED_OPERATION, "userChildrenNumber");
	}

	/** Normalizes both unknown attributes and navigation through a basic attribute. */
	@Test
	void strictUnknownPathsHaveActionableDiagnostics() throws Exception {
		var legacy = assertThrows(RuntimeException.class, () -> this.users.findAll(spec("legacy", "missing:value")));
		assertFalse(legacy instanceof InvalidSearchQueryException);
		rejected("missing:value", QueryErrorCode.UNKNOWN_FIELD, "missing");
		rejected("userFirstName.missing:value", QueryErrorCode.UNKNOWN_FIELD, "userFirstName.missing");
	}

	/**
	 * Preserves conversion wording while exposing a stable category and full field path.
	 */
	@Test
	void invalidTypedValuesHaveStructuredErrors() throws Exception {
		for (String expression : List.of("userChildrenNumber:abc", "uuid:broken", "type:missing",
				"updatedDateAt:broken")) {
			String field = expression.substring(0, expression.indexOf(':'));
			rejected(expression, QueryErrorCode.INVALID_VALUE, field);
		}
		var error = assertThrows(InvalidSearchQueryException.class,
				() -> this.users.findAll(spec("legacy", "userChildrenNumber:abc")));
		assertEquals("Could not parse input for the field userChildrenNumber as a Int", error.getReason());
	}

	/**
	 * Rejects empty scalar input only in strict mode, preserving empty membership
	 * support.
	 */
	@Test
	void strictEmptyScalarGetsAnActionableError() throws Exception {
		assertThrows(NullPointerException.class, () -> spec("legacy", "userFirstName:''"));
		rejected("userFirstName:''", QueryErrorCode.INVALID_VALUE, "userFirstName");
		assertEquals(0, this.users.count(spec("strict", "userFirstName IN []")));
	}

	/**
	 * Ensures scalar EMPTY and unsupported collection comparisons fail before SQL
	 * generation.
	 */
	@Test
	void strictEmptyAndCollectionOperationsAreValidated() throws Exception {
		rejected("userFirstName IS EMPTY", QueryErrorCode.UNSUPPORTED_OPERATION, "userFirstName");
		var search = new SpecificationsBuilder<Author>(settings("strict")).withSearch("books IN []").build();
		assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION,
				assertThrows(InvalidSearchQueryException.class, () -> this.authors.findAll(search)).getErrorCode());
	}

	/** Ensures restrictions apply to comparisons, membership, ranges and null checks. */
	@Test
	void allowlistChecksEveryGrammarBranchAndBlacklistWins() throws Exception {
		assertEquals(List.of("Ada"), names(spec("allowed", "userFirstName:Ada")));
		for (String expression : List.of("userLastName:One", "userLastName IN [One]", "userLastName BETWEEN A AND Z",
				"userLastName IS NULL")) {
			var error = assertThrows(InvalidSearchQueryException.class, () -> spec("allowed", expression));
			assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED, error.getErrorCode());
		}
		var denied = assertThrows(InvalidSearchQueryException.class, () -> spec("denied", "userFirstName:Ada"));
		assertEquals("Field userFirstName is blacklisted", denied.getReason());
	}

	/** Distinguishes invalid consumer configuration from invalid user input. */
	@Test
	void negativeLimitsAreConfigurationErrors() {
		for (String policy : List.of("invalidLength", "invalidDepth")) {
			assertThrows(IllegalArgumentException.class, () -> spec(policy, "userFirstName:Ada"));
		}
	}

	/** Checks the exact character boundary and the legacy unlimited default. */
	@Test
	void lengthLimitIsOptInAndBoundaryInclusive() throws Exception {
		assertEquals(List.of("Ada"), names(spec("sized", "userFirstName:Ada")));
		assertEquals(QueryErrorCode.QUERY_LIMIT_EXCEEDED,
				assertThrows(InvalidSearchQueryException.class, () -> spec("sized", "userFirstName:Gamma"))
					.getErrorCode());
		assertEquals(List.of("Gamma"), names(spec("legacy", "userFirstName:Gamma")));
	}

	/**
	 * Counts grammar parentheses without treating parentheses in quoted values as
	 * nesting.
	 */
	@Test
	void depthLimitIsOptInAndIgnoresQuotedParentheses() throws Exception {
		assertEquals(List.of("Ada"), names(spec("nested", "(userFirstName:Ada)")));
		assertEquals(0, this.users.count(spec("nested", "userFirstName:'((value))'")));
		assertEquals(QueryErrorCode.QUERY_LIMIT_EXCEEDED,
				assertThrows(InvalidSearchQueryException.class, () -> spec("nested", "((userFirstName:Ada))"))
					.getErrorCode());
		assertEquals(List.of("Ada"), names(spec("legacy", "((userFirstName:Ada))")));
	}

	/**
	 * Checks useful syntax locations and metadata without adding a fabricated entity
	 * field.
	 */
	@Test
	void syntaxErrorsCarryCodeAndOriginalLocationText() throws Exception {
		var error = assertThrows(InvalidSearchQueryException.class, () -> spec("strict", "userFirstName:"));
		assertEquals(QueryErrorCode.SYNTAX_ERROR, error.getErrorCode());
		var reason = error.getReason();
		assertNotNull(reason);
		assertTrue(reason.startsWith("Invalid search query: [line 1:14"));
		assertNull(error.getField());
		var properties = error.getBody().getProperties();
		assertNotNull(properties);
		assertEquals(1, properties.get("line"));
		assertEquals(14, properties.get("column"));
		assertFalse(properties.containsKey("field"));
	}

	/**
	 * Verifies numeric and temporal conversion, equality, ordering and ranges in strict
	 * mode.
	 */
	@Test
	void strictTypedQueriesRetainExistingSuccessfulSemantics() throws Exception {
		assertEquals(4, this.users.count(spec("strict", "userChildrenNumber BETWEEN 2 AND 4")));
		assertEquals(4, this.users.count(spec("strict", "userChildrenNumber>:3 AND userSalary<:3000")));
		assertEquals(0, this.users.count(spec("strict", "userChildrenNumber>3 OR userSalary<3000")));
		assertEquals(4, this.users.count(spec("strict", "type IN [TEAM_MEMBER]")));
		assertEquals(4, this.users.count(spec("strict", "updatedDateAt>'2000-01-01'")));
		assertEquals(4, this.users.count(spec("strict", "userAgeInSeconds>0")));
		var id = this.users.findAll().iterator().next().uuid;
		assertEquals(1, this.users.count(spec("strict", "uuid:'" + id + "'")));
	}

	/**
	 * Rejects unregistered association-valued scalar types with a provider-independent
	 * category.
	 */
	@Test
	void strictUnsupportedFieldTypeIsExplicit() throws Exception {
		var builder = this.entityManager.getCriteriaBuilder();
		var query = builder.createQuery(Book.class);
		var root = query.from(Book.class);
		Specification<Book> search = new SpecificationsBuilder<Book>(settings("strict")).withSearch("author:Writer")
			.build();
		var error = assertThrows(InvalidSearchQueryException.class, () -> search.toPredicate(root, query, builder));
		assertEquals(QueryErrorCode.UNSUPPORTED_FIELD_TYPE, error.getErrorCode());
		assertEquals("author", error.getField());
	}

	/**
	 * Extracts real annotation instances so new options use Java's annotation defaults.
	 * @param method annotated method name
	 * @return method parameter settings
	 * @throws Exception if the fixture method is absent
	 */
	private SearchSpec settings(String method) throws Exception {
		return QueryBehaviorTest.class.getDeclaredMethod(method, Specification.class).getParameters()[0]
			.getAnnotation(SearchSpec.class);
	}

	/**
	 * Builds a query using the selected compatibility or validation settings.
	 * @param policy fixture method naming the policy
	 * @param expression advanced query expression
	 * @return unexecuted JPA specification
	 * @throws Exception if the annotation fixture cannot be read
	 */
	private Specification<Users> spec(String policy, String expression) throws Exception {
		return new SpecificationsBuilder<Users>(settings(policy)).withSearch(expression).build();
	}

	/**
	 * Returns stable names independently of database result order.
	 * @param search search predicate
	 * @return sorted first names
	 */
	private List<String> names(Specification<Users> search) {
		return this.users.findAll(search).stream().map((user) -> user.userFirstName).sorted().toList();
	}

	/**
	 * Verifies errors from actual JPA predicate construction as well as parsing.
	 * @param expression rejected expression
	 * @param code expected diagnostic category
	 * @param field expected entity path
	 * @throws Exception if fixture settings cannot be read
	 */
	private void rejected(String expression, QueryErrorCode code, String field) throws Exception {
		var error = assertThrows(InvalidSearchQueryException.class,
				() -> this.users.findAll(spec("strict", expression)));
		assertEquals(400, error.getStatusCode().value());
		assertEquals(code, error.getErrorCode());
		assertEquals(field, error.getField());
		var properties = error.getBody().getProperties();
		assertNotNull(properties);
		assertEquals(code.name(), properties.get("code"));
		assertEquals(field, properties.get("field"));
	}

}
