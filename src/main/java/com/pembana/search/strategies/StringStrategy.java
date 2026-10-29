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
package com.pembana.search.strategies;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;

import com.pembana.search.SearchOperation;
import com.pembana.search.annotation.SearchSpec;

/**
 * Converts String values using the inherited matching policy.
 *
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public final class StringStrategy implements ParsingStrategy {

	private SearchSpec searchSpecAnnotation;

	/**
	 * Supplies legacy case/matching policy.
	 * @param searchSpecAnnotation legacy case/matching policy
	 */
	public StringStrategy(SearchSpec searchSpecAnnotation) {
		this.searchSpecAnnotation = searchSpecAnnotation;
	}

	/**
	 * Returns current legacy matching settings.
	 * @return current legacy matching settings
	 */
	public SearchSpec getSearchSpecAnnotation() {
		return this.searchSpecAnnotation;
	}

	/**
	 * Supplies replacement legacy matching settings.
	 * @param value replacement legacy matching settings
	 */
	public void setSearchSpecAnnotation(SearchSpec value) {
		this.searchSpecAnnotation = value;
	}

	/**
	 * Returns matching settings through the retained data-class-style accessor.
	 * @return matching settings through the retained data-class-style accessor
	 */
	public SearchSpec component1() {
		return this.searchSpecAnnotation;
	}

	/**
	 * Copies this strategy with replacement settings.
	 * @param annotation replacement matching settings
	 * @return independent strategy instance
	 */
	public StringStrategy copy(SearchSpec annotation) {
		return new StringStrategy(annotation);
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(@Nullable Object other) {
		return this == other || other instanceof StringStrategy strategy
				&& Objects.equals(this.searchSpecAnnotation, strategy.searchSpecAnnotation);
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return this.searchSpecAnnotation.hashCode();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "StringStrategy(searchSpecAnnotation=" + this.searchSpecAnnotation + ")";
	}

	/** {@inheritDoc} */
	@Override
	public @Nullable Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName,
			@Nullable SearchOperation ops, @Nullable Object value) {
		if (!(value instanceof List<?>)) {
			String casedValue = String.valueOf(value);
			Expression<String> casedField = path.get(fieldName);
			if (!this.searchSpecAnnotation.caseSensitiveFlag()) {
				casedValue = casedValue.toLowerCase(Locale.ROOT);
				casedField = builder.lower(casedField);
			}
			if (ops == null) {
				return ParsingStrategy.super.buildPredicate(builder, path, fieldName, null, value);
			}
			return switch (ops) {
				case STARTS_WITH -> builder.like(casedField, casedValue + "%");
				case ENDS_WITH -> builder.like(casedField, "%" + casedValue);
				case CONTAINS -> builder.like(casedField, "%" + casedValue + "%");
				case DOESNT_START_WITH -> builder.notLike(casedField, casedValue + "%");
				case DOESNT_END_WITH -> builder.notLike(casedField, "%" + casedValue);
				case DOESNT_CONTAIN -> builder.notLike(casedField, "%" + casedValue + "%");
				case GREATER_THAN -> builder.greaterThan(casedField, casedValue);
				case GREATER_THAN_EQUALS -> builder.greaterThanOrEqualTo(casedField, casedValue);
				// Preserve the upstream asymmetry: equality and these comparisons use the
				// original path/value.
				case LESS_THAN -> builder.lessThan(path.get(fieldName), casedValue);
				case LESS_THAN_EQUALS -> builder.lessThanOrEqualTo(path.get(fieldName), casedValue);
				default -> ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
			};
		}
		return ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
	}

}
