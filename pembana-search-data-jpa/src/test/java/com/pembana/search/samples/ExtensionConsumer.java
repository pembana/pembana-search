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
package com.pembana.search.samples;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.PredicateRegistration;
import com.pembana.search.QueryExtensions;
import com.pembana.search.SpecificationsBuilder;
import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.InvalidSearchValueException;
import com.pembana.search.core.Membership;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;
import com.pembana.search.core.ValueConversion;

/**
 * Shows how to combine converters, virtual fields and transformations using public
 * search APIs. The caller supplies an entity with a {@code Long} id and
 * {@code String} title and note attributes. For example, {@code code:CODE-12}
 * searches by stored ID, while {@code all:Ada} searches both text fields.
 * Copy or adapt this test-source example for an application.
 *
 * @author Gunnar Hillert
 */
public final class ExtensionConsumer {

	private ExtensionConsumer() {
	}

	/**
	 * Creates the extensions used by the example. The {@code displayId} converter
	 * reads {@code CODE-12} as the numeric value 12, and its factory compares that
	 * value with the entity ID. The {@code any} transformation searches title or note
	 * using the original operation.
	 * @return immutable registrations suitable for reuse across requests
	 */
	public static QueryExtensions registrations() {
		return QueryExtensions.builder()
			.forField("displayId", new ValueConversion(Long.class, Set.of(ProfileOperation.EQUALS), (raw) -> {
				if (!raw.startsWith("CODE-")) {
					// Plain numeric input can use the built-in Long converter.
					return Optional.empty();
				}
				try {
					return Optional.of(Long.valueOf(raw.substring(5)));
				}
				catch (NumberFormatException exception) {
					throw new InvalidSearchValueException("Expected a decimal display code");
				}
			}))
			.factory("displayId", new PredicateRegistration(Long.class, Set.of(ProfileOperation.EQUALS),
					(context) -> Optional.of(context.builder().equal(context.root().get("id"), context.value()))))
			.transform("any", (criterion) -> {
				if (criterion instanceof Comparison comparison) {
					return Optional.of(new Comparison("title", comparison.operator(), comparison.value())
						.or(new Comparison("note", comparison.operator(), comparison.value())));
				}
				Membership membership = (Membership) criterion;
				return Optional.of(new Membership("title", membership.negated(), membership.values())
					.or(new Membership("note", membership.negated(), membership.values())));
			})
			.build();
	}

	/**
	 * Builds a search with the example profile and extensions, then combines it with
	 * the application's required restriction. For example, {@code code:CODE-12} selects ID 12
	 * only when it also satisfies scope; {@code all:Ada} searches title or note within scope.
	 * @param <T> application entity type with id, title and note attributes
	 * @param settings existing validation and matching settings
	 * @param scope required restriction, such as the current tenant or published items
	 * @param input STANDARD expression using name, note, all or code
	 * @return specification requiring both the application scope and user search
	 */
	public static <T> Specification<T> scoped(SearchSpec settings, Specification<T> scope, String input) {
		Set<ProfileOperation> all = EnumSet.allOf(ProfileOperation.class);
		var profile = new SearchProfile("example",
				Map.of("name", new SearchField("title", all), "note", new SearchField("note", all), "all",
						new SearchField("any", all), "code",
						new SearchField("displayId", Set.of(ProfileOperation.EQUALS))),
				Map.of(), List.of(), SearchOptions.defaults());
		var user = new SpecificationsBuilder<T>(settings).withExtendedSearch(input, profile, registrations()).build();
		return scope.and(user);
	}

}
