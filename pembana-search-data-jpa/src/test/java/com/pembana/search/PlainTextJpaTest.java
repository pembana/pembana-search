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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.pembana.search.core.PlainTextQuery;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchConfiguration;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchMode;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;
import com.pembana.search.core.TextPattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs opt-in text matching and mode overrides on both supported database profiles.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
public class PlainTextJpaTest {

	@Autowired
	EntityManager entityManager;

	/**
	 * Seeds distributed words, literal metacharacters, empty strings, and all-null
	 * values.
	 */
	@BeforeEach
	void seed() {
		String[] titles = { "Sabal", "Sabal minor", "Orphan", "", null };
		String[] notes = { "minor", "100% a_b a.b+ ! \\ 'quote'", null, null, null };
		for (int i = 0; i < titles.length; i++) {
			var row = new TextRow();
			row.id = i + 1;
			row.title = titles[i];
			row.note = notes[i];
			this.entityManager.persist(row);
		}
		this.entityManager.flush();
	}

	/**
	 * Word conjunction and anchored patterns agree with the in-memory corpus, including
	 * literals.
	 */
	@Test
	void matchingCorpus() throws Exception {
		for (String input : List.of("minor sabal", "sab* min*", "100%", "a_b", "a.b+", "!", "\\", "'quote'", "*", "**",
				"missing")) {
			var specification = configured(SearchMode.STANDARD, SearchOptions.defaults())
				.withInput(input, SearchMode.SIMPLE)
				.build();
			var composed = PlainTextQuery.compose(input, config(SearchMode.STANDARD),
					com.pembana.search.core.ParserOptions.defaults());
			var all = this.entityManager.createQuery("from PlainTextRow order by id", TextRow.class).getResultList();
			var expected = all.stream()
				.filter((row) -> composed.patterns()
					.stream()
					.allMatch((pattern) -> pattern.matches(row.title, false) || pattern.matches(row.note, false)))
				.map((row) -> row.id)
				.toList();
			assertEquals(expected, ids(specification, 0, 10));
		}
		assertEquals(List.of(1L, 2L),
				ids(configured(SearchMode.SIMPLE, SearchOptions.defaults()).withInput("minor sabal").build(), 0, 10));
		assertEquals(List.of(1L, 2L, 3L, 4L),
				ids(configured(SearchMode.SIMPLE, SearchOptions.defaults()).withInput("*").build(), 0, 10));
	}

