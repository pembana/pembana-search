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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.pembana.search.annotation.SearchSpec;

/**
 * Test-only factories: preserve Kotlin setOf and annotation-instance semantics without
 * Kotlin.
 *
 * @author Gunnar Hillert
 */
final class SearchTestFixtures {

	private SearchTestFixtures() {
	}

	@SafeVarargs
	static <T extends @Nullable Object> Set<T> setOf(T... values) {
		// Unlike Set.of, Kotlin's setOf permits nulls and repeated values.
		return new HashSet<>(Arrays.asList(values));
	}

	static SearchSpec searchSpec(String searchParam, boolean caseSensitiveFlag, String[] blackListedFields) {
		String[] fields = blackListedFields.clone();
		return new SearchSpec() {
			@Override
			public String searchParam() {
				return searchParam;
			}

			@Override
			public boolean caseSensitiveFlag() {
				return caseSensitiveFlag;
			}

			@Override
			public String[] blackListedFields() {
				return fields.clone();
			}

			/** {@inheritDoc} */
			@Override
			public boolean strictValidation() {
				return false;
			}

			/** {@inheritDoc} */
			@Override
			public String[] allowedFields() {
				return new String[0];
			}

			/** {@inheritDoc} */
			@Override
			public int maxQueryLength() {
				return 0;
			}

			/** {@inheritDoc} */
			@Override
			public int maxNestingDepth() {
				return 0;
			}

			@Override
			public Class<? extends Annotation> annotationType() {
				return SearchSpec.class;
			}

			@Override
			public boolean equals(@Nullable Object other) {
				return other instanceof SearchSpec spec && searchParam.equals(spec.searchParam())
						&& caseSensitiveFlag == spec.caseSensitiveFlag()
						&& Arrays.equals(fields, spec.blackListedFields()) && !spec.strictValidation()
						&& spec.allowedFields().length == 0 && spec.maxQueryLength() == 0
						&& spec.maxNestingDepth() == 0;
			}

			@Override
			public int hashCode() {
				return ((127 * "searchParam".hashCode()) ^ searchParam.hashCode())
						+ ((127 * "caseSensitiveFlag".hashCode()) ^ Boolean.hashCode(caseSensitiveFlag))
						+ ((127 * "blackListedFields".hashCode()) ^ Arrays.hashCode(fields))
						+ ((127 * "strictValidation".hashCode()) ^ Boolean.hashCode(false))
						+ ((127 * "allowedFields".hashCode()) ^ Arrays.hashCode(new String[0]))
						+ (127 * "maxQueryLength".hashCode()) + (127 * "maxNestingDepth".hashCode());
			}

			@Override
			public String toString() {
				return "@" + SearchSpec.class.getName() + "(searchParam=" + searchParam + ", caseSensitiveFlag="
						+ caseSensitiveFlag + ", blackListedFields=" + Arrays.toString(fields) + ")";
			}
		};
	}

}
