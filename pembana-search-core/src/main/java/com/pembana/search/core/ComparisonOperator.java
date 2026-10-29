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
 * Explicit scalar operations; selecting an operation never adds implicit wildcards.
 *
 * @author Gunnar Hillert
 */
public enum ComparisonOperator {

	/** equals comparison. */
	EQUALS,
	/** not equals comparison. */
	NOT_EQUALS,
	/** greater than comparison. */
	GREATER_THAN,
	/** less than comparison. */
	LESS_THAN,
	/** starts with comparison. */
	STARTS_WITH,
	/** ends with comparison. */
	ENDS_WITH,
	/** contains comparison. */
	CONTAINS,
	/** doesnt start with comparison. */
	DOESNT_START_WITH,
	/** doesnt end with comparison. */
	DOESNT_END_WITH,
	/** doesnt contain comparison. */
	DOESNT_CONTAIN,
	/** greater than equals comparison. */
	GREATER_THAN_EQUALS,
	/** less than equals comparison. */
	LESS_THAN_EQUALS,
	/** is comparison. */
	IS,
	/** is not comparison. */
	IS_NOT

}
