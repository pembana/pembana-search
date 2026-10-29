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

import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.strategies.ParsingStrategy;

/**
 * Original JPA specification, using Java reflection instead of Kotlin reflection.
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

	/**
	 * Creates a legacy criterion specification; parsed field restrictions belong to the
	 * visitor.
	 * @param criteria criterion to evaluate
	 * @param searchSpecAnnotation matching and validation settings
	 */
	public SpecificationImpl(SearchCriteria criteria, SearchSpec searchSpecAnnotation) {
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
		try {
			for (int i = 0; i < nestedKey.length - 1; i++) {
				nestedRoot = nestedRoot.get(nestedKey[i]);
			}
			fieldClass = nestedRoot.get(criteriaKey).getJavaType();
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			if (!this.searchSpecAnnotation.strictValidation()) {
				throw exception;
			}
			throw new InvalidSearchQueryException(QueryErrorCode.UNKNOWN_FIELD, this.criteria.getKey(),
					"Unknown field " + this.criteria.getKey());
		}
		boolean isCollectionField = isCollectionType(nestedRoot.getJavaType(), criteriaKey);
		if (this.searchSpecAnnotation.strictValidation()) {
			StrictQueryValidation.validate(fieldClass, isCollectionField, this.criteria.getKey(),
					this.criteria.getOperation(), this.criteria.getValue());
		}
		ParsingStrategy strategy = ParsingStrategy.getStrategy(fieldClass, this.searchSpecAnnotation,
				isCollectionField);
		@Nullable Object value = parseValue(strategy, fieldClass, criteriaKey, this.criteria.getValue());
		return strategy.buildPredicate(builder, nestedRoot, criteriaKey, this.criteria.getOperation(), value);
	}

	private boolean isCollectionType(Class<?> clazz, String fieldName) {
		try {
			Class<?> type = clazz.getDeclaredField(fieldName).getType();
			return Collection.class.isAssignableFrom(type) || type.isArray();
		}
		catch (NoSuchFieldException ex) {
			return false;
		}
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
