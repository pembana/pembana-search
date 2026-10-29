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

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.grammar.QueryLexer;
import com.pembana.search.grammar.QueryParser;
import com.pembana.search.listeners.SyntaxErrorListener;

/**
 * Parses expressions using the unchanged lexer, parser and visitor sequence.
 *
 * @param <T> entity type represented by the generated specification
 * @author Spring Search contributors
 * @author Gunnar Hillert
 */
public final class CriteriaParser<T> {

	private final QueryVisitorImpl<T> visitor;

	private final SearchSpec settings;

	/**
	 * Creates a parser retaining the supplied policy.
	 * @param searchSpecAnnotation matching, field-access and validation settings
	 * @throws IllegalArgumentException if a configured query limit is negative
	 */
	public CriteriaParser(SearchSpec searchSpecAnnotation) {
		if (searchSpecAnnotation.maxQueryLength() < 0 || searchSpecAnnotation.maxNestingDepth() < 0) {
			throw new IllegalArgumentException("Search query limits must be non-negative");
		}
		this.visitor = new QueryVisitorImpl<>(searchSpecAnnotation);
		this.settings = searchSpecAnnotation;
	}

	/**
	 * Parses an advanced expression without executing a database query.
	 * @param searchParam non-null expression; an empty expression is invalid here
	 * @return a specification whose values and paths are resolved by JPA later
	 * @throws InvalidSearchQueryException if syntax or configured input checks fail
	 */
	public Specification<T> parse(String searchParam) {
		if (this.settings.maxQueryLength() > 0 && searchParam.length() > this.settings.maxQueryLength()) {
			throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, null,
					"Search query exceeds maximum length of " + this.settings.maxQueryLength());
		}
		QueryLexer lexer = new QueryLexer(CharStreams.fromString(searchParam));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		SyntaxErrorListener listener = new SyntaxErrorListener();
		if (this.settings.strictValidation()) {
			lexer.removeErrorListeners();
			lexer.addErrorListener(listener);
		}
		if (this.settings.maxNestingDepth() > 0) {
			tokens.fill();
			int depth = 0;
			for (var token : tokens.getTokens()) {
				if (token.getType() == QueryLexer.LPAREN && ++depth > this.settings.maxNestingDepth()) {
					throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, null,
							"Search query exceeds maximum nesting depth of " + this.settings.maxNestingDepth());
				}
				if (token.getType() == QueryLexer.RPAREN) {
					depth--;
				}
			}
		}
		QueryParser parser = new QueryParser(tokens);
		parser.removeErrorListeners();
		parser.addErrorListener(listener);
		QueryParser.InputContext input = parser.input();
		if (parser.getNumberOfSyntaxErrors() > 0 || (this.settings.strictValidation() && listener.hasErrors())) {
			var error = new InvalidSearchQueryException(QueryErrorCode.SYNTAX_ERROR, null,
					"Invalid search query: " + listener);
			error.getBody().setProperty("line", listener.getFirstLine());
			error.getBody().setProperty("column", listener.getFirstColumn());
			throw error;
		}
		return this.visitor.visit(input);
	}

}
