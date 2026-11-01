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

import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.SearchOptions;

/**
 * Correlated existence predicates keep child rows out of the outer query's result shape.
 *
 * @author Gunnar Hillert
 */
final class CollectionPredicates {

	private CollectionPredicates() {
	}

	static <T> Predicate leaf(SearchCriteria criterion, SearchSpec settings, SearchOptions options,
			QueryExtensions extensions, Root<T> root, @Nullable CriteriaQuery<?> query, CriteriaBuilder builder,
			CollectionPaths.Plan plan) {
		if (query == null) {
			throw CollectionPaths.unsupported(criterion.getKey(), "Collection matching requires a query context");
		}
		var scope = new CollectionScope<>(root, query, plan);
		SearchOperation operation = Objects.requireNonNull(criterion.getOperation());
		boolean empty = (operation == SearchOperation.IS || operation == SearchOperation.IS_NOT)
				&& SearchOperation.EMPTY.equals(criterion.getValue()) && criterion.getKey().equals(plan.collection());
		boolean absent = operation == SearchOperation.NOT_EQUALS || operation == SearchOperation.NOT_IN_ARRAY
				|| operation == SearchOperation.DOESNT_CONTAIN || operation == SearchOperation.DOESNT_START_WITH
				|| operation == SearchOperation.DOESNT_END_WITH || (empty && operation == SearchOperation.IS);
		Predicate condition;
		if (empty) {
			condition = builder.conjunction();
		}
		else {
			Predicate result = ExtensionPredicates.build(criterion, settings, options, extensions, scope.root, null,
					builder, scope);
			if (result == null) {
				throw CollectionPaths.unsupported(criterion.getKey(), "Collection conditions must produce a predicate");
			}
			condition = absent ? builder.not(result) : result;
		}
		scope.query.select(builder.literal(1)).where(condition);
		Predicate exists = builder.exists(scope.query);
		return absent ? builder.not(exists) : exists;
	}

}
