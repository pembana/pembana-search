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

import com.pembana.search.SearchOperation;

/**
 * Converts Enum values using the inherited matching policy.
 *
 * @author Spring Search contributors
 * @author Gunnar Hillert
 */
public final class EnumStrategy implements ParsingStrategy {

	/** Creates a EnumStrategy with the inherited default configuration. */
	public EnumStrategy() {
	}

	/** {@inheritDoc} */
	@Override
	public Object parse(String value, Class<?> fieldClass) throws Exception {
		if (SearchOperation.NULL.equals(value)) {
			return value;
		}
		// Keep the upstream qualified-name lookup, including its nested-enum limitation.
		return Class.forName(fieldClass.getCanonicalName()).getMethod("valueOf", String.class).invoke(null, value);
	}

}
