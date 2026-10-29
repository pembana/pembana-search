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

import java.util.Arrays;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.strategies.StringStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Protects explicit strategy policy when copying and binding nullable membership items.
 *
 * @author Gunnar Hillert
 */
class CaseStrategyOptionsTest {

	/** A strategy copy must not silently discard explicit request case choices. */
	@Test
	void copyRetainsCaseChoiceAndLegacyValueSemantics() {
		var settings = SearchTestFixtures.searchSpec("search", true, new String[0]);
		for (CaseSensitivity choice : CaseSensitivity.values()) {
			var strategy = new StringStrategy(settings, choice);
			var copy = strategy.copy(settings);
			assertEquals(strategy, copy);
			assertEquals(strategy.hashCode(), copy.hashCode());
			assertEquals(strategy, strategy);
			assertFalse(strategy.equals(null));
			assertFalse(strategy.equals(new Object()));
			assertSame(settings, copy.component1());
			copy.setSearchSpecAnnotation(settings);
			assertSame(settings, copy.getSearchSpecAnnotation());
			assertTrue(strategy.toString().contains("StringStrategy(searchSpecAnnotation="));
		}
		assertFalse(new StringStrategy(settings, CaseSensitivity.SENSITIVE)
			.equals(new StringStrategy(settings, CaseSensitivity.INSENSITIVE)));
		assertEquals(settings.hashCode(), new StringStrategy(settings).hashCode());
	}

	/**
	 * Explicit membership folding preserves Java null items rather than manufacturing
	 * text.
	 */
	@Test
	void membershipKeepsNullableItems() {
		CriteriaBuilder builder = mock(CriteriaBuilder.class);
		Path<?> path = mock(Path.class);
		Path<String> field = mock();
		Expression<String> folded = mock();
		CriteriaBuilder.In<Object> clause = mock();
		given(path.<String>get("name")).willReturn(field);
		given(builder.lower(field)).willReturn(folded);
		given(builder.<Object>in(folded)).willReturn(clause);
		var strategy = new StringStrategy(SearchTestFixtures.searchSpec("search", true, new String[0]),
				CaseSensitivity.INSENSITIVE);
		assertSame(clause, strategy.buildPredicate(builder, path, "name", SearchOperation.IN_ARRAY,
				Arrays.<@Nullable Object>asList(null, "ADA")));
		then(clause).should().value((Object) null);
		then(clause).should().value("ada");
	}

}
