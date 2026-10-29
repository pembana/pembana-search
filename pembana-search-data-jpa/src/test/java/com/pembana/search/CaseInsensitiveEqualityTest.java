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
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
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
import com.pembana.search.strategies.StringStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Verifies string equality case policy, SQL null handling and both construction paths.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class CaseInsensitiveEqualityTest {

	@Autowired
	UsersRepository users;

	/** Creates distinct case variants, nullable strings and typed values. */
	@BeforeEach
	void seed() {
		this.users.save(new Users().withUserFirstName("Ada").withUserLastName("One").withActive(true));
		this.users.save(new Users().withUserFirstName("ADA").withUserLastName("Other").withActive(true));
		this.users.save(new Users().withUserFirstName("Beta").withUserLastName("oNe").withActive(false));
		this.users.save(new Users().withUserFirstName("Gamma").withUserLastName(null).withActive(null));
	}

	/**
	 * Defaults retain exact comparison.
	 * @param unused annotation target
	 */
	private void sensitive(@SearchSpec Specification<Users> unused) {
	}

	/**
	 * Selects folding.
	 * @param unused annotation target
	 */
	private void insensitive(@SearchSpec(caseSensitiveFlag = false) Specification<Users> unused) {
	}

	/**
	 * Strict validation must not override the case policy.
	 * @param unused annotation target
	 */
	private void strictInsensitive(
			@SearchSpec(caseSensitiveFlag = false, strictValidation = true) Specification<Users> unused) {
	}

	/** Equality folds both the field and input, including strict-validation mode. */
	@Test
	void insensitiveEqualityMatchesCaseVariants() throws Exception {
		for (String policy : List.of("insensitive", "strictInsensitive")) {
			assertEquals(List.of("ADA", "Ada"), names(spec(policy, "userFirstName:aDa")));
		}
	}

	/** Inequality excludes every case variant of the compared value. */
	@Test
	void insensitiveInequalityExcludesCaseVariants() throws Exception {
		for (String policy : List.of("insensitive", "strictInsensitive")) {
			assertEquals(List.of("Beta", "Gamma"), names(spec(policy, "userFirstName!aDa")));
		}
	}

	/** The default true flag still distinguishes Ada, ADA and ada. */
	@Test
	void sensitiveEqualityRemainsExact() throws Exception {
		assertTrue(settings("sensitive").caseSensitiveFlag());
		assertEquals(List.of("Ada"), names(spec("sensitive", "userFirstName:Ada")));
		assertEquals(List.of("ADA"), names(spec("sensitive", "userFirstName:ADA")));
		assertEquals(List.of(), names(spec("sensitive", "userFirstName:ada")));
	}

	/** Case-sensitive inequality still excludes only the exact value. */
	@Test
	void sensitiveInequalityRemainsExact() throws Exception {
		assertEquals(List.of("ADA", "Beta", "Gamma"), names(spec("sensitive", "userFirstName!Ada")));
	}

	/** SQL nulls remain distinct from literal strings and do not satisfy inequality. */
	@Test
	void nullableStringsAndLiteralNullRemainDistinct() throws Exception {
		this.users.save(new Users().withUserFirstName("NullText").withUserLastName("NuLl"));
		assertEquals(List.of("Ada", "Beta"), names(spec("insensitive", "userLastName:ONE")));
		assertEquals(List.of("ADA", "NullText"), names(spec("insensitive", "userLastName!ONE")));
		assertEquals(List.of("NullText"), names(spec("insensitive", "userLastName:'NULL'")));
		assertEquals(List.of("Gamma"), names(spec("insensitive", "userLastName IS NULL")));
		assertEquals(List.of("ADA", "Ada", "Beta", "NullText"), names(spec("insensitive", "userLastName IS NOT NULL")));
	}

	/** Low-level null input must not turn into the literal string null during folding. */
	@Test
	void rawNullComparisonRetainsProviderSemantics() throws Exception {
		this.users.save(new Users().withUserFirstName("NullText").withUserLastName("null"));
		Specification<Users> equalNull = (root, query, builder) -> builder.equal(root.get("userLastName"),
				(Object) null);
		Specification<Users> unequalNull = (root, query, builder) -> builder.notEqual(root.get("userLastName"),
				(Object) null);
		for (String policy : List.of("sensitive", "insensitive")) {
			assertEquals(names(equalNull),
					names(new SpecificationImpl<>(new SearchCriteria("userLastName", SearchOperation.EQUALS, null),
							settings(policy))));
			assertEquals(names(unequalNull),
					names(new SpecificationImpl<>(new SearchCriteria("userLastName", SearchOperation.NOT_EQUALS, null),
							settings(policy))));
		}
	}

	/** Programmatic equality/inequality use the same adapter policy as expressions. */
	@Test
	void programmaticAndParsedQueriesAgree() throws Exception {
		for (String policy : List.of("sensitive", "insensitive", "strictInsensitive")) {
			var translator = new JpaQueryTranslator<Users>(settings(policy));
			assertEquals(names(spec(policy, "userFirstName:aDa")),
					names(translator.translate(new Comparison("userFirstName", ComparisonOperator.EQUALS, "aDa"))));
			assertEquals(names(spec(policy, "userFirstName!aDa")),
					names(translator.translate(new Comparison("userFirstName", ComparisonOperator.NOT_EQUALS, "aDa"))));
			assertEquals(4, this.users.count(spec(policy, "userChildrenNumber:3")));
			assertEquals(2, this.users.count(spec(policy, "active:true")));
		}
	}

	/** Verifies ROOT normalization of bound values independently of SQL folding rules. */
	@Test
	void inputCaseFoldingDoesNotUseDefaultLocale() throws Exception {
		CriteriaBuilder builder = mock(CriteriaBuilder.class);
		Path<?> path = mock(Path.class);
		Path<String> field = mock();
		Expression<String> folded = mock();
		given(path.<String>get("userFirstName")).willReturn(field);
		given(builder.lower(field)).willReturn(folded);
		StringStrategy strategy = new StringStrategy(settings("insensitive"));
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			strategy.buildPredicate(builder, path, "userFirstName", SearchOperation.EQUALS, "IRIS");
			strategy.buildPredicate(builder, path, "userFirstName", SearchOperation.NOT_EQUALS, "IRIS");
			then(builder).should().equal(folded, "iris");
			then(builder).should().notEqual(folded, "iris");
		}
		finally {
			Locale.setDefault(original);
		}
	}

	/**
	 * Retrieves a real annotation instance with the selected policy.
	 * @param policy policy method name
	 * @return immutable Java annotation
	 * @throws ReflectiveOperationException if a policy method is missing
	 */
	private SearchSpec settings(String policy) throws ReflectiveOperationException {
		return Objects.requireNonNull(
				CaseInsensitiveEqualityTest.class.getDeclaredMethod(policy, Specification.class).getParameters()[0]
					.getAnnotation(SearchSpec.class));
	}

	/**
	 * Builds a predicate through the expression entry point.
	 * @param policy policy method name
	 * @param expression expression to parse
	 * @return JPA specification
	 * @throws ReflectiveOperationException if a policy method is missing
	 */
	private Specification<Users> spec(String policy, String expression) throws ReflectiveOperationException {
		return new SpecificationsBuilder<Users>(settings(policy)).withSearch(expression).build();
	}

	/**
	 * Makes results independent of database row ordering.
	 * @param specification predicate to execute
	 * @return sorted first names
	 */
	private List<String> names(Specification<Users> specification) {
		return this.users.findAll(specification).stream().map((user) -> user.userFirstName).sorted().toList();
	}

}
