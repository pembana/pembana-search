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

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.ComparisonOperator;
import com.pembana.search.core.CriterionTransformer;
import com.pembana.search.core.InvalidSearchValueException;
import com.pembana.search.core.Membership;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;
import com.pembana.search.core.ValueConversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises public extension APIs with real predicates, conversion, scopes and failures
 * on both databases.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
public class QueryExtensionsTest {

	private static final Set<ProfileOperation> ALL = EnumSet.allOf(ProfileOperation.class);

	@Autowired
	EntityManager entityManager;

	/**
	 * Seeds exact typed values, nullable state and text for virtual-field composition.
	 */
	@BeforeEach
	void seed() {
		for (long id : List.of(1L, 2L, 3L)) {
			var row = new ExtensionRow();
			row.id = id;
			row.amount = (id == 3) ? null : new BigDecimal(id * 10);
			row.title = (id == 1) ? "Ada" : (id == 2) ? "Beta" : "Gamma";
			row.note = (id == 2) ? null : (id == 1) ? "One" : "Ada";
			this.entityManager.persist(row);
		}
		this.entityManager.flush();
	}

	private ValueConversion idsConversion() {
		return new ValueConversion(Long.class, ALL, (raw) -> {
			if (!raw.startsWith("ID-")) {
				return Optional.empty();
			}
			try {
				return Optional.of(Long.valueOf(raw.substring(3)));
			}
			catch (NumberFormatException exception) {
				throw new InvalidSearchValueException("Invalid external identifier");
			}
		});
	}

