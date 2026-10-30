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

import java.util.ArrayList;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.PlainTextQuery;
import com.pembana.search.core.SearchOptions;

/**
 * Translates composed text on root String properties before counting or pagination.
 *
 * @author Gunnar Hillert
 */
public final class PlainTextSpecifications {

	private PlainTextSpecifications() {
	}

	/**
	 * Creates a query-local predicate without changing existing expression translation.
	 * @param <T> root entity type
	 * @param text composed query
	 * @param options existing global and property case options; inherit defaults to
	 * insensitive here
	 * @return matching specification
	 */
	public static <T> Specification<T> translate(PlainTextQuery text, SearchOptions options) {
		for (String field : options.fieldCaseSensitivity().keySet()) {
			if (!text.fields().contains(field)) {
				throw new InvalidSearchQueryException(QueryErrorCode.INVALID_SEARCH_OPTIONS, field,
						"Case option property is not present in plain text: " + field);
			}
		}
		return (root, query, builder) -> {
			if (text.patterns().isEmpty()) {
				return null;
			}
			for (String field : text.fields()) {
				if (field.contains(".")) {
					throw new IllegalArgumentException("Plain text supports root String properties only");
				}
				try {
					if (root.get(field).getJavaType() != String.class) {
						throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_FIELD_TYPE, field,
								"Plain text requires a String property: " + field);
					}
				}
				catch (IllegalArgumentException exception) {
					throw new InvalidSearchQueryException(QueryErrorCode.UNKNOWN_FIELD, field,
							"Unknown field " + field);
				}
			}
			var words = new ArrayList<Predicate>();
			for (var pattern : text.patterns()) {
				var fields = new ArrayList<Predicate>();
				for (String field : text.fields()) {
					boolean sensitive = options.resolve(field) == CaseSensitivity.SENSITIVE;
					Expression<String> value = root.get(field);
					fields.add(builder.like(sensitive ? value : builder.lower(value), pattern.sqlLike(sensitive), '!'));
				}
				words.add(builder.or(fields.toArray(Predicate[]::new)));
			}
			return builder.and(words.toArray(Predicate[]::new));
		};
	}

}
