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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.criteria.JoinType;
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
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;
import com.pembana.search.core.ValueConversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies per-query optional-to-one navigation, SQL null semantics and root paging on
 * both databases.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
public class OptionalToOneTest {

	private static final Set<ProfileOperation> ALL = EnumSet.allOf(ProfileOperation.class);

	@Autowired
	EntityManager entityManager;

	/**
	 * Creates missing associations at several levels, shared authors and a separate
	 * tenant.
	 */
	@BeforeEach
	void seed() {
		var press = new Publisher();
		press.id = 1L;
		press.name = "Press";
		this.entityManager.persist(press);
		var writer = author(1L, "Writer", press, press, "Paris");
		var other = author(2L, "Other", null, press, "London");
		var unnamed = author(3L, null, press, press, null);
		var empty = author(4L, "Empty", null, press, null);
		var cover = new Cover();
		cover.id = 1L;
		cover.label = "Owned";
		this.entityManager.persist(cover);
		for (long id = 1; id <= 7; id++) {
			var book = new Book();
			book.id = id;
			book.title = switch ((int) id) {
				case 1 -> "Written";
				case 2, 4 -> "Orphan";
				case 3 -> "Unrelated";
				case 5 -> "Blank";
				case 6 -> "Second";
				default -> "Missing";
			};
			book.tenant = (id == 4) ? "B" : "A";
			book.author = (id == 1 || id == 6) ? writer : (id == 3) ? other : (id == 5) ? unnamed : null;
			book.inheritedAuthor = (id == 3) ? empty : book.author;
			book.setAlternativeAuthor(book.author);
			book.extra.caption = (id == 1) ? "Root" : "Other";
			if (id == 1) {
				book.cover = cover;
				book.tags.add("tag");
			}
			this.entityManager.persist(book);
			if (id == 1) {
				var review = new Review();
				review.id = 1L;
				review.book = book;
				review.label = "Reviewed";
				this.entityManager.persist(review);
			}
		}
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private Author author(long id, @Nullable String name, @Nullable Publisher publisher, Publisher imprint,
			@Nullable String city) {
		var author = new Author();
		author.id = id;
		author.name = name;
		author.publisher = publisher;
		author.imprint = imprint;
		author.contact.city = city;
		this.entityManager.persist(author);
		return author;
	}

	private QueryExtensions enabled() {
		return QueryExtensions.builder().optionalToOne(true).build();
	}

	/**
	 * A matching orphan survives an OR branch only when this query selects the new
	 * policy.
	 */
	@Test
	void optInAndDeactivation() throws Exception {
		String input = "author.name:Writer OR title:Orphan";
		var builder = builder();
		assertEquals(List.of(1L, 6L), ids(builder.withSearch(input).build()));
		assertEquals(List.of(1L, 2L, 4L, 6L), ids(builder.withExtendedSearch(input, enabled()).build()));
		assertEquals(List.of(1L, 6L), ids(builder.withSearch(input).build()));
		assertEquals(List.of(1L, 6L),
				ids(builder.withExtendedSearch(input, QueryExtensions.builder().optionalToOne(false).build()).build()));
		var configuration = QueryExtensions.builder().optionalToOne(true);
		var snapshot = configuration.build();
		configuration.optionalToOne(false);
		assertEquals(List.of(1L, 2L, 4L, 6L), ids(builder.withExtendedSearch(input, snapshot).build()));
		assertEquals(List.of(1L, 6L), ids(builder.withExtendedSearch(input, configuration.build()).build()));
	}

	/**
	 * Positive, negative, explicit null and membership predicates preserve SQL truth
	 * rules for missing values.
	 */
	@Test
	void nullNegationAndMembership() throws Exception {
		assertEquals(List.of(1L, 6L), search("author.name:Writer"));
		assertEquals(List.of(3L), search("author.name!Writer"));
		assertEquals(List.of(2L, 4L, 5L, 7L), search("author.name IS NULL"));
		assertEquals(List.of(2L, 4L, 7L), search("author IS NULL"));
		assertEquals(List.of(1L, 3L, 6L), search("author.name IS NOT NULL"));
		assertEquals(List.of(1L, 2L, 4L, 6L), search("author.name IN [Writer] OR title:Orphan"));
		assertEquals(List.of(), search("author.name IN []"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), search("author.name NOT IN []"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 6L), search("author.name BETWEEN A AND Z OR title:Orphan"));
		var notWriter = new Comparison("author.name", ComparisonOperator.EQUALS, "Writer").not();
		assertEquals(List.of(3L), ids(builder().withExtendedQuery(notWriter, enabled()).build()));
	}

	/**
	 * Nested optional links and required links after an optional parent all preserve
	 * matching root branches.
	 */
	@Test
	void nestedToOnePaths() throws Exception {
		assertEquals(List.of(1L, 2L, 4L, 5L, 6L), search("author.publisher.name:Press OR title:Orphan"));
		assertEquals(List.of(2L, 3L, 4L, 7L), search("author.publisher.name IS NULL"));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), search("author.imprint.name:Press OR title:Orphan"));
		assertEquals(List.of(1L, 2L, 4L, 6L), search("author.contact.city:Paris OR title:Orphan"));
		assertEquals(List.of(1L), search("extra.caption:Root"));
	}

	/**
	 * Owning/inverse one-to-one, inherited mappings and getter-mapped associations
	 * resolve through JPA metadata.
	 */
	@Test
	void mappingStyles() throws Exception {
		assertEquals(List.of(1L, 2L, 4L), search("cover.label:Owned OR title:Orphan"));
		assertEquals(List.of(1L, 2L, 4L), search("review.label:Reviewed OR title:Orphan"));
		assertEquals(List.of(1L, 2L, 4L, 6L), search("inheritedAuthor.name:Writer OR title:Orphan"));
		assertEquals(List.of(1L, 2L, 4L, 6L), search("alternativeAuthor.name:Writer OR title:Orphan"));
	}

	/**
	 * Direct and dotted collection emptiness keep their existing predicate mechanism
	 * without traversing children.
	 */
	@Test
	void terminalCollectionEmptiness() throws Exception {
		assertEquals(List.of(2L, 3L, 4L, 5L, 6L, 7L), search("tags IS EMPTY"));
		assertEquals(List.of(1L), search("tags IS NOT EMPTY"));
		assertEquals(List.of(2L, 4L, 7L), search("author.books IS EMPTY"));
		assertEquals(List.of(1L, 3L, 5L, 6L), search("author.books IS NOT EMPTY"));
		assertEquals(List.of(2L, 3L, 4L, 7L), search("inheritedAuthor.books IS EMPTY"));
	}

	/**
	 * Unsupported navigation is rejected explicitly instead of introducing a to-many or
	 * embedded-association join.
	 */
	@Test
	void unsupportedAndUnknownPaths() throws Exception {
		for (String field : List.of("author.books.title", "author.contact.region.name", "title.value")) {
			var error = assertThrows(InvalidSearchQueryException.class, () -> search(field + ":x OR title:Orphan"));
			assertEquals(QueryErrorCode.UNSUPPORTED_OPERATION, error.getErrorCode());
			assertEquals(field, error.getField());
		}
		for (String field : List.of("unknown", "author.unknown", "author..name")) {
			var error = assertThrows(InvalidSearchQueryException.class, () -> search(field + ":x"));
			assertEquals(QueryErrorCode.UNKNOWN_FIELD, error.getErrorCode());
			assertEquals(field, error.getField());
		}
	}

	/**
	 * Shared specifications build fresh select/count paths and preserve caller sorting
	 * and mandatory root restrictions.
	 */
	@Test
	void countsPagesScopesAndCallerJoins() throws Exception {
		var specification = builder().withExtendedSearch("author.name:Writer OR title:Orphan", enabled()).build();
		assertEquals(List.of(1L, 2L, 4L, 6L), ids(specification));
		Specification<Book> tenant = (root, query, cb) -> cb.equal(root.get("tenant"), "A");
		assertEquals(List.of(1L, 2L, 6L), ids(tenant.and(specification)));
		for (int repetition = 0; repetition < 2; repetition++) {
			var cb = this.entityManager.getCriteriaBuilder();
			var count = cb.createQuery(Long.class);
			var root = count.from(Book.class);
			count.select(cb.count(root)).where(Objects.requireNonNull(specification.toPredicate(root, count, cb)));
			assertEquals(4L, this.entityManager.createQuery(count).getSingleResult());
			var page = cb.createQuery(Long.class);
			var pageRoot = page.from(Book.class);
			page.select(pageRoot.get("id"))
				.where(Objects.requireNonNull(specification.toPredicate(pageRoot, page, cb)))
				.orderBy(cb.desc(pageRoot.get("id")));
			assertEquals(List.of(6L, 4L), this.entityManager.createQuery(page).setMaxResults(2).getResultList());
			assertEquals(List.of(2L, 1L),
					this.entityManager.createQuery(page).setFirstResult(2).setMaxResults(2).getResultList());
		}
		Specification<Book> consumerJoin = (root, query, cb) -> {
			var join = root.join("author", JoinType.LEFT);
			join.on(cb.equal(join.get("name"), "Other"));
			return cb.conjunction();
		};
		assertEquals(List.of(1L, 2L, 4L, 6L), ids(consumerJoin.and(specification)));
	}

	/**
	 * Aliases, canonical restrictions and case choices use the same profile pipeline as
	 * other extensions.
	 */
	@Test
	void profilesCaseAndPermissions() throws Exception {
		var profile = new SearchProfile("books",
				Map.of("writer", new SearchField("author.name", ALL), "title", new SearchField("title", ALL)), Map.of(),
				List.of(), new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(builder().withExtendedSearch("writer:writer OR title:orphan", profile, enabled()).build()));
		var sensitive = new SearchOptions(CaseSensitivity.INHERIT, Map.of("writer", CaseSensitivity.SENSITIVE));
		assertEquals(List.of(2L, 4L),
				ids(new SpecificationsBuilder<Book>(settings("strict"), sensitive)
					.withExtendedSearch("writer:writer OR title:orphan", profile, enabled())
					.build()));
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(new SpecificationsBuilder<Book>(settings("allowed"))
					.withExtendedSearch("writer:Writer OR title:Orphan", profile, enabled())
					.build()));
		assertEquals(QueryErrorCode.FIELD_NOT_ALLOWED,
				assertThrows(InvalidSearchQueryException.class,
						() -> new SpecificationsBuilder<Book>(settings("denied"))
							.withExtendedSearch("writer:Writer OR title:Orphan", profile, enabled()))
					.getErrorCode());
	}

	/**
	 * Conversion, transformation, declined factories and custom helper-based predicates
	 * all preserve optional paths.
	 */
	@Test
	void extensionCoexistence() throws Exception {
		var registry = QueryExtensions.builder()
			.optionalToOne(true)
			.forField("author.name",
					new ValueConversion(String.class, ALL,
							(raw) -> (raw.startsWith("CODE-")) ? Optional.of(raw.substring(5)) : Optional.empty()))
			.transform("writerName",
					(query) -> Optional
						.of(new Comparison("author.name", ComparisonOperator.EQUALS, ((Comparison) query).value())))
			.build();
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(builder().withExtendedSearch("author.name:CODE-Writer OR title:Orphan", registry).build()));
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(builder().withExtendedSearch("writerName:Writer OR title:Orphan", registry).build()));
		var query = new Comparison("author.name", ComparisonOperator.EQUALS, "Writer")
			.or(new Comparison("title", ComparisonOperator.EQUALS, "Orphan"));
		assertEquals(List.of(1L, 2L, 4L, 6L), ids(builder().withExtendedQuery(query, registry).build()));
		var declined = QueryExtensions.builder()
			.optionalToOne(true)
			.factory("author.name", new PredicateRegistration(String.class, ALL, (context) -> Optional.empty()))
			.build();
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(builder().withExtendedSearch("author.name:Writer OR title:Orphan", declined).build()));
		var factory = QueryExtensions.builder()
			.optionalToOne(true)
			.factory("author.name",
					new PredicateRegistration(String.class, ALL,
							(context) -> Optional.of(context.builder()
								.equal(ToOnePaths.resolve(context.root(), context.field()), context.value()))))
			.build();
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(builder().withExtendedSearch("author.name:Writer OR title:Orphan", factory).build()));
	}

	/**
	 * Compatibility mode still uses legacy value conversion; concurrent policy snapshots
	 * do not leak across queries.
	 */
	@Test
	void compatibilityAndIsolation() throws Exception {
		assertEquals(List.of(1L, 2L, 4L, 6L),
				ids(new SpecificationsBuilder<Book>(settings("legacy"))
					.withExtendedSearch("author.name:Writer OR title:Orphan", enabled())
					.build()));
		var on = enabled();
		var off = QueryExtensions.builder().optionalToOne(false).build();
		SearchSpec settings = settings("strict");
		var queries = java.util.stream.IntStream.range(0, 12)
			.parallel()
			.mapToObj((index) -> new ExtendedQueryTranslator<Book>(settings, SearchOptions.defaults(),
					(index % 2 == 0) ? on : off, null)
				.parse("author.name:Writer OR title:Orphan"))
			.toList();
		for (int i = 0; i < queries.size(); i++) {
			assertEquals((i % 2 == 0) ? List.of(1L, 2L, 4L, 6L) : List.of(1L, 6L), ids(queries.get(i)));
		}
	}

	private List<Long> search(String input) throws Exception {
		return ids(builder().withExtendedSearch(input, enabled()).build());
	}

	private SpecificationsBuilder<Book> builder() throws Exception {
		return new SpecificationsBuilder<>(settings("strict"));
	}

	private SearchSpec settings(String name) throws Exception {
		return Objects
			.requireNonNull(OptionalToOneTest.class.getDeclaredMethod(name, Specification.class).getParameters()[0]
				.getAnnotation(SearchSpec.class));
	}

	private List<Long> ids(Specification<Book> specification) {
		var cb = this.entityManager.getCriteriaBuilder();
		var query = cb.createQuery(Long.class);
		var root = query.from(Book.class);
		query.select(root.get("id"))
			.where(Objects.requireNonNull(specification.toPredicate(root, query, cb)))
			.orderBy(cb.asc(root.get("id")));
		return this.entityManager.createQuery(query).getResultList();
	}

	private static void strict(@SearchSpec(strictValidation = true) Specification<?> query) {
	}

	private static void legacy(@SearchSpec Specification<?> query) {
	}

	private static void allowed(@SearchSpec(allowedFields = { "author.name", "title" }) Specification<?> query) {
	}

	private static void denied(@SearchSpec(allowedFields = { "author.name", "title" },
			blackListedFields = { "author.name" }) Specification<?> query) {
	}

	/**
	 * Shared metadata verifies inherited scalar properties without reflecting on Java
	 * fields.
	 *
	 * @author Gunnar Hillert
	 */
	@MappedSuperclass
	@Access(AccessType.FIELD)
	public static class Named {

		@Id
		public long id;

		public @Nullable String name;

		/** Creates persistent named metadata. */
		public Named() {
		}

	}

	/**
	 * Scalar target for nested optional and required to-one paths.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "OptionalPublisher")
	@Table(name = "optional_publisher")
	public static class Publisher extends Named {

		/** Creates a publisher for JPA. */
		public Publisher() {
		}

	}

	/**
	 * Embeddable scalar navigation is supported; an association after it is explicitly
	 * bounded out.
	 *
	 * @author Gunnar Hillert
	 */
	@Embeddable
	public static class Contact {

		public @Nullable String city;

		@ManyToOne
		public @Nullable Publisher region;

		/** Creates optional contact data. */
		public Contact() {
		}

	}

	/**
	 * Author has nested associations and a collection used only for terminal emptiness
	 * tests.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "OptionalAuthor")
	@Table(name = "optional_author")
	public static class Author extends Named {

		@ManyToOne
		public @Nullable Publisher publisher;

		@ManyToOne(optional = false)
		public Publisher imprint = new Publisher();

		@Embedded
		public Contact contact = new Contact();

		@OneToMany(mappedBy = "author")
		public Set<Book> books = new HashSet<>();

		/** Creates an author for JPA. */
		public Author() {
		}

	}

	/**
	 * An inherited association exercises metamodel rather than declared-field lookup.
	 *
	 * @author Gunnar Hillert
	 */
	@MappedSuperclass
	@Access(AccessType.FIELD)
	public static class BookLinks {

		@ManyToOne
		public @Nullable Author inheritedAuthor;

		/** Creates inherited book links. */
		public BookLinks() {
		}

	}

	/**
	 * Embedded scalar attributes on the query root require no association join.
	 *
	 * @author Gunnar Hillert
	 */
	@Embeddable
	public static class Extra {

		public String caption = "";

		/** Creates embedded root data. */
		public Extra() {
		}

	}

	/**
	 * Root fixture combines field, inherited, property and owning/inverse association
	 * mappings.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "OptionalBook")
	@Table(name = "optional_book")
	public static class Book extends BookLinks {

		@Id
		public long id;

		public String title = "";

		public String tenant = "";

		@ManyToOne
		public @Nullable Author author;

		@OneToOne
		public @Nullable Cover cover;

		@OneToOne(mappedBy = "book")
		public @Nullable Review review;

		@Embedded
		public Extra extra = new Extra();

		@ElementCollection
		public List<String> tags = new ArrayList<>();

		@Transient
		private @Nullable Author writer;

		/** Creates a book for JPA. */
		public Book() {
		}

		/**
		 * Maps a property whose Java backing field has a different name.
		 * @return optional alternate author
		 */
		@Access(AccessType.PROPERTY)
		@ManyToOne
		@JoinColumn(name = "alternate_author_id")
		public @Nullable Author getAlternativeAuthor() {
			return this.writer;
		}

		/**
		 * Assigns the property-mapped relation.
		 * @param author optional alternate author
		 */
		public void setAlternativeAuthor(@Nullable Author author) {
			this.writer = author;
		}

	}

	/**
	 * Owning one-to-one target, referenced uniquely from the root.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "OptionalCover")
	@Table(name = "optional_cover")
	public static class Cover {

		@Id
		public long id;

		public String label = "";

		/** Creates a cover for JPA. */
		public Cover() {
		}

	}

	/**
	 * Owning review link allows testing navigation from its inverse Book property.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "OptionalReview")
	@Table(name = "optional_review")
	public static class Review {

		@Id
		public long id;

		public String label = "";

		@OneToOne(optional = false)
		public Book book = new Book();

		/** Creates a review for JPA. */
		public Review() {
		}

	}

}
