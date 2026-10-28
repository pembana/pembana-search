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

import com.sipios.springsearch.anotation.SearchSpec;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Test-only factories: preserve Kotlin setOf and annotation-instance semantics without Kotlin. */
final class SearchTestFixtures {
    private SearchTestFixtures() { }

    @SafeVarargs
    static <T> Set<T> setOf(T... values) {
        // Unlike Set.of, Kotlin's setOf permits nulls and repeated values.
        return new HashSet<>(Arrays.asList(values));
    }

    static SearchSpec searchSpec(String searchParam, boolean caseSensitiveFlag, String[] blackListedFields) {
        String[] fields = blackListedFields.clone();
        return new SearchSpec() {
            @Override public String searchParam() { return searchParam; }
            @Override public boolean caseSensitiveFlag() { return caseSensitiveFlag; }
            @Override public String[] blackListedFields() { return fields.clone(); }
            @Override public Class<? extends Annotation> annotationType() { return SearchSpec.class; }
            @Override public boolean equals(Object other) {
                return other instanceof SearchSpec spec && searchParam.equals(spec.searchParam())
                        && caseSensitiveFlag == spec.caseSensitiveFlag()
                        && Arrays.equals(fields, spec.blackListedFields());
            }
            @Override public int hashCode() {
                return ((127 * "searchParam".hashCode()) ^ searchParam.hashCode())
                        + ((127 * "caseSensitiveFlag".hashCode()) ^ Boolean.hashCode(caseSensitiveFlag))
                        + ((127 * "blackListedFields".hashCode()) ^ Arrays.hashCode(fields));
            }
            @Override public String toString() {
                return "@" + SearchSpec.class.getName() + "(searchParam=" + searchParam
                        + ", caseSensitiveFlag=" + caseSensitiveFlag
                        + ", blackListedFields=" + Arrays.toString(fields) + ")";
            }
        };
    }
}
