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
package com.pembana.search.core;

import org.jspecify.annotations.Nullable;

/**
 * Structured parser failure; adapters choose transport status and response rendering.
 *
 * @author Gunnar Hillert
 */
public final class QueryParseException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	/** Retained errorCode diagnostic metadata. */
	private final DiagnosticCode errorCode;

	/** Retained field diagnostic metadata. */
	private final @Nullable String field;

	/** Retained reason diagnostic metadata. */
	private final String reason;

	/** Retained line diagnostic metadata. */
	private final int line;

	/** Retained column diagnostic metadata. */
	private final int column;

	/**
	 * Creates a failure without a source location.
	 * @param errorCode rejection category
	 * @param field rejected entity path, or null for an expression-wide failure
	 * @param reason human-readable explanation
	 */
	public QueryParseException(DiagnosticCode errorCode, @Nullable String field, String reason) {
		this(errorCode, field, reason, -1, -1);
	}

	/**
	 * Creates a located failure, retaining the original diagnostic text.
	 * @param errorCode rejection category
	 * @param field rejected entity path, or null for an expression-wide failure
	 * @param reason human-readable explanation
	 * @param line one-based source line, or -1 if absent
	 * @param column zero-based source column, or -1 if absent
	 */
	public QueryParseException(DiagnosticCode errorCode, @Nullable String field, String reason, int line, int column) {
		super(reason);
		this.errorCode = errorCode;
		this.field = field;
		this.reason = reason;
		this.line = line;
		this.column = column;
	}

	/**
	 * Returns the rejection category.
	 * @return rejection category
	 */
	public DiagnosticCode getErrorCode() {
		return this.errorCode;
	}

	/**
	 * Returns the rejected field.
	 * @return entity path, or null
	 */
	public @Nullable String getField() {
		return this.field;
	}

	/**
	 * Returns the original message.
	 * @return non-null diagnostic text
	 */
	public String getReason() {
		return this.reason;
	}

	/**
	 * Returns the source line.
	 * @return one-based line, or -1
	 */
	public int getLine() {
		return this.line;
	}

	/**
	 * Returns the source column.
	 * @return zero-based column, or -1
	 */
	public int getColumn() {
		return this.column;
	}

}
