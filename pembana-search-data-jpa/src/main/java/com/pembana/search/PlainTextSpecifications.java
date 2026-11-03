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
import java.util.LinkedHashMap;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.PlainTextQuery;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.TextPattern;

/**
 * Translates composed text on String paths and collection elements before counting or
 * pagination.
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
			var scalars = new LinkedHashMap<String, Expression<String>>();
			var collections = new LinkedHashMap<String, CollectionPaths.Plan>();
			for (String field : text.fields()) {
				var plan = CollectionPaths.plan(root, field, false);
				if (plan == null) {
					scalars.put(field, string(ToOnePaths.resolve(root, field), field));
				}
				else {
					collections.put(field, plan);
				}
			}
			var words = new ArrayList<Predicate>();
			for (var pattern : text.patterns()) {
				var fields = new ArrayList<Predicate>();
				for (var scalar : scalars.entrySet()) {
					fields.add(match(scalar.getValue(), pattern, options, scalar.getKey(), builder));
				}
				for (var collection : collections.entrySet()) {
					String field = collection.getKey();
					if (query == null) {
						throw CollectionPaths.unsupported(field, "Collection matching requires a query context");
					}
					try {
						var scope = new CollectionScope<>(root, query, collection.getValue());
						var value = string(scope.resolve(field).expression(), field);
						scope.query.select(builder.literal(1)).where(match(value, pattern, options, field, builder));
						fields.add(builder.exists(scope.query));
					}
					catch (InvalidSearchQueryException exception) {
						throw new InvalidSearchQueryException(exception.getErrorCode(), field,
								"Cannot search " + field + ": " + exception.getReason());
					}
				}
				words.add(builder.or(fields.toArray(Predicate[]::new)));
			}
			return builder.and(words.toArray(Predicate[]::new));
		};
	}

	private static Expression<String> string(Path<?> path, String field) {
		if (path.getJavaType() != String.class) {
			throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_FIELD_TYPE, field,
					"Plain text requires a String property or element: " + field);
		}
		return path.as(String.class);
	}

	private static Predicate match(Expression<String> value, TextPattern pattern, SearchOptions options, String field,
			CriteriaBuilder builder) {
		boolean sensitive = options.resolve(field) == CaseSensitivity.SENSITIVE;
		return builder.like(sensitive ? value : builder.lower(value), pattern.sqlLike(sensitive), '!');
	}

}
