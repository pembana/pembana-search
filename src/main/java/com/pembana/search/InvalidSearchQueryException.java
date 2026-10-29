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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A bad-request diagnostic with a stable category and optional entity field. Its
 * ProblemDetail exposes {@code code} and, when known, {@code field}. Applications choose
 * whether and how to render that body through MVC advice.
 *
 * @author Gunnar Hillert
 */
public final class InvalidSearchQueryException extends ResponseStatusException {

	private static final long serialVersionUID = 1L;

	/** Retained diagnostic metadata for this instance. */
	private final QueryErrorCode errorCode;

	/** Retained diagnostic metadata for this instance. */
	private final String field;

	/**
	 * Creates a diagnostic without including a rejected value in structured metadata.
	 * @param errorCode stable rejection category
	 * @param field complete entity path, or null for expression-wide errors
	 * @param reason human-readable explanation
	 */
	public InvalidSearchQueryException(QueryErrorCode errorCode, String field, String reason) {
		super(HttpStatus.BAD_REQUEST, reason);
		this.errorCode = errorCode;
		this.field = field;
		getBody().setProperty("code", errorCode.name());
		if (field != null) {
			getBody().setProperty("field", field);
		}
	}

	/**
	 *
	 * Returns the stable rejection category.
	 * @return the stable rejection category
	 *
	 */
	public QueryErrorCode getErrorCode() {
		return this.errorCode;
	}

	/**
	 *
	 * Returns the complete entity path, or null for expression-wide errors.
	 * @return the complete entity path, or null for expression-wide errors
	 *
	 */
	public String getField() {
		return this.field;
	}

}
