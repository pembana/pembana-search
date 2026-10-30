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
import java.util.List;
import java.util.Optional;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.InvalidSearchValueException;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.ValueConversion;
import com.pembana.search.strategies.ParsingStrategy;

/**
 * Query-local execution of registered factories and conversions, with ordinary fallback.
 *
 * @author Gunnar Hillert
 */
final class ExtensionPredicates {

	private ExtensionPredicates() {
	}

	static <T> @Nullable Predicate build(SearchCriteria criterion, SearchSpec settings, SearchOptions options,
			QueryExtensions extensions, Root<T> root, @Nullable CriteriaQuery<?> query, CriteriaBuilder builder) {
		var fallback = new SpecificationImpl<T>(criterion, settings, options, extensions.optionalToOne);
		String field = criterion.getKey();
		PredicateRegistration factory = extensions.factories.get(field);
		Class<?> type;
		if (factory != null) {
			type = factory.type();
		}
		else {
			try {
				type = path(root, field, extensions.optionalToOne).getJavaType();
			}
			catch (IllegalArgumentException | IllegalStateException exception) {
				return fallback.toPredicate(root, query, builder);
			}
		}
		ValueConversion specific = extensions.fields.get(field);
		ValueConversion general = extensions.types.get(type);
		if (specific == null && general == null && factory == null) {
			return fallback.toPredicate(root, query, builder);
		}
		ProfileOperation operation = operation(criterion.getOperation());

		if (factory != null) {
			permit(factory.operations(), operation, field);
		}

		if (specific != null && specific.type() != type) {
			throw new IllegalStateException("Field converter type does not match " + field);
		}

		try {
			Object raw = java.util.Objects.requireNonNull(criterion.getValue());
			boolean state = operation == ProfileOperation.IS || operation == ProfileOperation.IS_NOT;
			if (state && !SearchOperation.NULL.equals(raw) && !SearchOperation.EMPTY.equals(raw)) {
				throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field,
						"Expected NULL or EMPTY state for " + field);
			}
			List<?> values = (raw instanceof List<?> list) ? list : List.of(raw);
			List<Optional<Object>> converted = new ArrayList<>();
			boolean handled = raw instanceof List<?> && values.isEmpty() && (specific != null || general != null);
			if (handled) {
				ValueConversion selected = (specific != null) ? specific : java.util.Objects.requireNonNull(general);
				permit(selected.operations(), operation, field);
			}
			if (!state) {
				for (Object item : values) {
					Optional<Object> value = convert(String.valueOf(item), specific, general, type, operation, field);
					handled |= value.isPresent();
					converted.add(value);
				}
			}
			if (!handled && factory == null) {
				return fallback.toPredicate(root, query, builder);
			}
			if (options.hasFieldOverride(field) && type != String.class) {
				throw new InvalidSearchQueryException(QueryErrorCode.INVALID_SEARCH_OPTIONS, field,
						"Case sensitivity overrides require a String property: " + field);
			}
			@Nullable Object value;
			if (state) {
				value = SearchOperation.NULL.equals(raw) ? null : raw;
			}
			else {
				List<Object> parsed = new ArrayList<>();
				for (int i = 0; i < values.size(); i++) {
					Object item = converted.get(i).isPresent() ? converted.get(i).get()
							: builtin(String.valueOf(values.get(i)), type, settings, options, field);
					parsed.add(item);
				}
				value = (raw instanceof List<?>) ? List.copyOf(parsed) : parsed.get(0);
			}
			if (factory != null) {
				Optional<Predicate> predicate = factory.factory()
					.create(new PredicateContext(root, query, builder, field, operation, value, type,
							(type == String.class) ? options.resolve(field) : CaseSensitivity.INHERIT,
							settings.caseSensitiveFlag()));
				if (predicate.isPresent()) {
					return predicate.get();
				}
			}
			if (!handled) {
				return fallback.toPredicate(root, query, builder);
			}
			Path<?> attribute = path(root, field, extensions.optionalToOne);
			if (boxed(attribute.getJavaType()) != boxed(type)) {
				throw new IllegalStateException("Converted value type does not match fallback attribute " + field);
			}
			if (type == String.class) {
				String[] parts = field.split("\\.");
				Path<?> owner = root;
				if (extensions.optionalToOne) {
					owner = ToOnePaths.owner(root, field);
				}
				else {
					for (int i = 0; i < parts.length - 1; i++) {
						owner = owner.get(parts[i]);
					}
				}
				return ParsingStrategy.getStrategy(type, settings, false, options.resolve(field))
					.buildPredicate(builder, owner, parts[parts.length - 1], criterion.getOperation(), value);
			}
			return typed(builder, attribute, operation, value, field);
		}
		catch (InvalidSearchValueException exception) {
			throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, field, exception.getMessage());
		}
	}

	private static Optional<Object> convert(String raw, @Nullable ValueConversion specific,
			@Nullable ValueConversion general, Class<?> type, ProfileOperation operation, String field) {
		Optional<Object> result = Optional.empty();
		if (specific != null) {
			permit(specific.operations(), operation, field);
			result = specific.converter().convert(raw);
		}
		if (result.isEmpty() && general != null) {
			permit(general.operations(), operation, field);
			result = general.converter().convert(raw);
		}
		if (result.isPresent() && !boxed(type).isInstance(result.get())) {
			throw new IllegalStateException("Converter returned a value of the wrong Java type");
		}
		return result;
	}

	private static Object builtin(String raw, Class<?> type, SearchSpec settings, SearchOptions options, String field) {
		Object result;
		try {
			if (settings.strictValidation()) {
				StrictQueryValidation.validate(type, false, field, SearchOperation.EQUALS, raw);
			}
			result = java.util.Objects.requireNonNull(
					ParsingStrategy.getStrategy(type, settings, false, options.resolve(field)).parse(raw, type));
		}
		catch (InvalidSearchQueryException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, field,
					"Could not convert value for " + field);
		}
		if (!boxed(type).isInstance(result)) {
			throw new IllegalStateException("No converter produced the declared Java type for " + field);
		}
		return result;
	}

	private static Class<?> boxed(Class<?> type) {
		java.util.Map<Class<?>, Class<?>> boxes = java.util.Map.of(long.class, Long.class, int.class, Integer.class,
				boolean.class, Boolean.class, double.class, Double.class, float.class, Float.class, short.class,
				Short.class, byte.class, Byte.class, char.class, Character.class);
		return boxes.getOrDefault(type, type);
	}

	static ProfileOperation operation(@Nullable SearchOperation operation) {
		return switch (java.util.Objects.requireNonNull(operation)) {
			case IN_ARRAY -> ProfileOperation.IN;
			case NOT_IN_ARRAY -> ProfileOperation.NOT_IN;
			default -> ProfileOperation.valueOf(operation.name());
		};
	}

	private static void permit(java.util.Set<ProfileOperation> permissions, ProfileOperation operation, String field) {
		if (!permissions.contains(operation)) {
			throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field,
					"Registered extension does not permit " + operation + " for " + field);
		}
	}

	private static Path<?> path(Root<?> root, String field, boolean optionalToOne) {
		if (optionalToOne) {
			return ToOnePaths.resolve(root, field);
		}
		Path<?> current = root;
		for (String part : field.split("\\.")) {
			current = current.get(part);
		}
		return current;
	}

	@SuppressWarnings("unchecked")
	private static Predicate typed(CriteriaBuilder builder, Path<?> path, ProfileOperation operation,
			@Nullable Object value, String field) {
		if (operation == ProfileOperation.IN || operation == ProfileOperation.NOT_IN) {
			var clause = builder.in((Expression<Object>) path);
			for (Object item : (List<?>) java.util.Objects.requireNonNull(value)) {
				clause.value(item);
			}
			return (operation == ProfileOperation.IN) ? clause : builder.not(clause);
		}
		if (operation == ProfileOperation.EQUALS) {
			return builder.equal(path, value);
		}
		if (operation == ProfileOperation.NOT_EQUALS) {
			return builder.notEqual(path, value);
		}
		if (!(value instanceof Comparable<?>)) {
			throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field,
					"Value is not ordered for " + field);
		}
		Expression<Comparable<Object>> expression = (Expression<Comparable<Object>>) path;
		Comparable<Object> ordered = (Comparable<Object>) value;
		return switch (operation) {
			case GREATER_THAN -> builder.greaterThan(expression, ordered);
			case GREATER_THAN_EQUALS -> builder.greaterThanOrEqualTo(expression, ordered);
			case LESS_THAN -> builder.lessThan(expression, ordered);
			case LESS_THAN_EQUALS -> builder.lessThanOrEqualTo(expression, ordered);
			default -> throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field,
					"Unsupported typed operation for " + field);
		};
	}

}
