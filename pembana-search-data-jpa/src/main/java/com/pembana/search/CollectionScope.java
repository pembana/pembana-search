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

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Execution-local correlated child scope; never stored in configuration or
 * specifications.
 *
 * @param <T> parent entity type
 * @author Gunnar Hillert
 */
final class CollectionScope<T> {

	final Root<T> root;

	final CriteriaQuery<?> outerQuery;

	final Subquery<Integer> query;

	final From<?, ?> element;

	final CollectionPaths.Plan plan;

	CollectionScope(Root<T> parent, CriteriaQuery<?> outer, CollectionPaths.Plan plan) {
		this.outerQuery = outer;
		this.plan = plan;
		this.query = outer.subquery(Integer.class);
		this.root = this.query.correlate(parent);
		Path<?> owner = ToOnePaths.owner(this.root, plan.collection());
		if (!(owner instanceof From<?, ?> from)) {
			throw CollectionPaths.unsupported(plan.collection(),
					"Collections inside embedded attributes are not supported");
		}
		String[] parts = plan.collection().split("\\.");
		var join = from.join(parts[parts.length - 1], JoinType.INNER);
		join.alias(CollectionPaths.MARKER + plan.collection().replace('.', '_'));
		this.element = join;
	}

	CollectionPaths.Resolved resolve(String field) {
		return CollectionPaths.child(this.element, this.plan.elementType(), this.plan.collection(), field);
	}

}
