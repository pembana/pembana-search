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
import com.pembana.search.core.CaseSensitivity;

/**
 * Converts String values using the configured case policy for equality and patterns.
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

	private final CaseSensitivity caseSensitivity;

	/**
	 * Supplies legacy case/matching policy.
	 * @param searchSpecAnnotation legacy case/matching policy
	 */
	public StringStrategy(SearchSpec searchSpecAnnotation) {
		this(searchSpecAnnotation, CaseSensitivity.INHERIT);
	}

	/**
	 * Supplies an explicit case choice without mutating the annotation fallback.
	 * @param searchSpecAnnotation configured matching settings
	 * @param caseSensitivity request choice, or INHERIT for existing semantics
	 */
	public StringStrategy(SearchSpec searchSpecAnnotation, CaseSensitivity caseSensitivity) {
		this.caseSensitivity = caseSensitivity;
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
		return new StringStrategy(annotation, this.caseSensitivity);
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(@Nullable Object other) {
		return this == other || other instanceof StringStrategy strategy
				&& Objects.equals(this.searchSpecAnnotation, strategy.searchSpecAnnotation)
				&& this.caseSensitivity == strategy.caseSensitivity;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return (this.caseSensitivity == CaseSensitivity.INHERIT) ? this.searchSpecAnnotation.hashCode()
				: Objects.hash(this.searchSpecAnnotation, this.caseSensitivity);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "StringStrategy(searchSpecAnnotation=" + this.searchSpecAnnotation
				+ ((this.caseSensitivity == CaseSensitivity.INHERIT) ? "" : ", caseSensitivity=" + this.caseSensitivity)
				+ ")";
	}

	/** {@inheritDoc} */
	@Override
	public @Nullable Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName,
			@Nullable SearchOperation ops, @Nullable Object value) {
		if (!(value instanceof List<?>)) {
			String casedValue = String.valueOf(value);
			Expression<String> casedField = path.get(fieldName);
			@Nullable Object equalityValue = value;
			if (!caseSensitive()) {
				casedValue = casedValue.toLowerCase(Locale.ROOT);
				casedField = builder.lower(casedField);
				equalityValue = (value == null) ? null : casedValue;
			}
			if (ops == null) {
				return ParsingStrategy.super.buildPredicate(builder, path, fieldName, null, value);
			}
			return switch (ops) {
				case EQUALS -> builder.equal(casedField, equalityValue);
				case NOT_EQUALS -> builder.notEqual(casedField, equalityValue);
				case STARTS_WITH -> builder.like(casedField, casedValue + "%");
				case ENDS_WITH -> builder.like(casedField, "%" + casedValue);
				case CONTAINS -> builder.like(casedField, "%" + casedValue + "%");
				case DOESNT_START_WITH -> builder.notLike(casedField, casedValue + "%");
				case DOESNT_END_WITH -> builder.notLike(casedField, "%" + casedValue);
				case DOESNT_CONTAIN -> builder.notLike(casedField, "%" + casedValue + "%");
				case GREATER_THAN -> builder.greaterThan(casedField, casedValue);
				case GREATER_THAN_EQUALS -> builder.greaterThanOrEqualTo(casedField, casedValue);
				// Preserve the existing ordering behavior; it is separate from equality.
				case LESS_THAN -> builder.lessThan(
						(this.caseSensitivity == CaseSensitivity.INHERIT) ? path.get(fieldName) : casedField,
						casedValue);
				case LESS_THAN_EQUALS -> builder.lessThanOrEqualTo(
						(this.caseSensitivity == CaseSensitivity.INHERIT) ? path.get(fieldName) : casedField,
						casedValue);
				default -> ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
			};
		}
		if (this.caseSensitivity == CaseSensitivity.INSENSITIVE
				&& (ops == SearchOperation.IN_ARRAY || ops == SearchOperation.NOT_IN_ARRAY)) {
			CriteriaBuilder.In<Object> clause = builder.in(builder.lower(path.<String>get(fieldName)));
			for (var item : (List<?>) value) {
				clause.value((item == null) ? null : item.toString().toLowerCase(Locale.ROOT));
			}
			return (ops == SearchOperation.IN_ARRAY) ? clause : builder.not(clause);
		}

		return ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
	}

	/**
	 * Resolves the effective scalar case policy without changing configured defaults.
	 * @return whether scalar fields and values retain their case
	 */
	private boolean caseSensitive() {
		return (this.caseSensitivity == CaseSensitivity.INHERIT) ? this.searchSpecAnnotation.caseSensitiveFlag()
				: this.caseSensitivity == CaseSensitivity.SENSITIVE;
	}

}
