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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.AssociationOverride;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.AssociationQuery;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.ComparisonOperator;
import com.pembana.search.core.InvalidSearchValueException;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.Query;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;
import com.pembana.search.core.ValueConversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real collection quantifiers, same-child ranges and optional child relations on H2 and
 * PostgreSQL.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
public class CollectionPolicyTest {

	private static final Set<ProfileOperation> ALL = EnumSet.allOf(ProfileOperation.class);

	@Autowired
	EntityManager entityManager;

	/**
	 * Seeds empty, red-only, blue-only, mixed, null-only, split and same-child fixtures.
	 */
	@BeforeEach
	void seed() {
		var alice = owner(1L, "Alice");
		var bob = owner(2L, "Bob");
		var unnamed = owner(3L, null);
		alice.supervisor = bob;
		var red = tag(1L, "red");
		var blue = tag(2L, "blue");
		var nullTag = tag(3L, null);
		tag(4L, "Unused");
		var parents = new ArrayList<Parent>();
		for (long id = 1; id <= 7; id++) {
			var parent = new Parent();
			parent.id = id;
			parent.title = List.of("Orphan", "Red", "Blue", "Mixed", "Null", "Split", "Together").get((int) id - 1);
			parent.tenant = (id == 4) ? "B" : "A";
			this.entityManager.persist(parent);
			parents.add(parent);
		}
		child(parents.get(1), 21L, "red", "small", 2L, null);
		child(parents.get(2), 31L, "blue", "large", 20L, null);
		child(parents.get(3), 41L, "red", "small", 2L, null);
		child(parents.get(3), 42L, "blue", "large", 20L, bob);
		child(parents.get(3), 43L, "red", "small", 2L, alice);
		child(parents.get(4), 51L, null, null, null, unnamed);
		child(parents.get(5), 61L, "red", "small", 2L, alice);
		child(parents.get(5), 62L, "blue", "large", 20L, null);
		child(parents.get(6), 71L, "red", "large", 7L, alice);
		for (Parent parent : parents) {
			for (Child child : parent.children) {
				if ("red".equals(child.color)) {
					parent.labels.add("red");
					parent.tags.add(red);
				}
				if ("blue".equals(child.color)) {
					parent.labels.add("blue");
					parent.tags.add(blue);
				}
				if (child.price != null) {
					parent.scores.add(child.price);
				}
				var feature = new Feature();
				feature.color = child.color;
				feature.owner = child.owner;
				parent.features.add(feature);
			}
			parent.setTerms(new HashSet<>(parent.labels));
		}
		parents.get(4).tags.add(nullTag);
		parents.get(0).peer = parents.get(1);
		parents.get(2).peer = parents.get(3);
		var detail = new Detail();
		detail.id = 1L;
		detail.label = "Checked";
		this.entityManager.persist(detail);
		this.entityManager.find(Child.class, 31L).detail = detail;
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private Owner owner(long id, @Nullable String name) {
		var owner = new Owner();
		owner.id = id;
		owner.name = name;
		this.entityManager.persist(owner);
		return owner;
	}

	private Tag tag(long id, @Nullable String name) {
		var tag = new Tag();
		tag.id = id;
		tag.name = name;
		this.entityManager.persist(tag);
		return tag;
	}

	private void child(Parent parent, long id, @Nullable String color, @Nullable String size, @Nullable Long price,
			@Nullable Owner owner) {
		var child = new Child();
		child.id = id;
		child.parent = parent;
		child.color = color;
		child.size = size;
		child.price = price;
		child.owner = owner;
		child.extra.color = color;
		child.extra.owner = owner;
		this.entityManager.persist(child);
		parent.children.add(child);
	}

	private QueryExtensions enabled() {
		return QueryExtensions.builder().collections(true).build();
	}

	/**
	 * Positive existence returns each parent once; negated operations mean no matching
	 * child, including empty collections.
	 */
	@Test
	void existenceAndAbsence() throws Exception {
		assertEquals(List.of(2L, 4L, 6L, 7L), search("children.color:red"));
		for (String condition : List.of("children.color!red", "children.color!*re*", "children.color!*red",
				"children.color!red*", "children.color NOT IN [red]")) {
			assertEquals(List.of(1L, 3L, 5L), search(condition));
		}
		assertEquals(List.of(1L, 2L, 4L, 6L, 7L), search("children.color:red OR title:Orphan"));
		assertEquals(List.of(1L), search("children IS EMPTY"));
		assertEquals(List.of(2L, 3L, 4L, 5L, 6L, 7L), search("children IS NOT EMPTY"));
		assertEquals(List.of(), search("children.color IN []"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), search("children.color NOT IN []"));
		assertEquals(List.of(5L), search("children.color IS NULL"));
		assertEquals(List.of(2L, 3L, 4L, 6L, 7L), search("children.color IS NOT NULL"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 6L, 7L),
				ids(builder().withAssociationQuery(
						AssociationQuery.where(new Comparison("children.color", ComparisonOperator.IS, "NULL")).not(),
						enabled())
					.build()));
	}

	/**
	 * Independent AND conditions may use different children; an explicit scope binds both
	 * to the same child.
	 */
	@Test
	void sameChildScopesAndNegation() throws Exception {
		assertEquals(List.of(1L, 4L, 6L, 7L),
				search("title:Orphan OR children.color:red AND children.size:large"));
		assertEquals(List.of(4L, 6L, 7L),
				search("(title:Orphan OR children.color:red) AND children.size:large"));
		Query both = new Comparison("children.color", ComparisonOperator.EQUALS, "red")
			.and(new Comparison("children.size", ComparisonOperator.EQUALS, "large"));
		assertEquals(List.of(4L, 6L, 7L), search("children.color:red AND children.size:large"));
		assertEquals(List.of(4L, 6L, 7L), ids(builder().withExtendedQuery(both, enabled()).build()));
		var same = AssociationQuery.any("children", AssociationQuery.where(both));
		assertEquals(List.of(7L), ids(builder().withAssociationQuery(same, enabled()).build()));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L),
				ids(builder().withAssociationQuery(same.not(), enabled()).build()));
		var nonRed = AssociationQuery.any("children",
				AssociationQuery.where(new Comparison("children.color", ComparisonOperator.NOT_EQUALS, "red")));
		assertEquals(List.of(3L, 4L, 6L), ids(builder().withAssociationQuery(nonRed, enabled()).build()));
		var grouped = same.or(AssociationQuery.where(new Comparison("title", ComparisonOperator.EQUALS, "Orphan")));
		assertEquals(List.of(1L, 7L), ids(builder().withAssociationQuery(grouped, enabled()).build()));
	}

	/**
	 * Parsed and explicit ranges keep both bounds on one child; hand-built AND remains
	 * independent.
	 */
	@Test
	void rangesPreserveProvenance() throws Exception {
		assertEquals(List.of(7L), search("children.price BETWEEN 5 AND 10"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), search("children.price NOT BETWEEN 5 AND 10"));
		assertEquals(List.of(1L, 7L), search("children.price BETWEEN 5 AND 10 OR title:Orphan"));
		var bounds = new Comparison("children.price", ComparisonOperator.GREATER_THAN_EQUALS, "5")
			.and(new Comparison("children.price", ComparisonOperator.LESS_THAN_EQUALS, "10"));
		assertEquals(List.of(4L, 6L, 7L), ids(builder().withExtendedQuery(bounds, enabled()).build()));
		assertEquals(List.of(7L),
				ids(builder()
					.withAssociationQuery(AssociationQuery.any("children", AssociationQuery.where(bounds)), enabled())
					.build()));
		assertEquals(List.of(7L),
				ids(builder().withAssociationQuery(AssociationQuery.between("children.price", "5", "10"), enabled())
					.build()));
		assertEquals(List.of(2L, 3L), search("id BETWEEN 2 AND 3"));
		assertEquals(List.of(1L, 4L, 5L, 6L, 7L), search("id NOT BETWEEN 2 AND 3"));
	}

	/**
	 * Missing child owners retain another matching OR branch, and a null field requires
	 * an actual child.
	 */
	@Test
	void optionalToOneInsideChildren() throws Exception {
		assertEquals(List.of(4L, 6L, 7L), search("children.owner.name:Alice"));
		assertEquals(List.of(4L, 6L, 7L), search("children.owner.supervisor.name:Bob"));
		assertEquals(List.of(2L, 3L, 4L, 6L, 7L), search("children.extra.color:red OR children.extra.color:blue"));
		assertEquals(List.of(1L, 4L, 6L, 7L), search("children.owner.name:Alice OR title:Orphan"));
		assertEquals(List.of(2L, 3L, 4L, 5L, 6L), search("children.owner.name IS NULL"));
		assertEquals(List.of(2L, 3L, 4L, 6L), search("children.owner IS NULL"));
		assertEquals(List.of(1L, 3L), search("children.detail.label:Checked OR title:Orphan"));
		var colorOrOwner = new Comparison("children.color", ComparisonOperator.EQUALS, "red")
			.or(new Comparison("children.owner.name", ComparisonOperator.EQUALS, "Alice"));
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(builder()
					.withAssociationQuery(AssociationQuery.any("children", AssociationQuery.where(colorOrOwner)),
							enabled())
					.build()));
		assertEquals(List.of(1L, 3L), search("peer.children.color:red"));
		assertEquals(List.of(2L, 4L, 5L, 6L, 7L), search("peer.children.color!red"));
		assertEquals(List.of(1L), search("peer.title:Red OR title:Red"));
		var both = QueryExtensions.builder().collections(true).optionalToOne(true).build();
		assertEquals(List.of(1L, 2L), ids(builder().withExtendedSearch("peer.title:Red OR title:Red", both).build()));
	}

	/**
	 * Basic, getter-mapped, embeddable and owning/inverse many-to-many collections share
	 * existence semantics.
	 */
	@Test
	void collectionMappingStyles() throws Exception {
		for (String input : List.of("labels:red", "terms:red", "tags.name:red", "features.color:red")) {
			assertEquals(List.of(2L, 4L, 6L, 7L), search(input));
		}
		assertEquals(List.of(1L, 3L, 5L), search("labels!red"));
		assertEquals(List.of(1L, 5L), search("labels IS EMPTY"));
		assertEquals(List.of(7L), search("scores BETWEEN 5 AND 10"));
		assertEquals(List.of(5L), search("tags.name IS NULL"));
		assertEquals(List.of(4L, 6L, 7L), search("features.owner.name:Alice"));
		var specification = new SpecificationsBuilder<Tag>(settings("strict"))
			.withExtendedSearch("parents.title:Orphan OR name:Unused", enabled())
			.build();
		assertEquals(List.of(4L), ids(Tag.class, specification));
	}

	/**
	 * String element matching follows the annotation fallback and explicit case options.
	 */
	@Test
	void scalarElementCaseAndPatterns() throws Exception {
		for (String input : List.of("labels:*re*", "labels:red*", "labels:*red", "labels IN [red]")) {
			assertEquals(List.of(2L, 4L, 6L, 7L), search(input));
		}
		var insensitive = new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of());
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(builder(insensitive).withExtendedSearch("labels IN [RED]", enabled()).build()));
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(new SpecificationsBuilder<Parent>(settings("legacyInsensitive"))
					.withExtendedSearch("labels:RED", enabled())
					.build()));
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(new SpecificationsBuilder<Parent>(settings("legacyInsensitive"))
					.withExtendedSearch("labels IN [RED]", enabled())
					.build()));
		assertEquals(List.of(3L, 4L, 6L),
				ids(new SpecificationsBuilder<Parent>(settings("legacyInsensitive"))
					.withExtendedSearch("labels<RED", enabled())
					.build()));
		assertEquals(List.of(2L, 3L, 4L, 6L, 7L),
				ids(new SpecificationsBuilder<Parent>(settings("legacyInsensitive"))
					.withExtendedSearch("labels BETWEEN BLUE AND RED", enabled())
					.build()));
		assertEquals(List.of(2L, 3L, 4L, 6L, 7L), search("labels BETWEEN a AND z"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), search("labels NOT IN []"));
		assertEquals(List.of(), search("labels IN []"));
		var property = new SearchOptions(CaseSensitivity.SENSITIVE, Map.of("labels", CaseSensitivity.INSENSITIVE));
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(builder(property).withExtendedSearch("labels:RED", enabled()).build()));
	}

	/**
	 * Counts and sorted pages agree without DISTINCT, row deduplication or post-fetch
	 * filtering.
	 */
	@Test
	void countsPagesAndRequiredScope() throws Exception {
		var specification = builder().withExtendedSearch("children.color:red OR title:Orphan", enabled()).build();
		assertEquals(List.of(1L, 2L, 4L, 6L, 7L), ids(specification));
		Specification<Parent> tenant = (root, query, cb) -> cb.equal(root.get("tenant"), "A");
		assertEquals(List.of(1L, 2L, 6L, 7L), ids(tenant.and(specification)));
		for (int i = 0; i < 2; i++) {
			var cb = this.entityManager.getCriteriaBuilder();
			var count = cb.createQuery(Long.class);
			var root = count.from(Parent.class);
			count.select(cb.count(root)).where(Objects.requireNonNull(specification.toPredicate(root, count, cb)));
			assertEquals(5L, this.entityManager.createQuery(count).getSingleResult());
			var page = cb.createQuery(Long.class);
			var pageRoot = page.from(Parent.class);
			page.select(pageRoot.get("id"))
				.where(Objects.requireNonNull(specification.toPredicate(pageRoot, page, cb)))
				.orderBy(cb.desc(pageRoot.get("id")));
			assertEquals(List.of(7L, 6L), this.entityManager.createQuery(page).setMaxResults(2).getResultList());
			assertEquals(List.of(4L, 2L),
					this.entityManager.createQuery(page).setFirstResult(2).setMaxResults(2).getResultList());
			assertEquals(List.of(1L),
					this.entityManager.createQuery(page).setFirstResult(4).setMaxResults(2).getResultList());
			assertEquals(0, pageRoot.getJoins().size());
		}
	}

	private SearchProfile profile() {
		return new SearchProfile("children",
				Map.of("color", new SearchField("children.color", ALL), "owner",
						new SearchField("children.owner.name", ALL), "price", new SearchField("children.price", ALL)),
				Map.of(), List.of(), new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
	}

	/**
	 * Profiles resolve public aliases before scoping and retain canonical permission and
	 * case rules.
	 */
	@Test
	void profilesAndPermissions() throws Exception {
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(builder().withExtendedSearch("color:RED", profile(), enabled()).build()));
		assertEquals(List.of(7L),
				ids(builder().withExtendedSearch("price BETWEEN 5 AND 10", profile(), enabled()).build()));
		var same = AssociationQuery
			.any("children", AssociationQuery.where(new Comparison("color", ComparisonOperator.EQUALS, "RED")))
			.and(AssociationQuery.between("price", "5", "10"));
		assertEquals(List.of(7L), ids(builder().withAssociationQuery(same, profile(), enabled()).build()));
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED, assertThrows(InvalidSearchQueryException.class,
				() -> builder().withExtendedSearch("children.color:red", profile(), enabled()))
			.getErrorCode());
		assertThrows(InvalidSearchQueryException.class, () -> new SpecificationsBuilder<Parent>(settings("denied"))
			.withExtendedSearch("color:red", profile(), enabled()));
	}

	/**
	 * Converters receive original negative operators; custom factories resolve the
	 * currently bound child.
	 */
	@Test
	void convertersFactoriesAndTransformations() throws Exception {
		var registry = QueryExtensions.builder()
			.collections(true)
			.forField("children.color",
					new ValueConversion(String.class, ALL,
							(raw) -> Optional.of((raw.startsWith("CODE-")) ? raw.substring(5) : raw)))
			.forType(new ValueConversion(Long.class, ALL, (raw) -> {
				if (!raw.startsWith("N")) {
					return Optional.empty();
				}
				try {
					return Optional.of(Long.valueOf(raw.substring(1)));
				}
				catch (NumberFormatException exception) {
					throw new InvalidSearchValueException("Invalid numeric code");
				}
			}))
			.transform("choice",
					(query) -> Optional.of(new Comparison("children.color", ((Comparison) query).operator(),
							((Comparison) query).value())))
			.factory("children.virtualColor", new PredicateRegistration(String.class, ALL, (context) -> {
				assertNull(context.query());
				return Optional.of(context.builder()
					.equal(CollectionPaths.resolve(context.root(), "children.color"), context.value()));
			}))
			.build();
		assertEquals(List.of(2L, 4L, 6L, 7L),
				ids(builder().withExtendedSearch("children.color:CODE-red", registry).build()));
		assertEquals(List.of(1L, 3L, 5L),
				ids(builder().withExtendedSearch("children.color!CODE-red", registry).build()));
		assertEquals(List.of(7L), ids(builder().withExtendedSearch("scores:N7", registry).build()));
		assertEquals(List.of(7L),
				ids(builder().withExtendedSearch("children.price BETWEEN N5 AND N10", registry).build()));
		assertEquals(List.of(2L, 4L, 6L, 7L), ids(builder().withExtendedSearch("choice:red", registry).build()));
		var scoped = AssociationQuery.any("children",
				AssociationQuery.where(new Comparison("children.virtualColor", ComparisonOperator.EQUALS, "red")
					.and(new Comparison("children.size", ComparisonOperator.EQUALS, "large"))));
		assertEquals(List.of(7L), ids(builder().withAssociationQuery(scoped, registry).build()));
		var negativeOnly = QueryExtensions.builder()
			.collections(true)
			.forField("children.color",
					new ValueConversion(String.class, Set.of(ProfileOperation.NOT_EQUALS), Optional::of))
			.build();
		assertEquals(List.of(1L, 3L, 5L),
				ids(builder().withExtendedSearch("children.color!red", negativeOnly).build()));
		assertEquals(QueryErrorCode.INVALID_VALUE, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("scores:Nbad", registry).build()))
			.getErrorCode());
	}

	/**
	 * Disabled registries preserve the old unsupported collection behavior and
	 * independent policy snapshots.
	 */
	@Test
	void optInAndIsolation() throws Exception {
		var builder = builder();
		assertThrows(InvalidSearchQueryException.class, () -> ids(builder.withSearch("labels:red").build()));
		assertEquals(List.of(2L, 4L, 6L, 7L), ids(builder.withExtendedSearch("labels:red", enabled()).build()));
		assertThrows(InvalidSearchQueryException.class, () -> ids(builder.withSearch("labels:red").build()));
		var configure = QueryExtensions.builder().collections(true);
		var snapshot = configure.build();
		configure.collections(false);
		assertEquals(List.of(2L, 4L, 6L, 7L), ids(builder.withExtendedSearch("labels:red", snapshot).build()));
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder.withExtendedSearch("labels:red", configure.build()).build()));
		assertThrows(IllegalArgumentException.class,
				() -> builder.withAssociationQuery(
						AssociationQuery.where(new Comparison("labels", ComparisonOperator.EQUALS, "red")),
						configure.build()));
	}

	/**
	 * Unsupported shapes and escaped child paths fail explicitly; neutral root
	 * compatibility remains unchanged.
	 */
	@Test
	void unsupportedShapesAndNeutralRoot() throws Exception {
		for (String field : List.of("children.attributes", "notes", "labels.name")) {
			assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION,
					assertThrows(InvalidSearchQueryException.class, () -> search(field + ":red OR title:Orphan"))
						.getErrorCode());
		}
		assertEquals(QueryErrorCode.UNKNOWN_FIELD,
				assertThrows(InvalidSearchQueryException.class, () -> search("children.missing:red")).getErrorCode());
		var outside = AssociationQuery.any("children",
				AssociationQuery.where(new Comparison("title", ComparisonOperator.EQUALS, "Red")));
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withAssociationQuery(outside, enabled()).build()))
			.getErrorCode());
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(
						builder().withAssociationQuery(
								AssociationQuery.any("title",
										AssociationQuery
											.where(new Comparison("title", ComparisonOperator.EQUALS, "Red"))),
								enabled())
							.build()));
		var nested = AssociationQuery.any("children", AssociationQuery.any("children.attributes",
				AssociationQuery.where(new Comparison("children.attributes", ComparisonOperator.EQUALS, "red"))));
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withAssociationQuery(nested, enabled()).build()));
		var neutral = new SpecificationsBuilder<Parent>(settings("legacy")).withExtendedSearch("active>true", enabled())
			.build();
		var cb = this.entityManager.getCriteriaBuilder();
		var query = cb.createQuery(Long.class);
		assertNull(neutral.toPredicate(query.from(Parent.class), query, cb));
		assertEquals(List.of(1L),
				ids(new SpecificationsBuilder<Parent>(settings("legacy"))
					.withExtendedSearch("active>true OR title:Orphan", enabled())
					.build()));
	}

	/**
	 * Basic element fallbacks retain conversions, null checks and negative operations
	 * within an explicit child scope.
	 */
	@Test
	void elementFallbacksAndFailures() throws Exception {
		for (String input : List.of("labels!red", "labels!*re*", "labels!red*", "labels!*red", "labels NOT IN [red]")) {
			assertEquals(List.of(1L, 3L, 5L), search(input));
		}
		for (String input : List.of("labels>q", "labels>:red", "labels<r", "labels<:blue")) {
			assertEquals(input.contains("<") ? List.of(3L, 4L, 6L) : List.of(2L, 4L, 6L, 7L), search(input));
		}
		assertEquals(List.of(), search("labels IS NULL"));
		assertEquals(List.of(2L, 3L, 4L, 6L, 7L), search("labels IS NOT NULL"));
		var decline = QueryExtensions.builder()
			.collections(true)
			.forField("labels",
					new ValueConversion(String.class, ALL,
							(raw) -> raw.startsWith("CODE-") ? Optional.of(raw.substring(5)) : Optional.empty()))
			.factory("labels", new PredicateRegistration(String.class, ALL, (context) -> Optional.empty()))
			.factory("children.color", new PredicateRegistration(String.class, ALL, (context) -> Optional.empty()))
			.build();
		for (String input : List.of("labels:CODE-red", "labels:red", "children.color:red")) {
			assertEquals(List.of(2L, 4L, 6L, 7L), ids(builder().withExtendedSearch(input, decline).build()));
		}
		assertEquals(List.of(1L, 5L), ids(builder().withExtendedSearch("labels IS EMPTY", decline).build()));
		var equalityOnly = QueryExtensions.builder()
			.collections(true)
			.factory("labels",
					new PredicateRegistration(String.class, Set.of(ProfileOperation.EQUALS),
							(context) -> Optional.empty()))
			.build();
		assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION,
				assertThrows(InvalidSearchQueryException.class,
						() -> ids(builder().withExtendedSearch("labels IS EMPTY", equalityOnly).build()))
					.getErrorCode());
		assertEquals(QueryErrorCode.INVALID_VALUE,
				assertThrows(InvalidSearchQueryException.class, () -> search("scores:bad")).getErrorCode());
		var options = new SearchOptions(CaseSensitivity.INHERIT, Map.of("scores", CaseSensitivity.INSENSITIVE));
		assertEquals(QueryErrorCode.INVALID_SEARCH_OPTIONS, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder(options).withExtendedSearch("scores:7", enabled()).build()))
			.getErrorCode());
		var cb = this.entityManager.getCriteriaBuilder();
		var query = cb.createQuery(Long.class);
		var root = query.from(Parent.class);
		assertThrows(InvalidSearchQueryException.class, () -> CollectionPaths.resolve(root, "children.color"));
		assertThrows(InvalidSearchQueryException.class, () -> CollectionPaths.resolve(root, "peer.children.color"));
	}

	/**
	 * Transformation scope and failure contracts survive the collection execution path.
	 */
	@Test
	void scopedTransformationsAndLimits() throws Exception {
		var registry = QueryExtensions.builder()
			.collections(true)
			.transform("children.choice",
					(query) -> Optional.of(new Comparison("children.color", ((Comparison) query).operator(),
							((Comparison) query).value())))
			.transform("choice",
					(query) -> Optional.of(new Comparison("children.color", ((Comparison) query).operator(),
							((Comparison) query).value())))
			.build();
		var scoped = AssociationQuery
			.any("children",
					AssociationQuery.where(new Comparison("children.choice", ComparisonOperator.EQUALS, "red")))
			.and(AssociationQuery.where(new Comparison("title", ComparisonOperator.NOT_EQUALS, "Red")));
		assertEquals(List.of(4L, 6L, 7L), ids(builder().withAssociationQuery(scoped, registry).build()));
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withExtendedSearch("choice BETWEEN a AND z", registry).build()));
		var range = AssociationQuery.any("children", AssociationQuery.between("children.choice", "a", "z"));
		assertEquals(List.of(2L, 3L, 4L, 6L, 7L), ids(builder().withAssociationQuery(range, registry).build()));
		var escaping = QueryExtensions.builder()
			.collections(true)
			.transform("children.choice",
					(query) -> Optional.of(new Comparison("title", ComparisonOperator.EQUALS, "Red")))
			.build();
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withAssociationQuery(scoped, escaping).build()))
			.getErrorCode());
		var cycle = QueryExtensions.builder().collections(true).transform("children.choice", Optional::of).build();
		assertEquals(QueryErrorCode.QUERY_LIMIT_EXCEEDED, assertThrows(InvalidSearchQueryException.class,
				() -> ids(builder().withAssociationQuery(scoped, cycle).build()))
			.getErrorCode());
		for (String input : List.of("children.extra.owner.name:Alice", "features.owner.supervisor.parents.name:red")) {
			assertThrows(InvalidSearchQueryException.class, () -> search(input));
		}
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(new SpecificationsBuilder<Parent>(settings("legacy"))
					.withExtendedSearch("children.parent.active>true", enabled())
					.build()));
		var noPredicate = AssociationQuery.any("children", AssociationQuery
			.where(new Comparison("children.parent.active", ComparisonOperator.GREATER_THAN, "true")));
		assertThrows(InvalidSearchQueryException.class,
				() -> ids(new SpecificationsBuilder<Parent>(settings("legacy"))
					.withAssociationQuery(noPredicate, enabled())
					.build()));
	}

	/**
	 * One reusable specification renders concurrently into independent Criteria queries.
	 * Execute the completed queries in this transaction to verify their actual rows.
	 */
	@Test
	void concurrentCriteriaIsolation() throws Exception {
		var registry = enabled();
		var sameChild = builder()
			.withAssociationQuery(
					AssociationQuery.any("children",
							AssociationQuery.where(new Comparison("children.color", ComparisonOperator.EQUALS, "red")
								.and(new Comparison("children.size", ComparisonOperator.EQUALS, "large")))),
					registry)
			.build();
		var independent = builder().withExtendedSearch("children.color:red AND children.size:large", registry).build();
		var factory = this.entityManager.getEntityManagerFactory();
		var queries = java.util.stream.IntStream.range(0, 12).parallel().mapToObj((index) -> {
			var cb = factory.getCriteriaBuilder();
			var query = cb.createQuery(Long.class);
			var root = query.from(Parent.class);
			var specification = (index % 2 == 0) ? sameChild : independent;
			query.select(root.get("id"))
				.where(Objects.requireNonNull(specification.toPredicate(root, query, cb)))
				.orderBy(cb.asc(root.get("id")));
			assertEquals(0, root.getJoins().size());
			return query;
		}).toList();
		for (int i = 0; i < queries.size(); i++) {
			assertEquals((i % 2 == 0) ? List.of(7L) : List.of(4L, 6L, 7L),
					this.entityManager.createQuery(queries.get(i)).getResultList());
		}
	}

	private List<Long> search(String input) throws Exception {
		return ids(builder().withExtendedSearch(input, enabled()).build());
	}

	private SpecificationsBuilder<Parent> builder() throws Exception {
		return builder(SearchOptions.defaults());
	}

	private SpecificationsBuilder<Parent> builder(SearchOptions options) throws Exception {
		return new SpecificationsBuilder<>(settings("strict"), options);
	}

	private SearchSpec settings(String method) throws Exception {
		return Objects
			.requireNonNull(CollectionPolicyTest.class.getDeclaredMethod(method, Specification.class).getParameters()[0]
				.getAnnotation(SearchSpec.class));
	}

	private List<Long> ids(Specification<Parent> specification) {
		return ids(Parent.class, specification);
	}

	private <T> List<Long> ids(Class<T> type, Specification<T> specification) {
		var cb = this.entityManager.getCriteriaBuilder();
		var query = cb.createQuery(Long.class);
		var root = query.from(type);
		query.select(root.get("id"));
		var predicate = specification.toPredicate(root, query, cb);
		if (predicate != null) {
			query.where(predicate);
		}
		query.orderBy(cb.asc(root.get("id")));
		return this.entityManager.createQuery(query).getResultList();
	}

	private static void strict(@SearchSpec(strictValidation = true) Specification<?> query) {
	}

	private static void legacy(@SearchSpec(strictValidation = false, maxQueryLength = 0, maxNestingDepth = 0)
			Specification<?> query) {
	}

	private static void legacyInsensitive(@SearchSpec(caseSensitiveFlag = false, strictValidation = false) Specification<?> query) {
	}

	private static void denied(@SearchSpec(blackListedFields = "children.color") Specification<?> query) {
	}

	/**
	 * Inherited collection verifies metadata-based discovery.
	 *
	 * @author Gunnar Hillert
	 */
	@MappedSuperclass
	@Access(AccessType.FIELD)
	public static class ParentBase {

		@OneToMany(mappedBy = "parent")
		public Set<Child> children = new HashSet<>();

		/** Creates inherited collection metadata. */
		public ParentBase() {
		}

	}

	/**
	 * Parent combines entity, basic, getter-mapped and embeddable collections.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "CollectionParent")
	@Table(name = "collection_parent")
	public static class Parent extends ParentBase {

		@Id
		public Long id = 0L;

		public String title = "";

		public String tenant = "A";

		public Boolean active = true;

		@ManyToOne
		public @Nullable Parent peer;

		@ElementCollection
		public Set<String> labels = new HashSet<>();

		@ElementCollection
		public Set<Long> scores = new HashSet<>();

		@ElementCollection
		public List<Feature> features = new ArrayList<>();

		@ManyToMany
		public Set<Tag> tags = new HashSet<>();

		@ElementCollection
		@Column(name = "entry_text")
		public Map<String, String> notes = new HashMap<>();

		@Transient
		private Set<String> keywords = new HashSet<>();

		/** Creates a collection parent. */
		public Parent() {
		}

		/**
		 * Maps a collection whose backing field has a different Java name.
		 * @return search terms
		 */
		@Access(AccessType.PROPERTY)
		@ElementCollection
		public Set<String> getTerms() {
			return this.keywords;
		}

		/**
		 * Assigns getter-mapped collection contents.
		 * @param terms search terms
		 */
		public void setTerms(Set<String> terms) {
			this.keywords = terms;
		}

	}

	/**
	 * Actual child rows distinguish null fields and missing owners from an empty
	 * collection.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "CollectionChild")
	@Table(name = "collection_child")
	public static class Child {

		@Id
		public Long id = 0L;

		@ManyToOne(optional = false)
		public Parent parent = new Parent();

		public @Nullable String color;

		public @Nullable String size;

		public @Nullable Long price;

		@ManyToOne
		public @Nullable Owner owner;

		@OneToOne
		public @Nullable Detail detail;

		@Embedded
		@AttributeOverride(name = "color", column = @Column(name = "extra_color"))
		@AssociationOverride(name = "owner", joinColumns = @JoinColumn(name = "extra_owner_id"))
		public Feature extra = new Feature();

		@ElementCollection
		public Set<String> attributes = new HashSet<>();

		/** Creates a collection child. */
		public Child() {
		}

	}

	/**
	 * Shared labels exercise many-to-many traversal in both directions.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "CollectionTag")
	@Table(name = "collection_tag")
	public static class Tag {

		@Id
		public Long id = 0L;

		public @Nullable String name;

		@ManyToMany(mappedBy = "tags")
		public Set<Parent> parents = new HashSet<>();

		/** Creates a tag. */
		public Tag() {
		}

	}

	/**
	 * Optional many-to-one target can itself have a null scalar name.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "CollectionOwner")
	@Table(name = "collection_owner")
	public static class Owner {

		@Id
		public Long id = 0L;

		public @Nullable String name;

		@ManyToOne
		public @Nullable Owner supervisor;

		/** Creates an owner. */
		public Owner() {
		}

	}

	/**
	 * Optional one-to-one relation inside a collection.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "CollectionDetail")
	@Table(name = "collection_detail")
	public static class Detail {

		@Id
		public Long id = 0L;

		public String label = "";

		/** Creates a detail. */
		public Detail() {
		}

	}

	/**
	 * Embeddable element includes a nullable to-one relation.
	 *
	 * @author Gunnar Hillert
	 */
	@Embeddable
	public static class Feature {

		public @Nullable String color;

		@ManyToOne
		public @Nullable Owner owner;

		/** Creates an embeddable feature. */
		public Feature() {
		}

	}

}
