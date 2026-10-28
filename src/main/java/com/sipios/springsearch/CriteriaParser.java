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
package com.sipios.springsearch;

import com.sipios.springsearch.anotation.SearchSpec;
import com.sipios.springsearch.grammar.QueryLexer;
import com.sipios.springsearch.grammar.QueryParser;
import com.sipios.springsearch.listeners.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.springframework.data.jpa.domain.Specification;

/** Parses expressions using the unchanged lexer, parser and visitor sequence. */
public final class CriteriaParser<T> {
    private final QueryVisitorImpl<T> visitor;
    private final SearchSpec settings;
    public CriteriaParser(SearchSpec searchSpecAnnotation) {
        if (searchSpecAnnotation.maxQueryLength() < 0 || searchSpecAnnotation.maxNestingDepth() < 0) {
            throw new IllegalArgumentException("Search query limits must be non-negative");
        }
        visitor = new QueryVisitorImpl<>(searchSpecAnnotation);
        settings = searchSpecAnnotation;
    }
    public Specification<T> parse(String searchParam) {
        if (settings.maxQueryLength() > 0 && searchParam.length() > settings.maxQueryLength()) {
            throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, null,
                    "Search query exceeds maximum length of " + settings.maxQueryLength());
        }
        QueryLexer lexer = new QueryLexer(CharStreams.fromString(searchParam));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SyntaxErrorListener listener = new SyntaxErrorListener();
        if (settings.strictValidation()) {
            lexer.removeErrorListeners();
            lexer.addErrorListener(listener);
        }
        if (settings.maxNestingDepth() > 0) {
            tokens.fill();
            int depth = 0;
            for (var token : tokens.getTokens()) {
                if (token.getType() == QueryLexer.LPAREN && ++depth > settings.maxNestingDepth()) {
                    throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, null,
                            "Search query exceeds maximum nesting depth of " + settings.maxNestingDepth());
                }
                if (token.getType() == QueryLexer.RPAREN) { depth--; }
            }
        }
        QueryParser parser = new QueryParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        QueryParser.InputContext input = parser.input();
        if (parser.getNumberOfSyntaxErrors() > 0 || (settings.strictValidation() && listener.hasErrors())) {
            var error = new InvalidSearchQueryException(QueryErrorCode.SYNTAX_ERROR, null, "Invalid search query: " + listener);
            error.getBody().setProperty("line", listener.getFirstLine());
            error.getBody().setProperty("column", listener.getFirstColumn());
            throw error;
        }
        return visitor.visit(input);
    }
}
