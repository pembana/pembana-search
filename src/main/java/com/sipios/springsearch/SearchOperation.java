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

public enum SearchOperation {
    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, STARTS_WITH, ENDS_WITH, CONTAINS,
    DOESNT_START_WITH, DOESNT_END_WITH, DOESNT_CONTAIN, GREATER_THAN_EQUALS,
    LESS_THAN_EQUALS, IN_ARRAY, NOT_IN_ARRAY, IS, IS_NOT, BETWEEN, NOT_BETWEEN;

    public static final String[] SIMPLE_OPERATION_SET = {":", "!", ">", "<", "~", ">:", "<:"};
    public static final String ZERO_OR_MORE_REGEX = "*";
    public static final String OR_OPERATOR = "OR";
    public static final String AND_OPERATOR = "AND";
    public static final String LEFT_PARANTHESIS = "(";
    public static final String RIGHT_PARANTHESIS = ")";
    public static final String EMPTY = "EMPTY";
    public static final String NULL = "NULL";
    public static final Companion Companion = new Companion();

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

    /** Retains the original companion entry points without a Kotlin runtime. */
    public static final class Companion {
        private Companion() { }
        public String[] getSIMPLE_OPERATION_SET() { return SIMPLE_OPERATION_SET; }
        public String getZERO_OR_MORE_REGEX() { return ZERO_OR_MORE_REGEX; }
        public String getOR_OPERATOR() { return OR_OPERATOR; }
        public String getAND_OPERATOR() { return AND_OPERATOR; }
        public String getLEFT_PARANTHESIS() { return LEFT_PARANTHESIS; }
        public String getRIGHT_PARANTHESIS() { return RIGHT_PARANTHESIS; }
        public String getEMPTY() { return EMPTY; }
        public String getNULL() { return NULL; }
        public SearchOperation getSimpleOperation(String input) { return SearchOperation.getSimpleOperation(input); }
    }
}
