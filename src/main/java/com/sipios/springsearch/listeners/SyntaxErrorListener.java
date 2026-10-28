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
package com.sipios.springsearch.listeners;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/** Collects parser diagnostics in the original format. */
public class SyntaxErrorListener extends BaseErrorListener {
    private final List<String> messages = new ArrayList<>();
    private int firstLine = -1;
    private int firstColumn = -1;

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
            int charPositionInLine, String msg, RecognitionException e) {
        if (messages.isEmpty()) {
            firstLine = line;
            firstColumn = charPositionInLine;
        }
        messages.add("line " + line + ":" + charPositionInLine + " " + msg);
    }
    /** @return the first diagnostic's one-based line, or -1 if absent */
    public int getFirstLine() { return firstLine; }

    /** @return the first diagnostic's zero-based column, or -1 if absent */
    public int getFirstColumn() { return firstColumn; }

    /** @return whether any lexer or parser diagnostic was collected */
    public boolean hasErrors() { return !messages.isEmpty(); }

    @Override
    public String toString() { return messages.toString(); }
}
