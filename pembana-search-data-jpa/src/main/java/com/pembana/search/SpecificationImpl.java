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

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.PluralAttribute;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.strategies.ParsingStrategy;

/**
 * JPA specification using resolved attribute metadata and typed conversion strategies.
 *
 * @param <T> entity type represented by the generated specification
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public final class SpecificationImpl<T> implements Specification<T> {

	/** Retained criterion configuration for this instance. */
	private final SearchCriteria criteria;

	/** Retained criterion configuration for this instance. */
	private final SearchSpec searchSpecAnnotation;

	/** Retained immutable case choices for this criterion. */
	private final SearchOptions searchOptions;

	/**
	 * Creates a legacy criterion specification; parsed field restrictions belong to the
	 * visitor.
	 * @param criteria criterion to evaluate
	 * @param searchSpecAnnotation matching and validation settings
	 */
	public SpecificationImpl(SearchCriteria criteria, SearchSpec searchSpecAnnotation) {
		this(criteria, searchSpecAnnotation, SearchOptions.defaults());
	}

	/**
	 * Creates a criterion with case choices applied after resolving the entity type.
	 * @param criteria criterion to evaluate
	 * @param searchSpecAnnotation configured matching and validation settings
	 * @param searchOptions request-wide and per-property choices
	 */
	SpecificationImpl(SearchCriteria criteria, SearchSpec searchSpecAnnotation, SearchOptions searchOptions) {
		this.searchOptions = searchOptions;
		this.criteria = criteria;
		this.searchSpecAnnotation = searchSpecAnnotation;
	}

	/** {@inheritDoc} */
	@Override
	public @Nullable Predicate toPredicate(Root<T> root, @Nullable CriteriaQuery<?> query, CriteriaBuilder builder) {
		String[] nestedKey = this.criteria.getKey().split("\\.", -1);
		Path<?> nestedRoot = root;
		String criteriaKey = nestedKey[nestedKey.length - 1];
		Class<?> fieldClass;
		boolean isCollectionField;
		try {
			for (int i = 0; i < nestedKey.length - 1; i++) {
				nestedRoot = nestedRoot.get(nestedKey[i]);
			}
			Path<?> attribute = nestedRoot.get(criteriaKey);
			fieldClass = attribute.getJavaType();
			isCollectionField = attribute.getModel() instanceof PluralAttribute<?, ?, ?> || fieldClass.isArray();
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			if (!this.searchSpecAnnotation.strictValidation() && this.searchOptions.isEmpty()) {
				throw exception;
			}
			throw new InvalidSearchQueryException(QueryErrorCode.UNKNOWN_FIELD, this.criteria.getKey(),
					"Unknown field " + this.criteria.getKey());
		}
		if (this.searchSpecAnnotation.strictValidation()) {
			StrictQueryValidation.validate(fieldClass, isCollectionField, this.criteria.getKey(),
					this.criteria.getOperation(), this.criteria.getValue());
		}
		if (this.searchOptions.hasFieldOverride(this.criteria.getKey()) && fieldClass != String.class) {
			throw new InvalidSearchQueryException(QueryErrorCode.INVALID_SEARCH_OPTIONS, this.criteria.getKey(),
					"Case sensitivity overrides require a String property: " + this.criteria.getKey());
		}
		ParsingStrategy strategy = ParsingStrategy.getStrategy(fieldClass, this.searchSpecAnnotation, isCollectionField,
				this.searchOptions.resolve(this.criteria.getKey()));
		@Nullable Object value = parseValue(strategy, fieldClass, criteriaKey, this.criteria.getValue());
		return strategy.buildPredicate(builder, nestedRoot, criteriaKey, this.criteria.getOperation(), value);
	}

	private @Nullable Object parseValue(ParsingStrategy strategy, Class<?> fieldClass, String criteriaKey,
			@Nullable Object value) {
		try {
			return (value instanceof List<?> list) ? strategy.parse(list, fieldClass)
					: strategy.parse((value == null) ? null : value.toString(), fieldClass);
		}
		catch (Exception ex) {
			// Kotlin simpleName uses Kotlin names for boxed primitives.
			String typeName = (fieldClass == Integer.class || fieldClass == int.class) ? "Int"
					: (fieldClass == boolean.class) ? "Boolean" : (fieldClass == double.class) ? "Double"
							: (fieldClass == float.class) ? "Float" : fieldClass.getSimpleName();
			throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, this.criteria.getKey(),
					"Could not parse input for the field " + criteriaKey + " as a " + typeName);
		}
	}

}