	/**
	 * Field conversion wins over exact-type registration; declines and absent
	 * registrations retain fallback.
	 */
	@Test
	void converterPrecedenceAndFallback() throws Exception {
		var registry = QueryExtensions.builder()
			.forType(idsConversion())
			.forField("id",
					new ValueConversion(Long.class, Set.of(ProfileOperation.EQUALS),
							(raw) -> (raw.equals("ID-2")) ? Optional.of(1L) : Optional.empty()))
			.build();
		assertEquals(List.of(1L), ids(builder().withExtendedSearch("id:ID-2", registry).build()));
		var narrowType = QueryExtensions.builder()
			.forType(new ValueConversion(Long.class, Set.of(ProfileOperation.EQUALS), (raw) -> Optional.empty()))
			.forField("id", idsConversion())
			.build();
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("id IN [ID-2]", narrowType).build()));
		assertEquals(List.of(3L), ids(builder().withExtendedSearch("id:ID-3", registry).build()));
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("id:2", registry).build()));
		assertEquals(List.of(1L), ids(builder().withExtendedSearch("title:Ada", registry).build()));
		assertEquals(List.of(2L), ids(builder().withSearch("id:2").build()));
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("id:2", QueryExtensions.builder().build()).build()));
		var declined = QueryExtensions.builder()
			.transform("title", (query) -> Optional.empty())
			.factory("title", new PredicateRegistration(String.class, ALL, (context) -> Optional.empty()))
			.build();
		assertEquals(List.of(1L), ids(builder().withExtendedSearch("title:Ada", declined).build()));
		var missing = QueryExtensions.builder()
			.factory("missing", new PredicateRegistration(String.class, ALL, (context) -> Optional.empty()))
			.build();
		assertEquals(QueryErrorCode.UNKNOWN_FIELD, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("missing:x", missing).build()))
			.getErrorCode());
		assertThrows(InvalidSearchQueryException.class, () -> builder().withExtendedSearch("invalid", registry));
	}

	/**
	 * Scalar, membership, empty lists and ranges share exact conversion, including a
	 * newly supported persistent type.
	 */
	@Test
	void typedScalarListRangeAndNull() throws Exception {
		var decimal = new ValueConversion(BigDecimal.class, ALL, (raw) -> {
			try {
				return Optional.of(new BigDecimal(raw.replace("USD", "")));
			}
			catch (NumberFormatException exception) {
				throw new InvalidSearchValueException("Invalid decimal amount");
			}
		});
		var registry = QueryExtensions.builder().forType(idsConversion()).forType(decimal).build();
		for (String expression : List.of("id:ID-2", "id IN [ID-2]", "id BETWEEN ID-2 AND ID-2", "amount:USD20")) {
			assertEquals(List.of(2L), ids(builder().withExtendedSearch(expression, registry).build()));
		}
		assertEquals(List.of(1L), ids(builder().withExtendedSearch("amount<USD20", registry).build()));
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("amount>USD10", registry).build()));
		assertEquals(List.of(1L), ids(builder().withExtendedSearch("amount!USD20", registry).build()));
		assertEquals(List.of(1L), ids(builder().withExtendedSearch("id NOT IN [ID-2,ID-3]", registry).build()));
		assertEquals(List.of(1L, 3L),
				ids(builder().withExtendedSearch("id NOT BETWEEN ID-2 AND ID-2", registry).build()));
		assertEquals(List.of(), ids(builder().withExtendedSearch("amount IN []", registry).build()));
		assertEquals(List.of(1L, 2L, 3L), ids(builder().withExtendedSearch("amount NOT IN []", registry).build()));
		assertEquals(List.of(3L), ids(builder().withExtendedSearch("amount IS NULL", registry).build()));
		assertEquals(List.of(1L, 2L), ids(builder().withExtendedSearch("amount IS NOT NULL", registry).build()));
		assertEquals(QueryErrorCode.UNSUPPORTED_FIELD_TYPE,
				assertThrows(InvalidSearchQueryException.class, () -> ids(builder().withSearch("amount:20").build()))
					.getErrorCode());
		var mixed = QueryExtensions.builder().forField("id", idsConversion()).build();
		assertEquals(List.of(1L, 2L), ids(builder().withExtendedSearch("id IN [ID-1,2]", mixed).build()));
		for (String invalid : List.of("id:ID-no", "id IN [ID-1,ID-no]", "id BETWEEN ID-1 AND ID-no", "amount:USDno")) {
			assertEquals(QueryErrorCode.INVALID_VALUE, assertThrows(InvalidSearchQueryException.class,
					() -> ids(builder().withExtendedSearch(invalid, registry).build()))
				.getErrorCode());
		}
	}

	/** Converted strings retain the ordinary string matching and explicit case policy. */
	@Test
	void convertedStringMatching() throws Exception {
		var registry = QueryExtensions.builder()
			.forField("title",
					new ValueConversion(String.class, ALL,
							(raw) -> Optional.of((raw.equals("friendly")) ? "Ada" : raw)))
			.build();
		for (String expression : List.of("title:friendly", "title:*friendly*", "title IN [friendly]")) {
			assertEquals(List.of(1L), ids(builder().withExtendedSearch(expression, registry).build()));
		}
		var options = new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of());
		assertEquals(List.of(1L), ids(builder(options).withExtendedSearch("title:ADA", registry).build()));
	}

	/**
	 * A virtual-field factory receives converted values and query-local context and
	 * supports profiles.
	 */
	@Test
	void virtualFactoryAndProfile() throws Exception {
		var seen = new AtomicInteger();
		var registry = QueryExtensions.builder()
			.forField("displayId", idsConversion())
			.factory("displayId", new PredicateRegistration(Long.class, ALL, (context) -> {
				seen.incrementAndGet();
				assertEquals("displayId", context.field());
				assertEquals(Long.class, context.type());
				assertEquals(ProfileOperation.EQUALS, context.operation());
				return Optional.of(context.builder().equal(context.root().get("id"), context.value()));
			}))
			.build();
		var profile = new SearchProfile("codes",
				Map.of("code", new SearchField("displayId", Set.of(ProfileOperation.EQUALS))), Map.of(), List.of(),
				SearchOptions.defaults());
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("code:ID-2", profile, registry).build()));
		assertEquals(List.of(2L),
				ids(builder()
					.withExtendedQuery(new Comparison("code", ComparisonOperator.EQUALS, "ID-2"), profile, registry)
					.build()));
		assertEquals(2, seen.get());
		assertThrows(InvalidSearchQueryException.class,
				() -> builder().withExtendedSearch("code>1", profile, registry));
		assertEquals(2, seen.get());
		var caseError = builder(new SearchOptions(CaseSensitivity.INHERIT, Map.of("code", CaseSensitivity.INSENSITIVE)))
			.withExtendedSearch("code:ID-2", profile, registry)
			.build();
		assertEquals(QueryErrorCode.INVALID_SEARCH_OPTIONS,
				assertThrows(InvalidSearchQueryException.class, () -> ids(caseError)).getErrorCode());
		var nullRegistry = QueryExtensions.builder()
			.factory("virtualNull",
					new PredicateRegistration(String.class, ALL,
							(context) -> Optional.of(context.builder().isNull(context.root().get("note")))))
			.build();
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("virtualNull IS NULL", nullRegistry).build()));
		assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder()
					.withExtendedQuery(new Comparison("virtualNull", ComparisonOperator.IS, "invalid"), nullRegistry)
					.build()))
			.getErrorCode());
		assertEquals(List.of(2L),
				ids(builder(new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()))
					.withExtendedSearch("displayId:ID-2", registry)
					.build()));
	}

	/**
	 * Matching policy and query-local factory state remain valid across result, page and
	 * count executions.
	 */
	@Test
	void factoryMatchingAndRepeatedQueries() throws Exception {
		var registry = QueryExtensions.builder()
			.factory("any", new PredicateRegistration(String.class, ALL, (context) -> {
				assertEquals(CaseSensitivity.INSENSITIVE, context.caseSensitivity());
				assertEquals(true, context.legacyCaseSensitive());
				String value = ((String) Objects.requireNonNull(context.value())).toLowerCase(java.util.Locale.ROOT);
				return Optional.of(context.builder()
					.or(context.builder().equal(context.builder().lower(context.root().get("title")), value),
							context.builder().equal(context.builder().lower(context.root().get("note")), value)));
			}))
			.build();
		var spec = builder(new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()))
			.withExtendedSearch("any:ADA", registry)
			.build();
		assertEquals(List.of(1L, 3L), ids(spec));
		assertEquals(List.of(1L, 3L), ids(spec));
		var cb = this.entityManager.getCriteriaBuilder();
		var count = cb.createQuery(Long.class);
		var root = count.from(ExtensionRow.class);
		count.select(cb.count(root)).where(Objects.requireNonNull(spec.toPredicate(root, count, cb)));
		assertEquals(2L, this.entityManager.createQuery(count).getSingleResult());
		var page = cb.createQuery(Long.class);
		var pageRoot = page.from(ExtensionRow.class);
		page.select(pageRoot.get("id"))
			.where(Objects.requireNonNull(spec.toPredicate(pageRoot, page, cb)))
			.orderBy(cb.asc(pageRoot.get("id")));
		assertEquals(List.of(3L),
				this.entityManager.createQuery(page).setFirstResult(1).setMaxResults(1).getResultList());
	}

	private CriterionTransformer anyText() {
		return (query) -> {
			if (query instanceof Comparison comparison) {
				return Optional.of(new Comparison("title", comparison.operator(), comparison.value())
					.or(new Comparison("note", comparison.operator(), comparison.value())));
			}
			Membership membership = (Membership) query;
			return Optional.of(new Membership("title", membership.negated(), membership.values())
				.or(new Membership("note", membership.negated(), membership.values())));
		};
	}

	/**
	 * Grouped expansion retains outer scopes, Boolean negation and alias/case mappings.
	 */
	@Test
	void groupedTransformationsAndScopes() throws Exception {
		var registry = QueryExtensions.builder().transform("any", anyText()).build();
		var comparison = new Comparison("any", ComparisonOperator.EQUALS, "Ada");
		assertEquals(List.of(1L, 3L), ids(builder().withExtendedSearch("any:Ada", registry).build()));
		assertEquals(List.of(1L, 3L), ids(builder().withExtendedQuery(comparison, registry).build()));
		assertEquals(List.of(1L, 3L),
				ids(builder().withExtendedQuery(new Membership("any", false, List.of("Ada")), registry).build()));
		Specification<ExtensionRow> scope = (root, query, cb) -> cb.equal(root.get("id"), 1L);
		assertEquals(List.of(1L), ids(scope.and(builder().withExtendedSearch("any:Ada OR id:2", registry).build())));
		assertEquals(List.of(), ids(scope.and(builder().withExtendedQuery(comparison.not(), registry).build())));
		var profile = new SearchProfile("text",
				Map.of("all", new SearchField("any", ALL), "title", new SearchField("title", ALL), "note",
						new SearchField("note", ALL)),
				Map.of(), List.of(),
				new SearchOptions(CaseSensitivity.INHERIT, Map.of("all", CaseSensitivity.INSENSITIVE)));
		assertEquals(List.of(1L, 3L), ids(builder().withExtendedSearch("all:ADA", profile, registry).build()));
		assertEquals(List.of(1L, 3L),
				ids(builder(new SearchOptions(CaseSensitivity.INHERIT, Map.of("all", CaseSensitivity.INSENSITIVE)))
					.withExtendedQuery(new Membership("all", false, List.of("ADA")).not().not(), profile, registry)
					.build()));
		var hidden = new SearchProfile("limited", Map.of("all", new SearchField("any", ALL)), Map.of(), List.of(),
				SearchOptions.defaults());
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED, assertThrows(InvalidSearchQueryException.class,
				() -> builder().withExtendedSearch("all:Ada", hidden, registry))
			.getErrorCode());
		SearchSpec restricted = settings("restricted");
		assertThrows(InvalidSearchQueryException.class,
				() -> new SpecificationsBuilder<ExtensionRow>(restricted).withExtendedSearch("any:Ada", registry));
		assertThrows(InvalidSearchQueryException.class,
				() -> new SpecificationsBuilder<ExtensionRow>(restricted).withExtendedSearch("note:Ada", registry));
	}

	/**
	 * Cyclic or excessive expansion fails finitely, including when source field case
	 * choices are forwarded.
	 */
	@Test
	void expansionLimitsAndFailures() throws Exception {
		var cycle = QueryExtensions.builder().transform("any", Optional::of).build();
		assertEquals(QueryErrorCode.QUERY_LIMIT_EXCEEDED,
				assertThrows(InvalidSearchQueryException.class, () -> builder().withExtendedSearch("any:x", cycle))
					.getErrorCode());
		var huge = QueryExtensions.builder().transform("any", anyText()).limits(2, 8).build();
		assertThrows(InvalidSearchQueryException.class, () -> builder().withExtendedSearch("any:Ada", huge));
		assertThrows(InvalidSearchQueryException.class,
				() -> builder(new SearchOptions(CaseSensitivity.INHERIT, Map.of("any", CaseSensitivity.INSENSITIVE)))
					.withExtendedSearch("any:Ada", huge));
		var depth = QueryExtensions.builder()
			.transform("any",
					(query) -> Optional
						.of(new Comparison("any", ComparisonOperator.EQUALS, ((Comparison) query).value() + "x")))
			.limits(100, 2)
			.build();
		assertThrows(InvalidSearchQueryException.class, () -> builder().withExtendedSearch("any:x", depth));
		var invalid = QueryExtensions.builder().transform("any", (query) -> {
			throw new InvalidSearchValueException("Invalid transform input");
		}).build();
		assertEquals(QueryErrorCode.INVALID_VALUE,
				assertThrows(InvalidSearchQueryException.class, () -> builder().withExtendedSearch("any:x", invalid))
					.getErrorCode());
		var broken = QueryExtensions.builder().transform("any", (query) -> {
			throw new IllegalStateException("Broken transformer");
		}).build();
		assertThrows(IllegalStateException.class, () -> builder().withExtendedSearch("any:x", broken));
	}

	/**
	 * Extension implementation faults stay visible; explicit user rejection is the only
	 * converted custom failure.
	 */
	@Test
	void converterAndFactoryFailures() throws Exception {
		var broken = QueryExtensions.builder().forField("id", new ValueConversion(Long.class, ALL, (raw) -> {
			throw new IllegalStateException("Broken converter");
		})).build();
		assertThrows(IllegalStateException.class, () -> ids(builder().withExtendedSearch("id:1", broken).build()));
		var wrong = QueryExtensions.builder()
			.forField("id", new ValueConversion(Long.class, ALL, (raw) -> Optional.of("wrong")))
			.build();
		assertThrows(IllegalStateException.class, () -> ids(builder().withExtendedSearch("id:1", wrong).build()));
		var mismatch = QueryExtensions.builder()
			.forField("id", new ValueConversion(String.class, ALL, Optional::of))
			.build();
		assertThrows(IllegalStateException.class, () -> ids(builder().withExtendedSearch("id:1", mismatch).build()));
		var denied = QueryExtensions.builder()
			.forField("id", new ValueConversion(Long.class, Set.of(ProfileOperation.EQUALS), Optional::of))
			.build();
		assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("id>1", denied).build()))
			.getErrorCode());
		var badFactory = QueryExtensions.builder()
			.factory("id", new PredicateRegistration(Long.class, ALL, (context) -> {
				throw new IllegalStateException("Broken factory");
			}))
			.build();
		assertThrows(IllegalStateException.class, () -> ids(builder().withExtendedSearch("id:1", badFactory).build()));
		var badInput = QueryExtensions.builder().factory("id", new PredicateRegistration(Long.class, ALL, (context) -> {
			throw new InvalidSearchValueException("Rejected input");
		})).build();
		assertEquals(QueryErrorCode.INVALID_VALUE, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("id:1", badInput).build()))
			.getErrorCode());
		var wrongFallback = QueryExtensions.builder()
			.forField("id", new ValueConversion(String.class, ALL, Optional::of))
			.factory("id", new PredicateRegistration(String.class, ALL, (context) -> Optional.empty()))
			.build();
		assertThrows(IllegalStateException.class,
				() -> ids(builder().withExtendedSearch("id:1", wrongFallback).build()));
		var noOperations = QueryExtensions.builder()
			.factory("id", new PredicateRegistration(Long.class, Set.of(), (context) -> Optional.empty()))
			.build();
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("id:1", noOperations).build()));
		var declared = QueryExtensions.builder()
			.factory("id", new PredicateRegistration(Long.class, ALL, (context) -> Optional.empty()))
			.build();
		assertEquals(QueryErrorCode.INVALID_VALUE, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("id:abc", declared).build()))
			.getErrorCode());
	}

	/**
	 * Duplicate declarations fail eagerly and immutable registry snapshots isolate later
	 * registrations.
	 */
	@Test
	void configurationAndIsolation() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> QueryExtensions.builder().forType(idsConversion()).forType(idsConversion()));
		assertThrows(IllegalArgumentException.class,
				() -> QueryExtensions.builder().forField("id", idsConversion()).forField("id", idsConversion()));
		var registration = new PredicateRegistration(String.class, ALL, (context) -> Optional.empty());
		assertThrows(IllegalArgumentException.class,
				() -> QueryExtensions.builder().factory("name", registration).factory("name", registration));
		assertThrows(IllegalArgumentException.class,
				() -> QueryExtensions.builder().transform("name", Optional::of).transform("name", Optional::of));
		assertThrows(IllegalArgumentException.class,
				() -> QueryExtensions.builder().forField("bad_field", idsConversion()));
		assertThrows(IllegalArgumentException.class, () -> QueryExtensions.builder().limits(0, 1));
		assertThrows(IllegalArgumentException.class, () -> QueryExtensions.builder().limits(1, 0));
		assertThrows(IllegalArgumentException.class, () -> QueryExtensions.builder().limits(1, 257));
		assertThrows(IllegalArgumentException.class,
				() -> QueryExtensions.builder()
					.forField("name", idsConversion())
					.factory("name", registration)
					.build());
		var config = QueryExtensions.builder();
		var snapshot = config.build();
		config.forType(idsConversion());
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("id:ID-2", snapshot).build()));
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("id:ID-2", config.build()).build()));
		var profile = new SearchProfile("id", Map.of("key", new SearchField("id", ALL)), Map.of(), List.of(),
				SearchOptions.defaults());
		assertEquals(List.of(2L), ids(builder().withExtendedSearch("key:2", profile, snapshot).build()));
		var unknown = QueryExtensions.builder().forType(idsConversion()).build();
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("missing:x", unknown).build()));
	}

	/**
	 * The standalone example runs against generic fixture attributes and cannot escape
	 * the required scope.
	 */
	@Test
	void standaloneConsumerExample() throws Exception {
		Specification<ExtensionRow> scope = (root, query, cb) -> cb.equal(root.get("id"), 1L);
		assertEquals(List.of(1L),
				ids(com.pembana.search.samples.ExtensionConsumer.scoped(settings("policy"), scope, "all:Ada")));
		assertEquals(List.of(),
				ids(com.pembana.search.samples.ExtensionConsumer.scoped(settings("policy"), scope, "code:CODE-2")));
		assertEquals(List.of(1L),
				ids(com.pembana.search.samples.ExtensionConsumer.scoped(settings("policy"), scope, "code:1")));
		assertEquals(List.of(1L),
				ids(com.pembana.search.samples.ExtensionConsumer.scoped(settings("policy"), scope, "all IN [Ada]")));
		assertThrows(InvalidSearchQueryException.class, () -> ids(
				com.pembana.search.samples.ExtensionConsumer.scoped(settings("policy"), scope, "code:CODE-no")));
	}

	/**
	 * Parallel translations through independent registries retain their own
	 * transformations and factories.
	 */
	@Test
	void concurrentRegistryIsolation() throws Exception {
		var first = QueryExtensions.builder()
			.transform("virtual", (query) -> Optional.of(new Comparison("id", ComparisonOperator.EQUALS, "1")))
			.factory("id", new PredicateRegistration(Long.class, ALL,
					(context) -> Optional.of(context.builder().equal(context.root().get("id"), context.value()))))
			.build();
		var second = QueryExtensions.builder()
			.transform("virtual", (query) -> Optional.of(new Comparison("id", ComparisonOperator.EQUALS, "2")))
			.factory("id", new PredicateRegistration(Long.class, ALL,
					(context) -> Optional.of(context.builder().equal(context.root().get("id"), context.value()))))
			.build();
		SearchSpec policy = settings("policy");
		var specifications = java.util.stream.IntStream.range(0, 20)
			.parallel()
			.mapToObj((index) -> new ExtendedQueryTranslator<ExtensionRow>(policy, SearchOptions.defaults(),
					(index % 2 == 0) ? first : second, null)
				.translate(new Comparison("virtual", ComparisonOperator.EQUALS, "ignored")))
			.toList();
		for (int i = 0; i < specifications.size(); i++) {
			assertEquals(List.of((i % 2 == 0) ? 1L : 2L), ids(specifications.get(i)));
		}
	}

	/**
	 * Unmatched and declining converters preserve ordinary strict type errors ahead of
	 * case-option validation.
	 */
	@Test
	void fallbackPreservesErrorOrdering() throws Exception {
		var options = new SearchOptions(CaseSensitivity.INHERIT, Map.of("amount", CaseSensitivity.INSENSITIVE));
		var ordinary = builder(options).withSearch("amount:10").build();
		var baseline = assertThrows(InvalidSearchQueryException.class, () -> ids(ordinary));
		assertEquals(QueryErrorCode.UNSUPPORTED_FIELD_TYPE, baseline.getErrorCode());
		var unmatched = QueryExtensions.builder().forType(idsConversion()).build();
		var declining = QueryExtensions.builder()
			.forType(new ValueConversion(BigDecimal.class, ALL, (raw) -> Optional.empty()))
			.build();
		for (QueryExtensions registry : List.of(unmatched, declining, QueryExtensions.builder().build())) {
			var fallback = builder(options).withExtendedSearch("amount:10", registry).build();
			var error = assertThrows(InvalidSearchQueryException.class, () -> ids(fallback));
			assertEquals(baseline.getErrorCode(), error.getErrorCode());
			assertEquals(baseline.getReason(), error.getReason());
			assertEquals(baseline.getField(), error.getField());
		}
	}

	private SpecificationsBuilder<ExtensionRow> builder() throws Exception {
		return builder(SearchOptions.defaults());
	}

	private SpecificationsBuilder<ExtensionRow> builder(SearchOptions options) throws Exception {
		return new SpecificationsBuilder<>(settings("policy"), options);
	}

	private SearchSpec settings(String method) throws Exception {
		return Objects
			.requireNonNull(QueryExtensionsTest.class.getDeclaredMethod(method, Specification.class).getParameters()[0]
				.getAnnotation(SearchSpec.class));
	}

	private List<Long> ids(Specification<ExtensionRow> specification) {
		var cb = this.entityManager.getCriteriaBuilder();
		var query = cb.createQuery(Long.class);
		var root = query.from(ExtensionRow.class);
		query.select(root.get("id"))
			.where(Objects.requireNonNull(specification.toPredicate(root, query, cb)))
			.orderBy(cb.asc(root.get("id")));
		return this.entityManager.createQuery(query).getResultList();
	}

	private static void policy(@SearchSpec(strictValidation = true) Specification<?> query) {
	}

	private static void restricted(
			@SearchSpec(strictValidation = true, blackListedFields = { "note" }) Specification<?> query) {
	}

	/**
	 * Generic fixture includes a type without a built-in strict conversion strategy.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "ExtensionRow")
	@Table(name = "extension_row")
	public static class ExtensionRow {

		@Id
		public Long id = 0L;

		@Column(precision = 20, scale = 2)
		public @Nullable BigDecimal amount;

		public String title = "";

		public @Nullable String note;

		/** Creates a row for JPA. */
		public ExtensionRow() {
		}

	}

}
