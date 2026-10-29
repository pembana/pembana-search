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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.strategies.ParsingStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Protects legacy null handling while making production nullability contracts explicit.
 *
 * @author Gunnar Hillert
 */
class NullabilityContractTest {

	private static final SearchSpec SETTINGS = SearchTestFixtures.searchSpec("search", true, new String[0]);

	private static final List<Class<?>> SCALAR_TYPES = List.of(Boolean.class, Date.class, Double.class, Float.class,
			Integer.class, UserType.class, Duration.class, LocalDate.class, LocalTime.class, LocalDateTime.class,
			Instant.class, UUID.class, String.class);

	/**
	 * Ensures the query's NULL token remains the same sentinel in every scalar strategy.
	 */
	@Test
	void preservesNullTokenAcrossScalarStrategies() throws Exception {
		for (Class<?> type : SCALAR_TYPES) {
			ParsingStrategy strategy = ParsingStrategy.getStrategy(type, SETTINGS, false);
			assertSame(SearchOperation.NULL, strategy.parse(SearchOperation.NULL, type), type.getName());
		}
	}

	/**
	 * Keeps null references distinct from the NULL token and retains converter failures.
	 */
	@Test
	void retainsStrategySpecificNullReferenceHandling() throws Exception {
		for (Class<?> type : SCALAR_TYPES) {
			ParsingStrategy strategy = ParsingStrategy.getStrategy(type, SETTINGS, false);
			if (List.of(Boolean.class, Double.class, Float.class, Integer.class, String.class).contains(type)) {
				assertNull(strategy.parse((String) null, type), type.getName());
			}
			else {
				assertThrows(Exception.class, () -> strategy.parse((String) null, type), type.getName());
			}
		}
	}

	/**
	 * Retains null-list handling, empty membership and conversion of null list elements.
	 */
	@Test
	void preservesMembershipNullConversion() throws Exception {
		ParsingStrategy strings = ParsingStrategy.getStrategy(String.class, SETTINGS, false);
		assertNull(strings.parse((List<?>) null, String.class));
		assertEquals(List.of(), strings.parse(List.of(), String.class));
		assertEquals(List.of("null", "NULL", "text"), strings.parse(Arrays.asList(null, "NULL", "text"), String.class));
		ParsingStrategy booleans = ParsingStrategy.getStrategy(Boolean.class, SETTINGS, false);
		assertEquals(List.of(false, true), booleans.parse(Arrays.asList(null, "true"), Boolean.class));
	}

	/** Keeps unsupported operations absent and permits a null CriteriaQuery callback. */
	@Test
	void preservesAbsentPredicateAndNullableQuery() {
		CriteriaBuilder builder = mock(CriteriaBuilder.class);
		Path<?> path = mock(Path.class);
		Root<Object> root = mock();
		ParsingStrategy numbers = ParsingStrategy.getStrategy(Integer.class, SETTINGS, false);
		assertNull(SearchOperation.getSimpleOperation("~"));
		assertNull(SearchOperation.Companion.getSimpleOperation("~"));
		assertNull(numbers.buildPredicate(builder, path, "number", null, null));
		assertNull(numbers.buildPredicate(builder, path, "number", SearchOperation.BETWEEN, 1));
		assertNull(new NullSpecification<>().toPredicate(root, null, builder));
	}

	/**
	 * Ensures membership with no list continues to fail rather than becoming a filter.
	 */
	@Test
	void rejectsNullMembershipLists() {
		CriteriaBuilder builder = mock(CriteriaBuilder.class);
		Path<?> path = mock(Path.class);
		ParsingStrategy numbers = ParsingStrategy.getStrategy(Integer.class, SETTINGS, false);
		assertThrows(NullPointerException.class,
				() -> numbers.buildPredicate(builder, path, "number", SearchOperation.IN_ARRAY, null));
		assertThrows(NullPointerException.class,
				() -> numbers.buildPredicate(builder, path, "number", SearchOperation.NOT_IN_ARRAY, null));
	}

}
