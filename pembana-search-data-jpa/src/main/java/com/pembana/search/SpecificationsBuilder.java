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
/*
 * Derived from Spring Search, copyright (c) 2019-present Woody Rousseau
 * and other contributors, under the MIT license. See LICENSE.txt and NOTICE.
 */
package com.pembana.search;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.ExpressionParser;
import com.pembana.search.core.ParserOptions;
import com.pembana.search.core.PlainTextQuery;
import com.pembana.search.core.Query;
import com.pembana.search.core.QueryParseException;
import com.pembana.search.core.SearchConfiguration;
import com.pembana.search.core.SearchMode;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;

/**
 * Builds JPA specifications from advanced expressions using explicit annotation settings.
 *
 * @param <U> entity type represented by the generated specification
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author Gunnar Hillert
 */
public final class SpecificationsBuilder<U> {

	private Specification<U> specs = new NullSpecification<>();

	private final CriteriaParser<U> parser;

	private final JpaQueryTranslator<U> translator;

	private final SearchConfiguration configuration;

	private final SearchSpec settings;

	private final SearchOptions searchOptions;

	/**
	 * Creates an unconfigured builder whose specification contributes no predicate.
	 * @param searchSpecAnnotation matching, field-access and validation settings
	 */
	public SpecificationsBuilder(SearchSpec searchSpecAnnotation) {
		this(searchSpecAnnotation, SearchOptions.defaults());
	}

	/**
	 * Creates a SpecificationsBuilder with explicit case choices and configured fallback
	 * policy.
	 * @param searchSpecAnnotation configured matching and validation settings
	 * @param searchOptions request-wide and per-property choices
	 */
	public SpecificationsBuilder(SearchSpec searchSpecAnnotation, SearchOptions searchOptions) {
		this(searchSpecAnnotation, searchOptions, SearchConfiguration.defaults());
	}

	private SpecificationsBuilder(SearchSpec searchSpecAnnotation, SearchOptions searchOptions,
			SearchConfiguration configuration) {
		this.configuration = java.util.Objects.requireNonNull(configuration);
		this.settings = searchSpecAnnotation;
		this.searchOptions = searchOptions;
		this.parser = new CriteriaParser<>(searchSpecAnnotation, searchOptions);
		this.translator = new JpaQueryTranslator<>(searchSpecAnnotation, searchOptions);
	}

	/**
	 * Creates a builder with an explicit consumer default. Existing constructors keep
	 * STANDARD mode.
	 * @param <T> entity type
	 * @param settings existing validation and access policy
	 * @param options explicit case options
	 * @param configuration immutable consumer default and eligible text fields
	 * @return configured builder
	 */
	public static <T> SpecificationsBuilder<T> configured(SearchSpec settings, SearchOptions options,
			SearchConfiguration configuration) {
		return new SpecificationsBuilder<>(settings, options, configuration);
	}

