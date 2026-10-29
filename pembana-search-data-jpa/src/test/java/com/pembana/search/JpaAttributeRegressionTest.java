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
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pembana.search.annotation.SearchSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies JPA attribute metadata and nested enum conversion on both database profiles.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
public class JpaAttributeRegressionTest {

	@Autowired
	EntityManager entityManager;

	/** Seeds empty/nonempty collections and nullable nested enum values. */
	@BeforeEach
	void seed() {
		PropertyMappedSample empty = new PropertyMappedSample();
		empty.setId(1);
		PropertyMappedSample tagged = new PropertyMappedSample();
		tagged.setId(2);
		tagged.setTags(new ArrayList<>(List.of("one")));
		this.entityManager.persist(empty);
		this.entityManager.persist(tagged);
		for (long id : List.of(1L, 2L)) {
			FieldMappedSample direct = new FieldMappedSample();
			direct.id = id;
			direct.owner = (id == 1) ? empty : tagged;
			InheritedSample inherited = new InheritedSample();
			inherited.id = id;
			if (id == 2) {
				direct.tags.add("one");
				inherited.tags.add("one");
			}
			this.entityManager.persist(direct);
			this.entityManager.persist(inherited);
		}
		for (long id : List.of(1L, 2L, 3L)) {
			EnumSample sample = new EnumSample();
			sample.id = id;
			sample.state = (id == 1) ? State.ACTIVE : (id == 2) ? State.INACTIVE : null;
			sample.privateState = (id == 1) ? PrivateState.ACTIVE : (id == 2) ? PrivateState.INACTIVE : null;
			this.entityManager.persist(sample);
		}
		this.entityManager.flush();
		this.entityManager.clear();
	}

	/** Direct field access retains empty and nonempty collection semantics. */
	@Test
	void declaredCollectionEmptiness() {
		checkEmptiness(FieldMappedSample.class, "tags");
	}

	/** Mapped-superclass collections behave like declared fields in both modes. */
	@Test
	void inheritedCollectionEmptiness() {
		checkEmptiness(InheritedSample.class, "tags");
	}

	/** Getter-mapped collections do not require a Java field with the property name. */
	@Test
	void propertyAccessCollectionEmptiness() {
		checkEmptiness(PropertyMappedSample.class, "tags");
	}

	/** Dotted paths resolve plural metadata after traversing an association. */
	@Test
	void nestedPropertyAccessCollectionEmptiness() {
		checkEmptiness(FieldMappedSample.class, "owner.tags");
	}

