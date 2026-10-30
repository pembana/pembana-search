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
package com.pembana.search.core;

import java.util.Optional;

/**
 * Transforms a canonical comparison or membership leaf into a grouped immutable tree.
 *
 * @author Gunnar Hillert
 */
@FunctionalInterface
public interface CriterionTransformer {

	/**
	 * Expands a leaf without string concatenation or parser internals.
	 * @param criterion canonical Comparison or Membership
	 * @return replacement query, or empty to decline
	 * @throws InvalidSearchValueException if user input is invalid
	 */
	Optional<Query> transform(Query criterion);

}