	/**
	 * Replaces the current query using the consumer default interpretation.
	 * @param input raw input; null is neutral only in SIMPLE mode
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withInput(@Nullable String input) {
		return withInput(input, SearchMode.INHERIT);
	}

	/**
	 * Replaces the current query with a per-query mode override. Parse failures never
	 * fall back.
	 * @param input raw input
	 * @param mode explicit interpretation or INHERIT
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withInput(@Nullable String input, SearchMode mode) {
		java.util.Objects.requireNonNull(mode);
		SearchMode selected = (mode == SearchMode.INHERIT) ? this.configuration.defaultMode() : mode;
		if (selected == SearchMode.STANDARD) {
			return withSearch(java.util.Objects.requireNonNull(input));
		}
		try {
			var text = PlainTextQuery.compose(input, this.configuration,
					JpaQueryTranslator.parserOptions(this.settings));
			this.specs = PlainTextSpecifications.translate(text, this.searchOptions);
			return this;
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

	/**
	 * Replaces input using a profile selected for this query only; later calls without a
	 * profile use the ordinary field policy.
	 * @param input raw input
	 * @param mode per-query mode or INHERIT
	 * @param profile immutable field policy
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withInput(@Nullable String input, SearchMode mode, SearchProfile profile) {
		java.util.Objects.requireNonNull(mode);
		if (((mode == SearchMode.INHERIT) ? this.configuration.defaultMode() : mode) == SearchMode.STANDARD) {
			return withSearch(java.util.Objects.requireNonNull(input), profile);
		}
		try {
			var text = profile.composeText(input, this.configuration, JpaQueryTranslator.parserOptions(this.settings));
			this.specs = PlainTextSpecifications.translate(text, profile.textOptions(text, this.searchOptions));
			return this;
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

	/**
	 * Parses public names before applying canonical access restrictions and profile
	 * permissions.
	 * @param search STANDARD expression
	 * @param profile immutable policy for this query
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withSearch(String search, SearchProfile profile) {
		var policy = JpaQueryTranslator.parserOptions(this.settings);
		var syntax = new ParserOptions(policy.strictValidation(), java.util.List.of(), java.util.List.of(),
				policy.maxQueryLength(), policy.maxNestingDepth());
		try {
			this.specs = this.translator.translate(new ExpressionParser(syntax).parse(search), profile);
			return this;
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

	/**
	 * Resolves a programmatic public-name query through a per-query profile.
	 * @param query immutable query
	 * @param profile selected field policy
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withQuery(Query query, SearchProfile profile) {
		this.specs = this.translator.translate(query, profile);
		return this;
	}

	/**
	 * Replaces this builder's previous expression; calls do not accumulate predicates.
	 * @param search advanced expression to parse
	 * @return this builder
	 * @throws InvalidSearchQueryException if parsing, configured limits or field access
	 * fail
	 */
	public SpecificationsBuilder<U> withSearch(String search) {
		this.specs = this.parser.parse(search);
		return this;
	}

	/**
	 * Replaces this builder's query using an immutable model without string parsing.
	 * @param query programmatically constructed query
	 * @return this builder
	 * @throws InvalidSearchQueryException if a field restriction rejects the query
	 */
	public SpecificationsBuilder<U> withQuery(Query query) {
		this.specs = this.translator.translate(query);
		return this;
	}

	/**
	 * Replaces STANDARD input using explicitly selected extensions and no profile.
	 * @param input expression
	 * @param extensions immutable consumer registrations
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withExtendedSearch(String input, QueryExtensions extensions) {
		this.specs = new ExtendedQueryTranslator<U>(this.settings, this.searchOptions, extensions, null).parse(input);
		return this;
	}

	/**
	 * Replaces STANDARD input using a profile and explicitly selected extensions.
	 * @param input expression
	 * @param profile per-query profile
	 * @param extensions immutable consumer registrations
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withExtendedSearch(String input, SearchProfile profile,
			QueryExtensions extensions) {
		this.specs = new ExtendedQueryTranslator<U>(this.settings, this.searchOptions, extensions, profile)
			.parse(input);
		return this;
	}

	/**
	 * Replaces a canonical programmatic query using explicitly selected extensions.
	 * @param query immutable query
	 * @param extensions immutable consumer registrations
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withExtendedQuery(Query query, QueryExtensions extensions) {
		this.specs = new ExtendedQueryTranslator<U>(this.settings, this.searchOptions, extensions, null)
			.translate(query);
		return this;
	}

	/**
	 * Replaces a public-name programmatic query using a profile and extensions.
	 * @param query immutable query
	 * @param profile per-query profile
	 * @param extensions immutable consumer registrations
	 * @return this builder
	 */
	public SpecificationsBuilder<U> withExtendedQuery(Query query, SearchProfile profile, QueryExtensions extensions) {
		this.specs = new ExtendedQueryTranslator<U>(this.settings, this.searchOptions, extensions, profile)
			.translate(query);
		return this;
	}

	/**
	 * Returns the current specification; JPA field/type checks happen on predicate
	 * construction.
	 * @return the current specification, never null
	 */
	public Specification<U> build() {
		return this.specs;
	}

}
