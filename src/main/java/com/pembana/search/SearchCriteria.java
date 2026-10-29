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
package com.pembana.search;

import org.jspecify.annotations.Nullable;

/**
 * Search criteria with the original outer-asterisk operation mapping.
 *
 * @author Thibault F
 * @author Michael Mollard
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public final class SearchCriteria {

	private String key;

	private final @Nullable Object value;

	private @Nullable SearchOperation operation;

	/**
	 * Maps outer wildcard markers to the corresponding legacy string operation.
	 * @param key entity attribute path
	 * @param op requested operation, or null to contribute no predicate
	 * @param prefix optional leading wildcard marker
	 * @param value unconverted scalar or list, possibly null
	 * @param suffix optional trailing wildcard marker
	 */
	public SearchCriteria(String key, @Nullable SearchOperation op, @Nullable String prefix, @Nullable Object value,
			@Nullable String suffix) {
		this.key = key;
		this.value = value;
		boolean starts = prefix != null && prefix.contains(SearchOperation.ZERO_OR_MORE_REGEX);
		boolean ends = suffix != null && suffix.contains(SearchOperation.ZERO_OR_MORE_REGEX);
		if (op == SearchOperation.EQUALS) {
			op = (starts && ends) ? SearchOperation.CONTAINS
					: starts ? SearchOperation.ENDS_WITH : ends ? SearchOperation.STARTS_WITH : op;
		}
		if (op == SearchOperation.NOT_EQUALS) {
			op = (starts && ends) ? SearchOperation.DOESNT_CONTAIN
					: starts ? SearchOperation.DOESNT_END_WITH : ends ? SearchOperation.DOESNT_START_WITH : op;
		}
		this.operation = op;
	}

	/**
	 * Creates a criterion without wildcard markers.
	 * @param key entity attribute path
	 * @param op requested operation, or null to contribute no predicate
	 * @param value unconverted scalar or list, possibly null
	 */
	public SearchCriteria(String key, @Nullable SearchOperation op, @Nullable Object value) {
		this(key, op, null, value, null);
	}

	/**
	 * Returns the criterion's key.
	 * @return the criterion's key
	 */
	public String getKey() {
		return this.key;
	}

	/**
	 * Supplies replacement entity attribute path.
	 * @param key replacement entity attribute path
	 */
	public void setKey(String key) {
		this.key = key;
	}

	/**
	 * Returns the criterion's value.
	 * @return the criterion's value, possibly null
	 */
	public @Nullable Object getValue() {
		return this.value;
	}

	/**
	 * Returns the criterion's operation.
	 * @return the criterion's operation, or null if unset
	 */
	public @Nullable SearchOperation getOperation() {
		return this.operation;
	}

	/**
	 * Supplies replacement legacy operation.
	 * @param operation replacement legacy operation, or null to contribute no predicate
	 */
	public void setOperation(@Nullable SearchOperation operation) {
		this.operation = operation;
	}

}
