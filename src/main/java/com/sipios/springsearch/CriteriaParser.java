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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Parses expressions using the unchanged lexer, parser and visitor sequence. */
public final class CriteriaParser<T> {
    private final QueryVisitorImpl<T> visitor;
    public CriteriaParser(SearchSpec searchSpecAnnotation) {
        visitor = new QueryVisitorImpl<>(searchSpecAnnotation);
    }
    public Specification<T> parse(String searchParam) {
        QueryLexer lexer = new QueryLexer(CharStreams.fromString(searchParam));
        QueryParser parser = new QueryParser(new CommonTokenStream(lexer));
        SyntaxErrorListener listener = new SyntaxErrorListener();
        parser.addErrorListener(listener);
        QueryParser.InputContext input = parser.input();
        if (parser.getNumberOfSyntaxErrors() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search query: " + listener);
        }
        return visitor.visit(input);
    }
}
