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

/**
 * Stable categories for rejected search input; callers should not parse reason text.
 *
 * @author Gunnar Hillert
 */
public enum QueryErrorCode {

	/** The expression cannot be parsed. */
	SYNTAX_ERROR,
	/** An explicitly configured size or nesting limit was exceeded. */
	QUERY_LIMIT_EXCEEDED,
	/** A blacklist or allowlist excludes the field. */
	FIELD_NOT_ALLOWED,
	/** Strict validation could not resolve the entity path. */
	UNKNOWN_FIELD,
	/** The value cannot be converted to the field's supported type. */
	INVALID_VALUE,
	/** Strict validation does not support this field type. */
	UNSUPPORTED_FIELD_TYPE,
	/** The field does not support the requested operation. */
	UNSUPPORTED_OPERATION

}
