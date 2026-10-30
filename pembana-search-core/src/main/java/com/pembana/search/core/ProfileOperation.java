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
 * Profile permissions for existing comparisons, membership and simple whole-value
 * patterns.
 *
 * @author Gunnar Hillert
 */
public enum ProfileOperation {

	/** Permission for EQUALS. */
	EQUALS,
	/** Permission for NOT_EQUALS. */
	NOT_EQUALS,
	/** Permission for GREATER_THAN. */
	GREATER_THAN,
	/** Permission for LESS_THAN. */
	LESS_THAN,
	/** Permission for STARTS_WITH. */
	STARTS_WITH,
	/** Permission for ENDS_WITH. */
	ENDS_WITH,
	/** Permission for CONTAINS. */
	CONTAINS,
	/** Permission for DOESNT_START_WITH. */
	DOESNT_START_WITH,
	/** Permission for DOESNT_END_WITH. */
	DOESNT_END_WITH,
	/** Permission for DOESNT_CONTAIN. */
	DOESNT_CONTAIN,
	/** Permission for GREATER_THAN_EQUALS. */
	GREATER_THAN_EQUALS,
	/** Permission for LESS_THAN_EQUALS. */
	LESS_THAN_EQUALS,
	/** Permission for IS. */
	IS,
	/** Permission for IS_NOT. */
	IS_NOT,
	/** Permission for IN. */
	IN,
	/** Permission for NOT_IN. */
	NOT_IN,
	/** Permission for GLOB. */
	GLOB

}
