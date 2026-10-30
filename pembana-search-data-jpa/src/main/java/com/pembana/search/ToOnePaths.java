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

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Resolves query-local paths using left joins for traversed to-one associations. It
 * creates its own joins and never reuses consumer joins or retains Criteria state.
 *
 * @author Gunnar Hillert
 */
public final class ToOnePaths {

	private ToOnePaths() {
	}

	/**
	 * Resolves a canonical path for a custom factory using the optional-to-one policy.
	 * The caller owns field permissions. Invoke this for the current query only.
	 * @param root current query root
	 * @param field canonical entity path
	 * @return path whose traversed to-one links use LEFT joins
	 * @throws InvalidSearchQueryException for unknown fields or unsupported navigation
	 */
	public static Path<?> resolve(Root<?> root, String field) {
		String[] parts = field.split("\\.", -1);
		return owner(root, field).get(parts[parts.length - 1]);
	}

	static Path<?> owner(Root<?> root, String field) {
		String[] parts = field.split("\\.", -1);
		Path<?> current = root;
		ManagedType<?> type = root.getModel();
		for (int i = 0; i < parts.length; i++) {
			Attribute<?, ?> attribute;
			try {
				attribute = type.getAttribute(parts[i]);
			}
			catch (IllegalArgumentException exception) {
				throw new InvalidSearchQueryException(QueryErrorCode.UNKNOWN_FIELD, field, "Unknown field " + field);
			}
			if (i == parts.length - 1) {
				return current;
			}
			if (!(attribute instanceof SingularAttribute<?, ?> singular)
					|| !(singular.getType() instanceof ManagedType<?> next)) {
				throw unsupported(field, "Only to-one associations and embedded scalar paths may be traversed");
			}
			if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
				current = current.get(parts[i]);
			}
			else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE
					|| attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
				if (!(current instanceof From<?, ?> from)) {
					throw unsupported(field, "To-one navigation after an embedded attribute is not supported");
				}
				current = from.join(parts[i], JoinType.LEFT);
			}
			else {
				throw unsupported(field, "Unsupported association navigation");
			}
			type = next;
		}
		throw new IllegalStateException("A field path must have a terminal attribute");
	}

	private static InvalidSearchQueryException unsupported(String field, String reason) {
		return new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field, reason + ": " + field);
	}

}
