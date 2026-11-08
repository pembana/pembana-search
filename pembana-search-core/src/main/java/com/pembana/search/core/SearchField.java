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

import java.util.Set;

/**
 * Immutable canonical target and permitted operations for one public profile field.
 *
 * @param path canonical entity property path
 * @param operations explicitly permitted operations, copied defensively
 * @author Gunnar Hillert
 */
public record SearchField(String path, Set<ProfileOperation> operations) {
	/**
	 * Validates the canonical target and copies its permissions.
	 * @param path canonical entity property path
	 * @param operations allowed operations; empty denies all operations
	 */
	public SearchField {
		if (!path.matches("[A-Za-z0-9]++(?:\\.[A-Za-z0-9]++)*+")) {
			throw new IllegalArgumentException("Invalid canonical field path: " + path);
		}
		operations = Set.copyOf(operations);
	}
}
