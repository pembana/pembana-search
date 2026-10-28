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
package com.sipios.springsearch;

import com.sipios.springsearch.anotation.SearchSpec;
import com.sipios.springsearch.grammar.QueryBaseVisitor;
import com.sipios.springsearch.grammar.QueryParser;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

public final class QueryVisitorImpl<T> extends QueryBaseVisitor<Specification<T>> {
    private final SearchSpec searchSpecAnnotation;
    private final Pattern valueRegExp = Pattern.compile("^(?<prefix>\\*?)(?<value>.+?)(?<suffix>\\*?)$");
    public QueryVisitorImpl(SearchSpec searchSpecAnnotation) { this.searchSpecAnnotation = searchSpecAnnotation; }
    @Override
    public Specification<T> visitOpQuery(QueryParser.OpQueryContext ctx) {
        Specification<T> left = visit(ctx.left);
        Specification<T> right = visit(ctx.right);
        return "OR".equals(ctx.logicalOp.getText()) ? left.or(right) : left.and(right);
    }
    @Override
    public Specification<T> visitPriorityQuery(QueryParser.PriorityQueryContext ctx) { return visit(ctx.query()); }
    @Override
    public Specification<T> visitAtomQuery(QueryParser.AtomQueryContext ctx) { return visit(ctx.criteria()); }
    @Override
    public Specification<T> visitInput(QueryParser.InputContext ctx) { return visit(ctx.query()); }
    @Override
    public Specification<T> visitIsCriteria(QueryParser.IsCriteriaContext ctx) {
        String key = ctx.key().getText();
        verifyBlackList(key);
        return toSpec(key, ctx.IS() != null ? SearchOperation.IS : SearchOperation.IS_NOT, ctx.is_value().getText());
    }
    @Override
    public Specification<T> visitEqArrayCriteria(QueryParser.EqArrayCriteriaContext ctx) {
        String key = ctx.key().getText();
        verifyBlackList(key);
        SearchOperation op = ctx.IN() != null ? SearchOperation.IN_ARRAY : SearchOperation.NOT_IN_ARRAY;
        List<String> values = ctx.array().value().stream()
                .map(it -> it.STRING() != null ? clearString(it.getText()) : it.getText()).toList();
        return new SpecificationImpl<>(new SearchCriteria(key, op, values), searchSpecAnnotation);
    }
    @Override
    public Specification<T> visitBetweenCriteria(QueryParser.BetweenCriteriaContext ctx) {
        String key = ctx.key().getText();
        verifyBlackList(key);
        String leftValue = ctx.left.STRING() != null ? clearString(ctx.left.getText()) : ctx.left.getText();
        String rightValue = ctx.right.STRING() != null ? clearString(ctx.right.getText()) : ctx.right.getText();
        Specification<T> left = toSpec(key, SearchOperation.GREATER_THAN_EQUALS, leftValue);
        Specification<T> right = toSpec(key, SearchOperation.LESS_THAN_EQUALS, rightValue);
        return ctx.BETWEEN() != null ? left.and(right) : Specification.not(left.and(right));
    }
    private SpecificationImpl<T> toSpec(String key, SearchOperation op, String value) {
        return new SpecificationImpl<>(new SearchCriteria(key, op, value), searchSpecAnnotation);
    }
    @Override
    public Specification<T> visitOpCriteria(QueryParser.OpCriteriaContext ctx) {
        String key = ctx.key().getText();
        String value = ctx.value().STRING() != null ? clearString(ctx.value().getText()) : ctx.value().getText();
        verifyBlackList(key);
        Matcher match = valueRegExp.matcher(value);
        SearchOperation op = SearchOperation.getSimpleOperation(ctx.op().getText());
        if (op == null) { throw new IllegalArgumentException("Invalid operation"); }
        if (!match.find()) {
            if (searchSpecAnnotation.strictValidation()) {
                throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, key,
                        "Empty scalar values are not supported for field " + key);
            }
            throw new NullPointerException();
        }
        return new SpecificationImpl<>(new SearchCriteria(key, op, match.group("prefix"),
                match.group("value"), match.group("suffix")), searchSpecAnnotation);
    }
    private void verifyBlackList(String key) {
        if (Arrays.asList(searchSpecAnnotation.blackListedFields()).contains(key)) {
            throw new InvalidSearchQueryException(QueryErrorCode.FIELD_NOT_ALLOWED, key, "Field " + key + " is blacklisted");
        }
        String[] allowed = searchSpecAnnotation.allowedFields();
        if (allowed.length > 0 && !Arrays.asList(allowed).contains(key)) {
            throw new InvalidSearchQueryException(QueryErrorCode.FIELD_NOT_ALLOWED, key,
                    "Field " + key + " is not in the allowed fields");
        }
    }
    private String clearString(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\\\"", "\"").replace("\\'", "'");
    }
}