	/**
	 * Defaults and explicit overrides coexist, preserve replacement, and never fall back
	 * after syntax errors.
	 */
	@Test
	void queryModesAndCase() throws Exception {
		var builder = configured(SearchMode.SIMPLE, SearchOptions.defaults());
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L), ids(builder.withInput(null).build(), 0, 10));
		SearchSpec policy = Objects
			.requireNonNull(PlainTextJpaTest.class.getDeclaredMethod("policy", Specification.class).getParameters()[0]
				.getAnnotation(SearchSpec.class));
		assertEquals(List.of(3L),
				ids(new SpecificationsBuilder<TextRow>(policy).withInput("title:Orphan").build(), 0, 10));
		var limited = SpecificationsBuilder.<TextRow>configured(policy, SearchOptions.defaults(),
				new SearchConfiguration(SearchMode.SIMPLE, List.of("title", "note"), 1));
		var error = assertThrows(InvalidSearchQueryException.class, () -> limited.withInput("two words"));
		assertEquals(QueryErrorCode.QUERY_LIMIT_EXCEEDED, error.getErrorCode());

		assertEquals(List.of(3L), ids(builder.withInput("title:Orphan", SearchMode.STANDARD).build(), 0, 10));
		assertEquals(List.of(1L, 2L), ids(builder.withInput("sabal", SearchMode.INHERIT).build(), 0, 10));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L), ids(builder.withInput("  ").build(), 0, 10));
		assertThrows(InvalidSearchQueryException.class, () -> builder.withInput("broken", SearchMode.STANDARD));
		assertEquals(List.of(),
				ids(configured(SearchMode.SIMPLE, new SearchOptions(CaseSensitivity.SENSITIVE, Map.of()))
					.withInput("sabal")
					.build(), 0, 10));
		assertEquals(
				List.of(1L, 2L), ids(
						configured(SearchMode.SIMPLE,
								new SearchOptions(CaseSensitivity.SENSITIVE,
										Map.of("title", CaseSensitivity.INSENSITIVE)))
							.withInput("sabal")
							.build(),
						0, 10));
		assertThrows(InvalidSearchQueryException.class,
				() -> configured(SearchMode.SIMPLE,
						new SearchOptions(CaseSensitivity.INHERIT, Map.of("absent", CaseSensitivity.SENSITIVE)))
					.withInput("sabal"));
	}

	/** SQL predicates apply before paging and counting and reject unsupported fields. */
	@Test
	void pagesCountsAndValidation() throws Exception {
		var specification = configured(SearchMode.SIMPLE, SearchOptions.defaults()).withInput("sabal").build();
		assertEquals(List.of(1L), ids(specification, 0, 1));
		assertEquals(List.of(2L), ids(specification, 1, 1));
		var cb = this.entityManager.getCriteriaBuilder();
		var count = cb.createQuery(Long.class);
		var root = count.from(TextRow.class);
		count.select(cb.count(root)).where(Objects.requireNonNull(specification.toPredicate(root, count, cb)));
		assertEquals(2L, this.entityManager.createQuery(count).getSingleResult());
		for (String field : List.of("id", "unknown")) {
			var invalid = PlainTextSpecifications.<TextRow>translate(
					new PlainTextQuery(List.of(field), List.of(new TextPattern("x", false))), SearchOptions.defaults());
			assertThrows(InvalidSearchQueryException.class, () -> ids(invalid, 0, 10));
		}
	}

	/**
	 * Profiles apply equally to expression, programmatic and SIMPLE queries, and do not
	 * persist between calls.
	 */
	@Test
	void perQueryProfiles() throws Exception {
		var profile = new SearchProfile("public",
				Map.of("name", new SearchField("title", EnumSet.allOf(ProfileOperation.class))),
				Map.of("label", "name"), List.of("name"), new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
		var builder = configured(SearchMode.STANDARD, SearchOptions.defaults());
		assertEquals(List.of(1L), ids(builder.withInput("label:sabal", SearchMode.INHERIT, profile).build(), 0, 10));
		assertEquals(List.of(1L), ids(
				builder.withQuery(new Comparison("name", ComparisonOperator.EQUALS, "sabal"), profile).build(), 0, 10));
		assertEquals(List.of(1L, 2L), ids(builder.withInput("sabal", SearchMode.SIMPLE, profile).build(), 0, 10));
		assertEquals(List.of(3L), ids(builder.withSearch("title:Orphan").build(), 0, 10));
		assertThrows(InvalidSearchQueryException.class, () -> builder.withSearch("title:Orphan", profile));
		assertThrows(InvalidSearchQueryException.class, () -> builder.withSearch("note:minor", profile));
		assertThrows(InvalidSearchQueryException.class, () -> builder.withSearch("broken", profile));
		var admin = new SearchProfile("admin",
				Map.of("description", new SearchField("note", EnumSet.allOf(ProfileOperation.class))), Map.of(),
				List.of("description"), SearchOptions.defaults());
		assertEquals(List.of(1L), ids(builder.withInput("minor", SearchMode.SIMPLE, admin).build(), 0, 10));
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L),
				ids(builder.withInput(null, SearchMode.SIMPLE, profile).build(), 0, 10));
		assertEquals(List.of(),
				ids(configured(SearchMode.STANDARD, new SearchOptions(CaseSensitivity.SENSITIVE, Map.of()))
					.withSearch("name:sabal", profile)
					.build(), 0, 10));
		assertEquals(List.of(1L),
				ids(configured(SearchMode.STANDARD,
						new SearchOptions(CaseSensitivity.SENSITIVE, Map.of("label", CaseSensitivity.INSENSITIVE)))
					.withSearch("name:sabal", profile)
					.build(), 0, 10));
		assertThrows(InvalidSearchQueryException.class,
				() -> configured(SearchMode.STANDARD,
						new SearchOptions(CaseSensitivity.INHERIT,
								Map.of("label", CaseSensitivity.SENSITIVE, "name", CaseSensitivity.INSENSITIVE)))
					.withSearch("name:Sabal", profile));
	}

	/**
	 * Canonical deny rules survive aliasing, and numeric fields retain type checks under
	 * profile defaults.
	 */
	@Test
	void profileCanonicalPermissions() throws Exception {
		var profile = new SearchProfile("numeric",
				Map.of("key", new SearchField("id", EnumSet.allOf(ProfileOperation.class))), Map.of(), List.of(),
				new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
		assertEquals(List.of(2L, 3L),
				ids(configured(SearchMode.STANDARD, SearchOptions.defaults()).withSearch("key BETWEEN 2 AND 3", profile)
					.build(), 0, 10));
		var invalid = configured(SearchMode.STANDARD,
				new SearchOptions(CaseSensitivity.INHERIT, Map.of("key", CaseSensitivity.INSENSITIVE)))
			.withSearch("key:1", profile)
			.build();
		assertThrows(InvalidSearchQueryException.class, () -> ids(invalid, 0, 10));
		SearchSpec policy = Objects.requireNonNull(
				PlainTextJpaTest.class.getDeclaredMethod("restrictedPolicy", Specification.class).getParameters()[0]
					.getAnnotation(SearchSpec.class));
		assertThrows(InvalidSearchQueryException.class,
				() -> new SpecificationsBuilder<TextRow>(policy).withSearch("key:1", profile));
	}

	private static void restrictedPolicy(
			@SearchSpec(allowedFields = { "id" }, blackListedFields = { "id" }) Specification<?> query) {
	}

	private SearchConfiguration config(SearchMode mode) {
		return new SearchConfiguration(mode, List.of("title", "note"), 1024);
	}

	private SpecificationsBuilder<TextRow> configured(SearchMode mode, SearchOptions options) throws Exception {
		SearchSpec policy = Objects
			.requireNonNull(PlainTextJpaTest.class.getDeclaredMethod("policy", Specification.class).getParameters()[0]
				.getAnnotation(SearchSpec.class));
		return SpecificationsBuilder.configured(policy, options, config(mode));
	}

	private List<Long> ids(Specification<TextRow> specification, int offset, int limit) {
		var cb = this.entityManager.getCriteriaBuilder();
		var query = cb.createQuery(Long.class);
		var root = query.from(TextRow.class);
		var predicate = specification.toPredicate(root, query, cb);
		query.select(root.get("id")).orderBy(cb.asc(root.get("id")));
		if (predicate != null) {
			query.where(predicate);
		}
		return this.entityManager.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
	}

	private static void policy(@SearchSpec(strictValidation = true) Specification<?> query) {
	}

	/**
	 * Root String field fixture independent of application entities.
	 *
	 * @author Gunnar Hillert
	 */
	@Entity(name = "PlainTextRow")
	@Table(name = "plain_text_row")
	public static class TextRow {

		@Id
		public long id;

		public @Nullable String title;

		public @Nullable String note;

		/** Creates a fixture for JPA. */
		public TextRow() {
		}

	}

}
