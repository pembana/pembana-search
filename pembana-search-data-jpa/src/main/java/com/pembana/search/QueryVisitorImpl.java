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

import java.util.function.Supplier;

import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.core.Query;
import com.pembana.search.core.QueryModelVisitor;
import com.pembana.search.core.QueryParseException;
import com.pembana.search.grammar.QueryBaseVisitor;
import com.pembana.search.grammar.QueryParser;

/**
 * Translates the generated parse tree using the inherited advanced-expression semantics.
 *
 * @param <T> entity type represented by the generated specification
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
public final class QueryVisitorImpl<T> extends QueryBaseVisitor<Specification<T>> {

	private final QueryModelVisitor visitor;

	private final JpaQueryTranslator<T> translator;

	/**
	 * Creates a compatibility visitor backed by core and the shared JPA translator.
	 * @param searchSpecAnnotation matching, field-access and validation settings
	 */
	public QueryVisitorImpl(SearchSpec searchSpecAnnotation) {
		this.visitor = new QueryModelVisitor(JpaQueryTranslator.parserOptions(searchSpecAnnotation));
		this.translator = new JpaQueryTranslator<>(searchSpecAnnotation);
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitOpQuery(QueryParser.OpQueryContext ctx) {
		return translate(() -> this.visitor.visitOpQuery(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitPriorityQuery(QueryParser.PriorityQueryContext ctx) {
		return translate(() -> this.visitor.visitPriorityQuery(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitAtomQuery(QueryParser.AtomQueryContext ctx) {
		return translate(() -> this.visitor.visitAtomQuery(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitInput(QueryParser.InputContext ctx) {
		return translate(() -> this.visitor.visitInput(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitIsCriteria(QueryParser.IsCriteriaContext ctx) {
		return translate(() -> this.visitor.visitIsCriteria(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitEqArrayCriteria(QueryParser.EqArrayCriteriaContext ctx) {
		return translate(() -> this.visitor.visitEqArrayCriteria(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitBetweenCriteria(QueryParser.BetweenCriteriaContext ctx) {
		return translate(() -> this.visitor.visitBetweenCriteria(ctx));
	}

	/** {@inheritDoc} */
	@Override
	public Specification<T> visitOpCriteria(QueryParser.OpCriteriaContext ctx) {
		return translate(() -> this.visitor.visitOpCriteria(ctx));
	}

	/**
	 * Adapts transport-independent failures for legacy visitor callers.
	 * @param query core visitation operation
	 * @return translated specification
	 */
	private Specification<T> translate(Supplier<Query> query) {
		try {
			return this.translator.translate(query.get());
		}
		catch (QueryParseException exception) {
			throw JpaQueryTranslator.diagnostic(exception);
		}
	}

}
