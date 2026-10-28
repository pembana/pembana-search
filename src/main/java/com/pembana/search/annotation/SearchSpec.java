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
package com.pembana.search.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a request search parameter to a JPA specification.
 * @author Gunnar Hillert
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchSpec {
    /**
     * Returns HTTP query parameter name consumed by the MVC resolver.
     * @return HTTP query parameter name consumed by the MVC resolver
     */
    String searchParam() default "search";
    /**
     * Returns whether legacy string patterns retain case; see the query guide for operator asymmetry.
     * @return whether legacy string patterns retain case; see the query guide for operator asymmetry
     */
    boolean caseSensitiveFlag() default true;
    /**
     * Returns exactly forbidden entity paths; these override allowed fields.
     * @return exactly forbidden entity paths; these override allowed fields
     */
    String[] blackListedFields() default {};
    /**
     * Returns whether to reject lexical errors, unknown fields and unsupported typed operations.
     * @return whether to reject lexical errors, unknown fields and unsupported typed operations
     */
    boolean strictValidation() default false;
    /**
     * Returns exact searchable paths; an empty list preserves unrestricted legacy access.
     * @return exact searchable paths; an empty list preserves unrestricted legacy access
     */
    String[] allowedFields() default {};
    /**
     * Returns maximum expression characters, or zero for the legacy unlimited behavior.
     * @return maximum expression characters, or zero for the legacy unlimited behavior
     */
    int maxQueryLength() default 0;
    /**
     * Returns maximum parenthesis depth, or zero for the legacy unlimited behavior.
     * @return maximum parenthesis depth, or zero for the legacy unlimited behavior
     */
    int maxNestingDepth() default 0;
}
