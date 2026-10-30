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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.BooleanOperator;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.CriterionTransformer;
import com.pembana.search.core.ExpressionParser;
import com.pembana.search.core.InvalidSearchValueException;
import com.pembana.search.core.Junction;
import com.pembana.search.core.Membership;
import com.pembana.search.core.Negation;
import com.pembana.search.core.ParserOptions;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.Query;
import com.pembana.search.core.QueryParseException;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;

/**
 * Explicit extension entry point for STANDARD and immutable programmatic queries.
 *
 * @param <T> root entity type
 * @author Gunnar Hillert
 */
public final class ExtendedQueryTranslator<T> {

	private final SearchSpec settings;

	private final SearchOptions options;

	private final QueryExtensions extensions;

	private final @Nullable SearchProfile profile;

	private final ParserOptions policy;

	/**
	 * Creates an isolated translator with explicit registrations and an optional profile.
	 * @param settings existing matching and validation policy
	 * @param options request case choices
	 * @param extensions immutable registrations
	 * @param profile selected profile or null for canonical field names
	 */
	public ExtendedQueryTranslator(SearchSpec settings, SearchOptions options, QueryExtensions extensions,
			@Nullable SearchProfile profile) {
		this.settings = settings;
		this.options = options;
		this.extensions = extensions;
		this.profile = profile;
		this.policy = JpaQueryTranslator.parserOptions(settings);
	}

	/**
	 * Parses STANDARD syntax and uses the same translation path as programmatic queries.
	 * @param input expression
	 * @return matching specification
	 */
	public Specification<T> parse(String input) {
		try {
			if (this.extensions.isEmpty() && this.profile == null) {
				return new CriteriaParser<T>(this.settings, this.options).parse(input);
			}
			ParserOptions syntax = (this.profile == null) ? this.policy
					: new ParserOptions(this.policy.strictValidation(), List.of(), List.of(),
							this.policy.maxQueryLength(), this.policy.maxNestingDepth());
			return translate(new ExpressionParser(syntax).parse(input));
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

	/**
	 * Applies source permissions, transformations, target permissions, conversion and
	 * factories.
	 * @param query submitted immutable tree
	 * @return matching specification
	 */
	public Specification<T> translate(Query query) {
		if (this.extensions.isEmpty()) {
			var ordinary = new JpaQueryTranslator<T>(this.settings, this.options);
			return (this.profile == null) ? ordinary.translate(query) : ordinary.translate(query, this.profile);
		}
		try {
			Query canonical = (this.profile == null) ? query : this.profile.resolve(query, this.policy);
			SearchOptions request = (this.profile == null) ? this.options : this.profile.canonicalOptions(this.options);
			request.validate(canonical);
			return node(canonical, request, new Budget(), new HashSet<>(), 0);
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

	private Specification<T> node(Query query, SearchOptions request, Budget budget, Set<Query> expanding, int depth) {
		if (depth > 0
				&& (++budget.nodes > this.extensions.maxExpansionNodes || depth > this.extensions.maxExpansionDepth)) {
			throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, null,
					"Transformation expansion limit exceeded");
		}
		if (query instanceof Junction junction) {
			var left = node(junction.left(), request, budget, expanding, (depth == 0) ? 0 : depth + 1);
			var right = node(junction.right(), request, budget, expanding, (depth == 0) ? 0 : depth + 1);
			return (junction.operator() == BooleanOperator.AND) ? left.and(right) : left.or(right);
		}
		if (query instanceof Negation negation) {
			return Specification.not(node(negation.query(), request, budget, expanding, (depth == 0) ? 0 : depth + 1));
		}
		SearchCriteria criterion;
		if (query instanceof Comparison comparison) {
			criterion = new SearchCriteria(comparison.field(), SearchOperation.valueOf(comparison.operator().name()),
					comparison.value());
		}
		else {
			Membership membership = (Membership) query;
			criterion = new SearchCriteria(membership.field(),
					membership.negated() ? SearchOperation.NOT_IN_ARRAY : SearchOperation.IN_ARRAY,
					membership.values());
		}
		String field = criterion.getKey();
		ProfileOperation operation = ExtensionPredicates.operation(criterion.getOperation());
		this.policy.verifyField(field);
		if (this.profile != null) {
			this.profile.verifyTarget(field, operation, this.policy);
		}
		SearchOptions effective = (this.profile == null) ? request : this.profile.targetOptions(query, request);
		CriterionTransformer transformer = this.extensions.transformations.get(field);
		if (transformer != null) {
			if (!expanding.add(query)) {
				throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, field,
						"Cyclic criterion transformation");
			}
			try {
				var replacement = transformer.transform(query);
				if (replacement.isPresent()) {
					SearchOptions forwarded = request;
					if (effective.hasFieldOverride(field)) {
						var fields = new HashSet<String>();
						collect(replacement.get(), fields);
						var choices = new HashMap<>(request.fieldCaseSensitivity());
						for (String target : fields) {
							choices.putIfAbsent(target, effective.resolve(field));
						}
						forwarded = new SearchOptions(request.caseSensitivity(), choices);
					}
					return node(replacement.get(), forwarded, budget, expanding, depth + 1);
				}
			}
			catch (InvalidSearchValueException exception) {
				throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, field, exception.getMessage());
			}
			finally {
				expanding.remove(query);
			}
		}
		return (root, queryContext, builder) -> ExtensionPredicates.build(criterion, this.settings, effective,
				this.extensions, root, queryContext, builder);
	}

	private void collect(Query query, Set<String> fields) {
		var pending = new java.util.ArrayDeque<Query>();
		pending.push(query);
		int visited = 0;
		while (!pending.isEmpty()) {
			if (++visited > this.extensions.maxExpansionNodes) {
				throw new InvalidSearchQueryException(QueryErrorCode.QUERY_LIMIT_EXCEEDED, null,
						"Transformation expansion limit exceeded");
			}
			Query current = pending.pop();
			if (current instanceof Comparison comparison) {
				fields.add(comparison.field());
			}
			else if (current instanceof Membership membership) {
				fields.add(membership.field());
			}
			else if (current instanceof Junction junction) {
				pending.push(junction.left());
				pending.push(junction.right());
			}
			else {
				pending.push(((Negation) current).query());
			}
		}
	}

	private static final class Budget {

		private int nodes;

	}

}
