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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.BooleanOperator;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.ComparisonOperator;
import com.pembana.search.core.ExpressionParser;
import com.pembana.search.core.Junction;
import com.pembana.search.core.Membership;
import com.pembana.search.core.Negation;
import com.pembana.search.core.ParserOptions;
import com.pembana.search.core.Query;
import com.pembana.search.core.SearchConfiguration;
import com.pembana.search.core.SearchMode;
import com.pembana.search.core.SearchOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Standalone consumer compiled unchanged against the adopted e870eeb API in CI, then
 * executed against candidate JARs. Also exercises the candidate in the regular suite.
 *
 * @author Gunnar Hillert
 */
public class ApiCompatibilityTest {

	/** Verifies source-callable constructors, accessors and adopted matching defaults. */
	@Test
	void adoptedConsumerContract() {
		SearchSpec settings = settings();
		assertEquals("", settings.profile());
		assertEquals("search", settings.searchParam());
		assertEquals(SearchSpec.class, settings.annotationType());
		var parserOptions = new ParserOptions(settings.strictValidation(), List.of(settings.blackListedFields()),
				List.of(settings.allowedFields()), settings.maxQueryLength(), settings.maxNestingDepth());
		assertEquals(ParserOptions.defaults(), parserOptions);
		var options = new SearchOptions(CaseSensitivity.INHERIT, Map.of());
		assertEquals(SearchOptions.defaults(), options);
		var a = new Comparison("name", ComparisonOperator.EQUALS, "Ada");
		var b = new Membership("name", false, List.of("Beta", "Gamma"));
		var conjunction = new Junction(a, BooleanOperator.AND, b);
		var negation = new Negation(conjunction);
		assertEquals(a.and(b).not(), negation);
		assertEquals(conjunction, negation.query());
		assertEquals(a, conjunction.left());
		assertEquals(b, conjunction.right());
		assertEquals(BooleanOperator.AND, conjunction.operator());
		assertEquals("name", a.field());
		assertEquals(ComparisonOperator.EQUALS, a.operator());
		assertEquals("Ada", a.value());
		assertEquals(List.of("Beta", "Gamma"), b.values());
		assertEquals(false, b.negated());
		assertEquals("name", b.field());
		assertEquals(
				new Comparison("name", ComparisonOperator.EQUALS, "Ada")
					.or(new Comparison("name", ComparisonOperator.EQUALS, "Beta")
						.and(new Comparison("name", ComparisonOperator.EQUALS, "Gamma"))),
				new ExpressionParser(parserOptions).parse("name:Ada OR name:Beta AND name:Gamma"));
		assertEquals(Set.of(Comparison.class, Membership.class, Junction.class, Negation.class),
				Arrays.stream(Query.class.getPermittedSubclasses()).collect(Collectors.toSet()));
		for (ComparisonOperator operation : ComparisonOperator.values()) {
			assertNotNull(switch (operation) {
				case EQUALS, NOT_EQUALS -> "equality";
				case GREATER_THAN, LESS_THAN, GREATER_THAN_EQUALS, LESS_THAN_EQUALS -> "ordering";
				case STARTS_WITH, ENDS_WITH, CONTAINS, DOESNT_START_WITH, DOESNT_END_WITH, DOESNT_CONTAIN -> "pattern";
				case IS, IS_NOT -> "null";
			});
		}
		for (CaseSensitivity choice : CaseSensitivity.values()) {
			assertEquals(choice.wireValue(), switch (choice) {
				case INHERIT -> "inherit";
				case SENSITIVE -> "sensitive";
				case INSENSITIVE -> "insensitive";
			});
		}
		for (SearchMode mode : SearchMode.values()) {
			assertNotNull(switch (mode) {
				case INHERIT -> "inherit";
				case STANDARD -> "standard";
				case SIMPLE -> "simple";
			});
		}
		assertNotNull(new CriteriaParser<>(settings).parse("name:Ada"));
		assertNotNull(new CriteriaParser<>(settings, options).parse("name:Ada"));
		assertNotNull(new JpaQueryTranslator<>(settings).translate(a));
		assertNotNull(new JpaQueryTranslator<>(settings, options).translate(a));
		assertNotNull(new SpecificationsBuilder<>(settings).withSearch("name:Ada").build());
		assertNotNull(new SpecificationsBuilder<>(settings, options).withQuery(a).build());
		var configuration = new SearchConfiguration(SearchMode.STANDARD, List.of("name"), 1024);
		assertEquals(List.of("name"), configuration.textFields());
		assertEquals(SearchMode.STANDARD, configuration.defaultMode());
		assertEquals(1024, configuration.maxTextPredicates());
		assertNotNull(SpecificationsBuilder.configured(settings, options, configuration)
			.withInput("Ada", SearchMode.SIMPLE)
			.build());
	}

	/**
	 * Runs unchanged baseline bytecode against candidate library artifacts.
	 * @param args unused command arguments
	 */
	public static void main(String[] args) {
		new ApiCompatibilityTest().adoptedConsumerContract();
		System.out.println("Adopted API consumer passed");
	}

	private static SearchSpec settings() {
		return new SearchSpec() {
			@Override
			public String searchParam() {
				return "search";
			}

			@Override
			public String profile() {
				return "";
			}

			@Override
			public boolean caseSensitiveFlag() {
				return true;
			}

			@Override
			public String[] blackListedFields() {
				return new String[0];
			}

			@Override
			public boolean strictValidation() {
				return true;
			}

			@Override
			public String[] allowedFields() {
				return new String[0];
			}

			@Override
			public int maxQueryLength() {
				return 4096;
			}

			@Override
			public int maxNestingDepth() {
				return 16;
			}

			@Override
			public Class<? extends Annotation> annotationType() {
				return SearchSpec.class;
			}
		};
	}

}
