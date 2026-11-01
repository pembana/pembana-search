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

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.AssociationQuery;
import com.pembana.search.core.BooleanOperator;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.Junction;
import com.pembana.search.core.Query;
import com.pembana.search.core.QueryParseException;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;

/**
 * Per-query compiler for explicit same-child scopes and provenance-preserving ranges.
 *
 * @param <T> root entity type
 * @author Gunnar Hillert
 */
public final class AssociationQueryTranslator<T> {

	private final SearchSpec settings;

	private final SearchOptions options;

	private final QueryExtensions extensions;

	private final @Nullable SearchProfile profile;

	/**
	 * Creates a compiler without retaining any mutable JPA execution state.
	 * @param settings consumer validation and matching settings
	 * @param options request case choices
	 * @param extensions registry with collections enabled
	 * @param profile per-query profile, or null for canonical inputs
	 */
	public AssociationQueryTranslator(SearchSpec settings, SearchOptions options, QueryExtensions extensions,
			@Nullable SearchProfile profile) {
		if (!extensions.collections) {
			throw new IllegalArgumentException("AssociationQuery requires collections(true)");
		}
		this.settings = settings;
		this.options = options;
		this.extensions = extensions;
		this.profile = profile;
	}

	/**
	 * Validates the complete user tree and creates fresh correlated scopes per execution.
	 * @param input association query
	 * @return matching root specification
	 */
	public Specification<T> translate(AssociationQuery input) {
		try {
			AssociationQuery canonical = canonical(input);
			SearchOptions request = (this.profile == null) ? this.options : this.profile.canonicalOptions(this.options);
			// Validate all source options and canonical field restrictions before
			// callbacks.
			new JpaQueryTranslator<T>(this.settings, request).translate(canonical.query());
			return (root, query, cb) -> render(canonical, request, root, query, cb, null,
					new ExtendedQueryTranslator.Budget());
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

	private AssociationQuery canonical(AssociationQuery input) {
		if (input instanceof AssociationQuery.Filter filter) {
			return AssociationQuery.where(resolve(filter.query()));
		}
		if (input instanceof AssociationQuery.Range range) {
			Junction resolved = (Junction) resolve(range.query());
			Comparison lower = (Comparison) resolved.left();
			Comparison upper = (Comparison) resolved.right();
			return AssociationQuery.between(lower.field(), lower.value(), upper.value());
		}
		if (input instanceof AssociationQuery.Group group) {
			return new AssociationQuery.Group(canonical(group.left()), group.operator(), canonical(group.right()));
		}
		if (input instanceof AssociationQuery.Not not) {
			return canonical(not.condition()).not();
		}
		AssociationQuery.Scope scope = (AssociationQuery.Scope) input;
		return AssociationQuery.any(scope.collection(), canonical(scope.condition()));
	}

	private Query resolve(Query input) {
		return (this.profile == null) ? input
				: this.profile.resolve(input, JpaQueryTranslator.parserOptions(this.settings));
	}

	private @Nullable Predicate render(AssociationQuery input, SearchOptions request, Root<T> root,
			@Nullable CommonAbstractCriteria query, CriteriaBuilder cb, @Nullable CollectionScope<T> scope,
			ExtendedQueryTranslator.Budget budget) {
		if (input instanceof AssociationQuery.Group group) {
			@Nullable Predicate left = render(group.left(), request, root, query, cb, scope, budget);
			@Nullable Predicate right = render(group.right(), request, root, query, cb, scope, budget);
			if (left == null) {
				return right;
			}
			if (right == null) {
				return left;
			}
			return (group.operator() == BooleanOperator.AND) ? cb.and(left, right) : cb.or(left, right);
		}
		if (input instanceof AssociationQuery.Not not) {
			Predicate predicate = render(not.condition(), request, root, query, cb, scope, budget);
			return (predicate == null) ? null : cb.not(predicate);
		}
		if (input instanceof AssociationQuery.Scope explicit) {
			if (scope != null) {
				throw CollectionPaths.unsupported(explicit.collection(), "Nested collection scopes are not supported");
			}
			CollectionScope<T> child = create(root, query, explicit.collection());
			Predicate condition = require(
					render(explicit.condition(), request, child.root, child.query, cb, child, budget),
					explicit.collection());
			child.query.select(cb.literal(1)).where(condition);
			return cb.exists(child.query);
		}
		if (input instanceof AssociationQuery.Range range && scope == null) {
			if (this.extensions.transformations.containsKey(range.field())) {
				throw CollectionPaths.unsupported(range.field(),
						"Transformed ranges require an explicit same-child scope");
			}
			CollectionPaths.Plan plan = CollectionPaths.plan(root, range.field(),
					this.extensions.factories.containsKey(range.field()));
			if (plan != null) {
				CollectionScope<T> child = create(root, query, plan.collection());
				Predicate condition = require(
						condition(range.query(), request, child.root, child.query, cb, child, budget), range.field());
				child.query.select(cb.literal(1)).where(condition);
				return cb.exists(child.query);
			}
		}
		return condition(input.query(), request, root, query, cb, scope, budget);
	}

	private @Nullable Predicate condition(Query query, SearchOptions request, Root<T> root,
			@Nullable CommonAbstractCriteria context, CriteriaBuilder cb, @Nullable CollectionScope<T> scope,
			ExtendedQueryTranslator.Budget budget) {
		var translator = new ExtendedQueryTranslator<T>(this.settings, request, this.extensions, this.profile, scope);
		var specification = translator.translateCanonical(query, request, budget);
		CriteriaQuery<?> criteria = (scope == null) ? ((context instanceof CriteriaQuery<?> current) ? current : null)
				: scope.outerQuery;
		if (criteria == null) {
			throw CollectionPaths.unsupported("", "Collection-mode execution requires a CriteriaQuery context");
		}
		return specification.toPredicate(root, criteria, cb);
	}

	private CollectionScope<T> create(Root<T> root, @Nullable CommonAbstractCriteria query, String collection) {
		CollectionPaths.Plan plan = CollectionPaths.plan(root, collection, false);
		if (plan == null || !plan.collection().equals(collection)) {
			throw CollectionPaths.unsupported(collection, "A same-child scope must name a collection");
		}
		if (!(query instanceof CriteriaQuery<?> criteria)) {
			throw CollectionPaths.unsupported(collection, "Collection matching requires a CriteriaQuery context");
		}
		return new CollectionScope<>(root, criteria, plan);
	}

	private static Predicate require(@Nullable Predicate predicate, String field) {
		if (predicate == null) {
			throw CollectionPaths.unsupported(field, "Child conditions must produce a predicate");
		}
		return predicate;
	}

}
