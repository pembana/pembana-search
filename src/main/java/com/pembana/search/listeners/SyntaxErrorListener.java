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
package com.pembana.search.listeners;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Collects parser diagnostics in the original format.
 *
 * @author Spring Search contributors
 * @author Gunnar Hillert
 */
public class SyntaxErrorListener extends BaseErrorListener {

	/** Creates a SyntaxErrorListener with the inherited default configuration. */
	public SyntaxErrorListener() {
	}

	private final List<String> messages = new ArrayList<>();

	private int firstLine = -1;

	private int firstColumn = -1;

	/** {@inheritDoc} */
	@Override
	public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
			String msg, RecognitionException e) {
		if (this.messages.isEmpty()) {
			this.firstLine = line;
			this.firstColumn = charPositionInLine;
		}
		this.messages.add("line " + line + ":" + charPositionInLine + " " + msg);
	}

	/**
	 * Returns the first diagnostic's one-based line, or -1 if absent.
	 * @return the first diagnostic's one-based line, or -1 if absent
	 */
	public int getFirstLine() {
		return this.firstLine;
	}

	/**
	 *
	 * Returns the first diagnostic's zero-based column, or -1 if absent.
	 * @return the first diagnostic's zero-based column, or -1 if absent
	 *
	 */
	public int getFirstColumn() {
		return this.firstColumn;
	}

	/**
	 *
	 * Returns whether any lexer or parser diagnostic was collected.
	 * @return whether any lexer or parser diagnostic was collected
	 *
	 */
	public boolean hasErrors() {
		return !this.messages.isEmpty();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return this.messages.toString();
	}

}
