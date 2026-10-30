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

import java.util.Optional;

import jakarta.persistence.criteria.Predicate;

/**
 * Trusted application factory for an ordinary or virtual field predicate.
 *
 * @author Gunnar Hillert
 */
@FunctionalInterface
public interface PredicateFactory {

	/**
	 * Builds a predicate using the current query's context.
	 * @param context validated operation, converted values and matching choices
	 * @return predicate, or empty to decline to normal field translation
	 */
	Optional<Predicate> create(PredicateContext context);

}
