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

import java.util.ArrayDeque;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.jspecify.annotations.Nullable;

/**
 * Resolves paths inside a collection factory's current child scope. It never creates an
 * outer-query collection join or stores query state for later executions.
 *
 * @author Gunnar Hillert
 */
public final class CollectionPaths {

	static final String MARKER = "pembana_collection_scope_";

	private CollectionPaths() {
	}

	/**
	 * Resolves a full canonical child path against the factory's actual current child.
	 * @param root root supplied in the current PredicateContext
	 * @param field full canonical path, such as children.owner.name
	 * @return child expression with optional to-one links left joined
	 * @throws InvalidSearchQueryException if no corresponding collection scope is active
	 */
	public static Path<?> resolve(Root<?> root, String field) {
		var pending = new ArrayDeque<From<?, ?>>();
		pending.add(root);
		while (!pending.isEmpty()) {
			for (Join<?, ?> join : pending.remove().getJoins()) {
				String alias = join.getAlias();
				if (alias != null && alias.startsWith(MARKER)) {
					String collection = alias.substring(MARKER.length()).replace('_', '.');
					if (field.equals(collection) || field.startsWith(collection + ".")) {
						PluralAttribute<?, ?, ?> attribute = (PluralAttribute<?, ?, ?>) join.getAttribute();
						return child(join, attribute.getElementType(), collection, field).expression();
					}
				}
				pending.add(join);
			}
		}
		throw unsupported(field, "No active child scope for this collection path");
	}

	static @Nullable Plan plan(Root<?> root, String field, boolean virtual) {
		String[] parts = field.split("\\.", -1);
		ManagedType<?> type = root.getModel();
		Plan result = null;
		for (int i = 0; i < parts.length; i++) {
			Attribute<?, ?> attribute;
			try {
				attribute = type.getAttribute(parts[i]);
			}
			catch (IllegalArgumentException exception) {
				if (virtual) {
					return result;
				}
				throw unknown(field);
			}
			Type<?> next;
			if (attribute instanceof PluralAttribute<?, ?, ?> plural) {
				if (result != null || plural.getCollectionType() == PluralAttribute.CollectionType.MAP) {
					throw unsupported(field, "Nested collections and map-valued paths are not supported");
				}
				String prefix = String.join(".", java.util.Arrays.copyOfRange(parts, 0, i + 1));
				result = new Plan(prefix, plural.getElementType());
				next = plural.getElementType();
			}
			else {
				next = ((SingularAttribute<?, ?>) attribute).getType();
			}
			if (i < parts.length - 1) {
				if (!(next instanceof ManagedType<?> managed)) {
					throw unsupported(field, "Only managed attributes may be traversed");
				}
				type = managed;
			}
		}
		return result;
	}

	static Resolved child(From<?, ?> element, Type<?> elementType, String collection, String field) {
		if (field.equals(collection)) {
			return new Resolved(element, null, null);
		}
		if (!field.startsWith(collection + ".")) {
			throw new InvalidSearchQueryException(QueryErrorCode.FIELD_NOT_ALLOWED, field,
					"A same-child condition must refer to " + collection);
		}
		String[] parts = field.substring(collection.length() + 1).split("\\.", -1);
		Path<?> current = element;
		if (!(elementType instanceof ManagedType<?> managed)) {
			throw unsupported(field, "Basic collection elements have no nested properties");
		}
		ManagedType<?> type = managed;
		for (int i = 0; i < parts.length; i++) {
			Attribute<?, ?> attribute;
			try {
				attribute = type.getAttribute(parts[i]);
			}
			catch (IllegalArgumentException exception) {
				throw unknown(field);
			}
			if (attribute instanceof PluralAttribute<?, ?, ?>) {
				throw unsupported(field, "Nested collections are not supported inside a child scope");
			}
			if (i == parts.length - 1) {
				return new Resolved(current.get(parts[i]), current, parts[i]);
			}
			SingularAttribute<?, ?> singular = (SingularAttribute<?, ?>) attribute;
			if (!(singular.getType() instanceof ManagedType<?> next)) {
				throw unsupported(field, "Only managed attributes may be traversed");
			}
			if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
				current = current.get(parts[i]);
			}
			else {
				if (!(current instanceof From<?, ?> from)) {
					throw unsupported(field, "Association navigation after an embedded attribute is not supported");
				}
				current = from.join(parts[i], JoinType.LEFT);
			}
			type = next;
		}
		throw new IllegalStateException("Missing child attribute");
	}

	static InvalidSearchQueryException unsupported(String field, String reason) {
		return new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field, reason + ": " + field);
	}

	private static InvalidSearchQueryException unknown(String field) {
		return new InvalidSearchQueryException(QueryErrorCode.UNKNOWN_FIELD, field, "Unknown field " + field);
	}

	record Plan(String collection, Type<?> elementType) {
	}

	record Resolved(Path<?> expression, @Nullable Path<?> owner, @Nullable String localField) {
	}

}
