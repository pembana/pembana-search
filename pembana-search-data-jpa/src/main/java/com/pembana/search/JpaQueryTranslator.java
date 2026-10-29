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

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.BooleanOperator;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.DiagnosticCode;
import com.pembana.search.core.Junction;
import com.pembana.search.core.Membership;
import com.pembana.search.core.Negation;
import com.pembana.search.core.ParserOptions;
import com.pembana.search.core.Query;
import com.pembana.search.core.QueryParseException;

/**
 * Translates immutable query trees through the characterized JPA conversion strategies.
 * Both parsed and programmatic trees enforce the same field-access policy.
 *
 * @param <T> entity type
 * @author Gunnar Hillert
 */
public final class JpaQueryTranslator<T> {

	private final SearchSpec settings;

	private final ParserOptions options;

	/**
	 * Retains matching and validation settings for translation and predicate execution.
	 * @param settings application-supplied policy
	 */
	public JpaQueryTranslator(SearchSpec settings) {
		this.settings = settings;
		this.options = parserOptions(settings);
	}

	/**
	 * Creates a specification without concatenating or reparsing expression strings.
	 * Values are converted against entity attributes at predicate construction time.
	 * @param query immutable query tree
	 * @return equivalent JPA specification
	 * @throws InvalidSearchQueryException if a field-access rule rejects a leaf
	 */
	public Specification<T> translate(Query query) {
		try {
			if (query instanceof Comparison comparison) {
				this.options.verifyField(comparison.field());
				return new SpecificationImpl<>(new SearchCriteria(comparison.field(),
						SearchOperation.valueOf(comparison.operator().name()), comparison.value()), this.settings);
			}
			if (query instanceof Membership membership) {
				this.options.verifyField(membership.field());
				return new SpecificationImpl<>(new SearchCriteria(membership.field(),
						membership.negated() ? SearchOperation.NOT_IN_ARRAY : SearchOperation.IN_ARRAY,
						membership.values()), this.settings);
			}
			if (query instanceof Junction junction) {
				Specification<T> left = translate(junction.left());
				Specification<T> right = translate(junction.right());
				return (junction.operator() == BooleanOperator.OR) ? left.or(right) : left.and(right);
			}
			return Specification.not(translate(((Negation) query).query()));
		}
		catch (QueryParseException exception) {
			throw diagnostic(exception);
		}
	}

	/**
	 * Copies parser policy without introducing a Spring dependency into core.
	 * @param settings legacy annotation policy
	 * @return immutable parser options
	 */
	static ParserOptions parserOptions(SearchSpec settings) {
		return new ParserOptions(settings.strictValidation(), List.of(settings.blackListedFields()),
				List.of(settings.allowedFields()), settings.maxQueryLength(), settings.maxNestingDepth());
	}

	/**
	 * Preserves the legacy HTTP diagnostic at the adapter boundary.
	 * @param exception transport-independent parser failure
	 * @return matching bad-request diagnostic
	 */
	static InvalidSearchQueryException diagnostic(QueryParseException exception) {
		var error = new InvalidSearchQueryException(QueryErrorCode.valueOf(exception.getErrorCode().name()),
				exception.getField(), exception.getReason());
		if (exception.getErrorCode() == DiagnosticCode.SYNTAX_ERROR) {
			error.getBody().setProperty("line", exception.getLine());
			error.getBody().setProperty("column", exception.getColumn());
		}
		return error;
	}

}
