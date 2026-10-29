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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import com.pembana.search.SearchOperation;

/**
 * Converts Int values using the inherited matching policy.
 *
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public final class IntStrategy implements ParsingStrategy {

	/** Creates a IntStrategy with the inherited default configuration. */
	public IntStrategy() {
	}

	/** {@inheritDoc} */
	@Override
	public Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName, SearchOperation ops,
			Object value) {
		if (ops == null) {
			return ParsingStrategy.super.buildPredicate(builder, path, fieldName, null, value);
		}
		return switch (ops) {
			case GREATER_THAN -> builder.greaterThan(path.get(fieldName), (Integer) value);
			case LESS_THAN -> builder.lessThan(path.get(fieldName), (Integer) value);
			case GREATER_THAN_EQUALS -> builder.greaterThanOrEqualTo(path.get(fieldName), (Integer) value);
			case LESS_THAN_EQUALS -> builder.lessThanOrEqualTo(path.get(fieldName), (Integer) value);
			default -> ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
		};
	}

	/** {@inheritDoc} */
	@Override
	public Object parse(String value, Class<?> fieldClass) throws Exception {
		if (SearchOperation.NULL.equals(value)) {
			return value;
		}
		return (value == null) ? null : Integer.valueOf(value);
	}

}
