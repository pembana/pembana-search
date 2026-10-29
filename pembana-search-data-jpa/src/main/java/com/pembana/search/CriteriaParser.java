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
import com.pembana.search.core.ExpressionParser;
import com.pembana.search.core.QueryParseException;
import com.pembana.search.core.SearchOptions;

/**
 * Parses expressions using the unchanged lexer, parser and visitor sequence.
 *
 * @param <T> entity type represented by the generated specification
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author vincentescoffier (upstream Git author)
 * @author Gunnar Hillert
 */
public final class CriteriaParser<T> {

	private final ExpressionParser parser;

	private final JpaQueryTranslator<T> translator;

	/**
	 * Creates a parser and adapter retaining the supplied policy.
	 * @param searchSpecAnnotation matching, field-access and validation settings
	 * @throws IllegalArgumentException if a configured query limit is negative
	 */
	public CriteriaParser(SearchSpec searchSpecAnnotation) {
		this(searchSpecAnnotation, SearchOptions.defaults());
	}

	/**
	 * Creates a CriteriaParser with explicit case choices and configured fallback policy.
	 * @param searchSpecAnnotation configured matching and validation settings
	 * @param searchOptions request-wide and per-property choices
	 */
	public CriteriaParser(SearchSpec searchSpecAnnotation, SearchOptions searchOptions) {
		this.parser = new ExpressionParser(JpaQueryTranslator.parserOptions(searchSpecAnnotation));
		this.translator = new JpaQueryTranslator<>(searchSpecAnnotation, searchOptions);
	}

	/**
	 * Parses into the immutable core model and translates it without executing a query.
	 * @param searchParam non-null expression; an empty expression is invalid here
	 * @return specification whose values and paths are resolved by JPA later
	 * @throws InvalidSearchQueryException if syntax or configured input checks fail
	 */
	public Specification<T> parse(String searchParam) {
		try {
			return this.translator.translate(this.parser.parse(searchParam));
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

}
