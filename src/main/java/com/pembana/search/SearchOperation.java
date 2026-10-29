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
package com.pembana.search;

/**
 * Legacy predicate operations and parser symbols; the grammar determines accepted
 * expression syntax.
 *
 * @author Spring Search contributors
 * @author Gunnar Hillert
 */
public enum SearchOperation {

	/** equals predicate operation. */
	EQUALS,
	/** not equals predicate operation. */
	NOT_EQUALS,
	/** greater than predicate operation. */
	GREATER_THAN,
	/** less than predicate operation. */
	LESS_THAN,
	/** starts with predicate operation. */
	STARTS_WITH,
	/** ends with predicate operation. */
	ENDS_WITH,
	/** contains predicate operation. */
	CONTAINS,
	/** doesnt start with predicate operation. */
	DOESNT_START_WITH,
	/** doesnt end with predicate operation. */
	DOESNT_END_WITH,
	/** doesnt contain predicate operation. */
	DOESNT_CONTAIN,
	/** greater than equals predicate operation. */
	GREATER_THAN_EQUALS,
	/** less than equals predicate operation. */
	LESS_THAN_EQUALS,
	/** in array predicate operation. */
	IN_ARRAY,
	/** not in array predicate operation. */
	NOT_IN_ARRAY,
	/** is predicate operation. */
	IS,
	/** is not predicate operation. */
	IS_NOT,
	/** between predicate operation. */
	BETWEEN,
	/** not between predicate operation. */
	NOT_BETWEEN;

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String[] SIMPLE_OPERATION_SET = { ":", "!", ">", "<", "~", ">:", "<:" };

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String ZERO_OR_MORE_REGEX = "*";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String OR_OPERATOR = "OR";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String AND_OPERATOR = "AND";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String LEFT_PARANTHESIS = "(";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String RIGHT_PARANTHESIS = ")";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String EMPTY = "EMPTY";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final String NULL = "NULL";

	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final Companion Companion = new Companion();

	/**
	 * Maps a scalar operator token to its operation.
	 * @param input operator token
	 * @return mapped operation, or null for an unsupported token
	 */
	public static SearchOperation getSimpleOperation(String input) {
		return switch (input) {
			case ":" -> EQUALS;
			case "!" -> NOT_EQUALS;
			case ">" -> GREATER_THAN;
			case "<" -> LESS_THAN;
			case ">:" -> GREATER_THAN_EQUALS;
			case "<:" -> LESS_THAN_EQUALS;
			default -> null;
		};
	}

	/**
	 * Retains the original companion entry points without a Kotlin runtime.
	 * @author Gunnar Hillert
	 */
	/** Legacy parser symbol/accessor retained for compatibility. */
	public static final class Companion {

		private Companion() {
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String[] getSIMPLE_OPERATION_SET() {
			return SIMPLE_OPERATION_SET;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getZERO_OR_MORE_REGEX() {
			return ZERO_OR_MORE_REGEX;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getOR_OPERATOR() {
			return OR_OPERATOR;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getAND_OPERATOR() {
			return AND_OPERATOR;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getLEFT_PARANTHESIS() {
			return LEFT_PARANTHESIS;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getRIGHT_PARANTHESIS() {
			return RIGHT_PARANTHESIS;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getEMPTY() {
			return EMPTY;
		}

		/**
		 * Returns the corresponding legacy parser symbol.
		 * @return the corresponding legacy parser symbol
		 */
		public String getNULL() {
			return NULL;
		}

		/**
		 * Delegates to the static operator mapping.
		 * @param input operator token
		 * @return mapped operation, or null
		 */
		public SearchOperation getSimpleOperation(String input) {
			return SearchOperation.getSimpleOperation(input);
		}

	}

}
