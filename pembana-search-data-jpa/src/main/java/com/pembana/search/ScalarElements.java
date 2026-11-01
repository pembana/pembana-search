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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.strategies.ParsingStrategy;

/**
 * Scalar element predicates for basic collections, using ordinary conversion and case
 * rules.
 *
 * @author Gunnar Hillert
 */
final class ScalarElements {

	private ScalarElements() {
	}

	static Predicate build(SearchCriteria criterion, SearchSpec settings, SearchOptions options, Path<?> expression,
			CriteriaBuilder builder) {
		Class<?> type = expression.getJavaType();
		if (settings.strictValidation()) {
			StrictQueryValidation.validate(type, false, criterion.getKey(), criterion.getOperation(),
					criterion.getValue());
		}
		if (options.hasFieldOverride(criterion.getKey()) && type != String.class) {
			throw new InvalidSearchQueryException(QueryErrorCode.INVALID_SEARCH_OPTIONS, criterion.getKey(),
					"Case choices require String elements");
		}
		SearchOperation operation = Objects.requireNonNull(criterion.getOperation());
		if (operation == SearchOperation.IS || operation == SearchOperation.IS_NOT) {
			if (!SearchOperation.NULL.equals(criterion.getValue())) {
				throw CollectionPaths.unsupported(criterion.getKey(),
						"Use collection emptiness outside a same-child scope");
			}
			return (operation == SearchOperation.IS) ? builder.isNull(expression) : builder.isNotNull(expression);
		}
		Object value;
		try {
			var strategy = ParsingStrategy.getStrategy(type, settings, false, options.resolve(criterion.getKey()));
			value = Objects.requireNonNull((criterion.getValue() instanceof List<?> list) ? strategy.parse(list, type)
					: strategy.parse(String.valueOf(criterion.getValue()), type));
		}
		catch (Exception exception) {
			throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, criterion.getKey(),
					"Could not convert collection element value");
		}
		return render(criterion, settings, options, expression, value, builder);
	}

	@SuppressWarnings("unchecked")
	static Predicate render(SearchCriteria criterion, SearchSpec settings, SearchOptions options, Path<?> expression,
			Object value, CriteriaBuilder builder) {
		SearchOperation operation = Objects.requireNonNull(criterion.getOperation());
		if (expression.getJavaType() != String.class) {
			return ExtensionPredicates.typed(builder, expression, ExtensionPredicates.operation(operation), value,
					criterion.getKey());
		}
		CaseSensitivity choice = options.resolve(criterion.getKey());
		boolean insensitive = choice == CaseSensitivity.INSENSITIVE
				|| (choice == CaseSensitivity.INHERIT && !settings.caseSensitiveFlag());
		Expression<String> original = (Expression<String>) expression;
		Expression<String> folded = insensitive ? builder.lower(original) : original;
		if (value instanceof List<?> list) {
			var clause = builder
				.in((Expression<Object>) (Expression<?>) ((choice == CaseSensitivity.INSENSITIVE) ? folded : original));
			for (Object item : list) {
				clause.value(
						(choice == CaseSensitivity.INSENSITIVE) ? String.valueOf(item).toLowerCase(Locale.ROOT) : item);
			}
			return (operation == SearchOperation.IN_ARRAY) ? clause : builder.not(clause);
		}
		String text = insensitive ? String.valueOf(value).toLowerCase(Locale.ROOT) : String.valueOf(value);
		Expression<String> less = (choice == CaseSensitivity.INHERIT) ? original : folded;
		return switch (operation) {
			case EQUALS -> builder.equal(folded, text);
			case NOT_EQUALS -> builder.notEqual(folded, text);
			case STARTS_WITH -> builder.like(folded, text + "%");
			case ENDS_WITH -> builder.like(folded, "%" + text);
			case CONTAINS -> builder.like(folded, "%" + text + "%");
			case DOESNT_START_WITH -> builder.notLike(folded, text + "%");
			case DOESNT_END_WITH -> builder.notLike(folded, "%" + text);
			case DOESNT_CONTAIN -> builder.notLike(folded, "%" + text + "%");
			case GREATER_THAN -> builder.greaterThan(folded, text);
			case GREATER_THAN_EQUALS -> builder.greaterThanOrEqualTo(folded, text);
			case LESS_THAN -> builder.lessThan(less, text);
			case LESS_THAN_EQUALS -> builder.lessThanOrEqualTo(less, text);
			default -> throw CollectionPaths.unsupported(criterion.getKey(), "Unsupported String element operation");
		};
	}

}