	/** Scalar operators remain invalid for every collection mapping style. */
	@Test
	void collectionComparisonsRemainUnsupported() {
		for (var type : List.of(FieldMappedSample.class, InheritedSample.class, PropertyMappedSample.class)) {
			for (boolean strict : List.of(false, true)) {
				var error = assertThrows(ResponseStatusException.class, () -> ids(type, "tags:one", strict));
				assertEquals(400, error.getStatusCode().value());
				if (strict) {
					assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION,
							((InvalidSearchQueryException) error).getErrorCode());
				}
			}
		}
	}

	/** Nested enum equality and inequality bind actual enum constants. */
	@Test
	void nestedEnumEqualityAndInequality() {
		for (boolean strict : List.of(false, true)) {
			assertEquals(List.of(1L), ids(EnumSample.class, "state:ACTIVE", strict));
			assertEquals(List.of(2L), ids(EnumSample.class, "state!ACTIVE", strict));
		}
	}

	/** Membership and negated membership convert each nested enum item. */
	@Test
	void nestedEnumMembership() {
		for (boolean strict : List.of(false, true)) {
			assertEquals(List.of(1L, 2L), ids(EnumSample.class, "state IN [ACTIVE,INACTIVE]", strict));
			assertEquals(List.of(2L), ids(EnumSample.class, "state NOT IN [ACTIVE]", strict));
		}
	}

	/** Null checks retain SQL null semantics rather than invoking enum conversion. */
	@Test
	void nestedEnumNullChecks() {
		for (boolean strict : List.of(false, true)) {
			assertEquals(List.of(3L), ids(EnumSample.class, "state IS NULL", strict));
			assertEquals(List.of(1L, 2L), ids(EnumSample.class, "state IS NOT NULL", strict));
		}
	}

	/** Private nested enum types do not require reflective method accessibility. */
	@Test
	void privateNestedEnumConversion() {
		for (boolean strict : List.of(false, true)) {
			assertEquals(List.of(1L), ids(EnumSample.class, "privateState:ACTIVE", strict));
			assertEquals(List.of(2L), ids(EnumSample.class, "privateState IN [INACTIVE]", strict));
		}
	}

	/** Unknown and incorrectly cased constants retain structured value errors. */
	@Test
	void invalidNestedEnumValuesRemainRejected() {
		for (boolean strict : List.of(false, true)) {
			for (String search : List.of("state:active", "state:MISSING", "state IN [ACTIVE,MISSING]")) {
				var error = assertThrows(InvalidSearchQueryException.class,
						() -> ids(EnumSample.class, search, strict));
				assertEquals(QueryErrorCode.INVALID_VALUE, error.getErrorCode());
				assertEquals("state", error.getField());
			}
		}
	}

	/**
	 * Tests both emptiness operators under independently configured validation modes.
	 * @param type mapped entity type
	 * @param field complete collection path
	 */
	private void checkEmptiness(Class<?> type, String field) {
		for (boolean strict : List.of(false, true)) {
			assertEquals(List.of(1L), ids(type, field + " IS EMPTY", strict));
			assertEquals(List.of(2L), ids(type, field + " IS NOT EMPTY", strict));
		}
	}

	/**
	 * Executes the public parser against the entity and returns deterministically sorted
	 * IDs.
	 * @param <T> entity type
	 * @param type mapped entity type
	 * @param search expression to execute
	 * @param strict whether strict validation is enabled
	 * @return matching entity IDs
	 */
	private <T> List<Long> ids(Class<T> type, String search, boolean strict) {
		SearchSpec settings;
		try {
			settings = Objects.requireNonNull(JpaAttributeRegressionTest.class
				.getDeclaredMethod(strict ? "strictPolicy" : "legacyPolicy", Specification.class)
				.getParameters()[0].getAnnotation(SearchSpec.class));
		}
		catch (ReflectiveOperationException exception) {
			throw new AssertionError("Missing regression test policy", exception);
		}
		var builder = this.entityManager.getCriteriaBuilder();
		var query = builder.createQuery(Long.class);
		var root = query.from(type);
		var specification = new CriteriaParser<T>(settings).parse(search);
		query.select(root.get("id"))
			.where(Objects.requireNonNull(specification.toPredicate(root, query, builder)))
			.orderBy(builder.asc(root.get("id")));
		return this.entityManager.createQuery(query).getResultList();
	}

	/**
	 * Defines strict type validation independently of mapping style.
	 * @param query annotation target
	 */
	private static void strictPolicy(@SearchSpec(strictValidation = true) Specification<?> query) {
	}

	/**
	 * Defines the unchanged compatibility policy.
	 * @param query annotation target
	 */
	private static void legacyPolicy(@SearchSpec Specification<?> query) {
	}

	/**
	 * Shared collection declared in an entity superclass.
	 *
	 * @author Gunnar Hillert
	 */
	@MappedSuperclass
	public static class TaggedBase {

		@ElementCollection
		public List<String> tags = new ArrayList<>();

		/** Creates an empty collection fixture for JPA. */
		public TaggedBase() {
		}

	}

	/**
	 * Entity inheriting its persistent collection.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "RegressionInherited")
	@Table(name = "regression_inherited")
	public static class InheritedSample extends TaggedBase {

		@Id
		public long id;

		/** Creates an inherited-attribute fixture for JPA. */
		public InheritedSample() {
		}

	}

	/**
	 * Control entity using ordinary field access and a nested association.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "RegressionFieldMapped")
	@Table(name = "regression_field_mapped")
	public static class FieldMappedSample {

		@Id
		public long id;

		@ElementCollection
		public List<String> tags = new ArrayList<>();

		@ManyToOne
		public @Nullable PropertyMappedSample owner;

		/** Creates a field-access fixture for JPA. */
		public FieldMappedSample() {
		}

	}

	/**
	 * Entity whose collection property intentionally has no same-named Java field.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "RegressionPropertyMapped")
	@Table(name = "regression_property_mapped")
	@Access(AccessType.PROPERTY)
	public static class PropertyMappedSample {

		private long key;

		private List<String> contents = new ArrayList<>();

		/** Creates a property-access fixture for JPA. */
		public PropertyMappedSample() {
		}

		/**
		 * Returns the persistent identifier.
		 * @return identifier
		 */
		@Id
		public long getId() {
			return this.key;
		}

		/**
		 * Sets the persistent identifier.
		 * @param id identifier
		 */
		public void setId(long id) {
			this.key = id;
		}

		/**
		 * Returns the mapped collection independently of its backing field name.
		 * @return tags
		 */
		@ElementCollection
		public List<String> getTags() {
			return this.contents;
		}

		/**
		 * Supplies the collection loaded through property access.
		 * @param tags persistent tags
		 */
		public void setTags(List<String> tags) {
			this.contents = tags;
		}

	}

	/**
	 * Public nested enum used by an entity attribute.
	 *
	 * @author Gunnar Hillert
	 */
	public enum State {

		/** Active sample. */
		ACTIVE,
		/** Inactive sample. */
		INACTIVE

	}

	/**
	 * Private nested enum used to verify conversion without reflective access.
	 *
	 * @author Gunnar Hillert
	 */
	private enum PrivateState {

		/** Active sample. */
		ACTIVE,
		/** Inactive sample. */
		INACTIVE

	}

	/**
	 * Entity with public/private nested enum attributes and null values.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "RegressionEnumSample")
	@Table(name = "regression_enum_sample")
	public static class EnumSample {

		@Id
		public long id;

		@Enumerated(EnumType.STRING)
		@Column(name = "sample_state")
		public @Nullable State state;

		@Enumerated(EnumType.STRING)
		@Column(name = "private_state")
		private @Nullable PrivateState privateState;

		/** Creates a nested-enum fixture for JPA. */
		public EnumSample() {
		}

	}

}
