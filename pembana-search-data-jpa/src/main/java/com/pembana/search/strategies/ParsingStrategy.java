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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pembana.search.SearchOperation;
import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.CaseSensitivity;

/**
 * Converts scalar or list values and constructs typed Criteria predicates.
 *
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author HamidReza Ireh
 * @author Diego Corredor
 * @author fernandorubio (upstream Git author)
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public interface ParsingStrategy {

	/**
	 * Converts a scalar using the strategy's inherited format.
	 * @param value raw value, possibly null; the NULL token remains a distinct string
	 * @param fieldClass entity attribute type
	 * @return converted value, or null for strategies that preserve null input
	 * @throws Exception if conversion fails, including null input for strategies that
	 * require a scalar
	 */
	default @Nullable Object parse(@Nullable String value, Class<?> fieldClass) throws Exception {
		return value;
	}

	/**
	 * Converts every membership item through scalar conversion. A null item is converted
	 * through the literal string {@code "null"}, retaining the legacy behavior.
	 * @param value raw membership values, or null
	 * @param fieldClass entity attribute type
	 * @return converted values, or null
	 * @throws Exception if an item cannot be converted
	 */
	default @Nullable Object parse(@Nullable List<? extends @Nullable Object> value, Class<?> fieldClass)
			throws Exception {
		if (value == null) {
			return null;
		}
		List<@Nullable Object> parsed = new ArrayList<>();
		for (var item : value) {
			parsed.add(parse(String.valueOf(item), fieldClass));
		}
		return parsed;
	}

	/**
	 * Builds a Criteria predicate with legacy null/operator behavior.
	 * @param builder criteria builder
	 * @param path owning path
	 * @param fieldName local attribute name
	 * @param ops requested operation, or null to contribute no predicate
	 * @param value converted value, possibly null; membership requires a non-null list
	 * @return predicate, or null for an unsupported legacy operation
	 */
	default @Nullable Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName,
			@Nullable SearchOperation ops, @Nullable Object value) {
		if (ops == null) {
			return null;
		}
		return switch (ops) {
			case IN_ARRAY -> getInClause(builder, path, fieldName, Objects.requireNonNull(value));
			case NOT_IN_ARRAY -> builder.not(getInClause(builder, path, fieldName, Objects.requireNonNull(value)));
			case IS, IS_NOT -> {
				if (!SearchOperation.NULL.equals(value)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Unsupported operation " + ops + " " + value + " for field " + fieldName);
				}
				yield (ops == SearchOperation.IS) ? builder.isNull(path.get(fieldName))
						: builder.isNotNull(path.get(fieldName));
			}
			case EQUALS -> builder.equal(path.get(fieldName), value);
			case NOT_EQUALS -> builder.notEqual(path.get(fieldName), value);
			case STARTS_WITH -> builder.like(path.get(fieldName), value + "%");
			case ENDS_WITH -> builder.like(path.get(fieldName), "%" + value);
			case CONTAINS -> builder.like(path.<String>get(fieldName).as(String.class), "%" + value + "%");
			case DOESNT_START_WITH -> builder.notLike(path.get(fieldName), value + "%");
			case DOESNT_END_WITH -> builder.notLike(path.get(fieldName), "%" + value);
			case DOESNT_CONTAIN -> builder.notLike(path.<String>get(fieldName).as(String.class), "%" + value + "%");
			default -> null;
		};
	}

	/**
	 * Builds typed membership without changing empty-list semantics.
	 * @param builder criteria builder
	 * @param path owning path
	 * @param fieldName local attribute name
	 * @param value converted membership list
	 * @return membership predicate
	 */
	default CriteriaBuilder.In<Object> getInClause(CriteriaBuilder builder, Path<?> path, String fieldName,
			Object value) {
		CriteriaBuilder.In<Object> clause = builder.in(path.get(fieldName));
		for (var item : (List<? extends @Nullable Object>) value) {
			clause.value(item);
		}
		return clause;
	}

	/**
	 * Selects the inherited converter, falling back to String handling.
	 * @param fieldClass attribute type
	 * @param annotation matching settings
	 * @param collection whether the attribute is collection-valued
	 * @return selected strategy
	 */
	static ParsingStrategy getStrategy(Class<?> fieldClass, SearchSpec annotation, boolean collection) {
		if (collection) {
			return new CollectionStrategy();
		}
		if (fieldClass == Boolean.class || fieldClass == boolean.class) {
			return new BooleanStrategy();
		}
		if (fieldClass == Date.class) {
			return new DateStrategy();
		}
		if (fieldClass == Double.class || fieldClass == double.class) {
			return new DoubleStrategy();
		}
		if (fieldClass == Float.class || fieldClass == float.class) {
			return new FloatStrategy();
		}
		if (fieldClass == Long.class || fieldClass == long.class) {
			return new LongStrategy();
		}
		if (fieldClass == Integer.class || fieldClass == int.class) {
			return new IntStrategy();
		}
		if (Enum.class.isAssignableFrom(fieldClass)) {
			return new EnumStrategy();
		}
		if (fieldClass == Duration.class) {
			return new DurationStrategy();
		}
		if (fieldClass == LocalDate.class) {
			return new LocalDateStrategy();
		}
		if (fieldClass == LocalTime.class) {
			return new LocalTimeStrategy();
		}
		if (fieldClass == LocalDateTime.class) {
			return new LocalDateTimeStrategy();
		}
		if (fieldClass == Instant.class) {
			return new InstantStrategy();
		}
		if (fieldClass == UUID.class) {
			return new UUIDStrategy();
		}
		return new StringStrategy(annotation);
	}

	/**
	 * Selects a converter, applying explicit case choices only to String attributes.
	 * @param fieldClass attribute type
	 * @param annotation configured matching settings
	 * @param collection whether collection-valued
	 * @param caseSensitivity resolved request choice, or INHERIT for legacy behavior
	 * @return selected strategy
	 */
	static ParsingStrategy getStrategy(Class<?> fieldClass, SearchSpec annotation, boolean collection,
			CaseSensitivity caseSensitivity) {
		return (fieldClass == String.class && !collection) ? new StringStrategy(annotation, caseSensitivity)
				: getStrategy(fieldClass, annotation, collection);
	}

	/** Legacy companion-style access; ordinary Java callers can use the static method. */
	Companion Companion = new Companion();

	/**
	 * Retained companion-shaped strategy lookup.
	 *
	 * @author Gunnar Hillert
	 */
	final class Companion {

		private Companion() {
		}

		/**
		 * Selects a strategy through the legacy companion entry point.
		 * @param type attribute type
		 * @param annotation matching settings
		 * @param collection whether collection-valued
		 * @return selected strategy
		 */
		public ParsingStrategy getStrategy(Class<?> type, SearchSpec annotation, boolean collection) {
			return ParsingStrategy.getStrategy(type, annotation, collection);
		}

	}

}
