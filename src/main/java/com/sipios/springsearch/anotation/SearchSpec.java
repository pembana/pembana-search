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
package com.sipios.springsearch.anotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Maps a request search parameter to a JPA specification. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchSpec {
    String searchParam() default "search";
    boolean caseSensitiveFlag() default true;
    String[] blackListedFields() default {};
    /** @return whether to reject lexical errors, unknown fields and unsupported typed operations */
    boolean strictValidation() default false;
    /** @return exact searchable paths; an empty list preserves unrestricted legacy access */
    String[] allowedFields() default {};
    /** @return maximum expression characters, or zero for the legacy unlimited behavior */
    int maxQueryLength() default 0;
    /** @return maximum parenthesis depth, or zero for the legacy unlimited behavior */
    int maxNestingDepth() default 0;
}
