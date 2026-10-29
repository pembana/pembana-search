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

/**
 * Parser rejection categories independent of HTTP rendering.
 *
 * @author Gunnar Hillert
 */
public enum DiagnosticCode {

	/** Expression syntax is invalid. */
	SYNTAX_ERROR,
	/** A configured expression limit was exceeded. */
	QUERY_LIMIT_EXCEEDED,
	/** A configured field restriction rejected a path. */
	FIELD_NOT_ALLOWED,
	/** A strict scalar check rejected a value. */
	INVALID_VALUE

}
