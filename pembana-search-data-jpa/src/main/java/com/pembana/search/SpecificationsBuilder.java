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

import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.Query;

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

	/**
	 * Creates an unconfigured builder whose specification contributes no predicate.
	 * @param searchSpecAnnotation matching, field-access and validation settings
	 */
	public SpecificationsBuilder(SearchSpec searchSpecAnnotation) {
		this.parser = new CriteriaParser<>(searchSpecAnnotation);
		this.translator = new JpaQueryTranslator<>(searchSpecAnnotation);
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
	 * Returns the current specification; JPA field/type checks happen on predicate
	 * construction.
	 * @return the current specification, never null
	 */
	public Specification<U> build() {
		return this.specs;
	}

}
