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
package com.sipios.springsearch.strategies;

import com.sipios.springsearch.SearchOperation;
import com.sipios.springsearch.anotation.SearchSpec;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class StringStrategy implements ParsingStrategy {
    private SearchSpec searchSpecAnnotation;
    public StringStrategy(SearchSpec searchSpecAnnotation) { this.searchSpecAnnotation = searchSpecAnnotation; }
    public SearchSpec getSearchSpecAnnotation() { return searchSpecAnnotation; }
    public void setSearchSpecAnnotation(SearchSpec value) { searchSpecAnnotation = value; }
    public SearchSpec component1() { return searchSpecAnnotation; }
    public StringStrategy copy(SearchSpec annotation) { return new StringStrategy(annotation); }
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StringStrategy strategy
                && Objects.equals(searchSpecAnnotation, strategy.searchSpecAnnotation);
    }
    @Override
    public int hashCode() { return searchSpecAnnotation.hashCode(); }
    @Override
    public String toString() { return "StringStrategy(searchSpecAnnotation=" + searchSpecAnnotation + ")"; }
    @Override
    public Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName,
            SearchOperation ops, Object value) {
        if (!(value instanceof List<?>)) {
            String casedValue = String.valueOf(value);
            Expression<String> casedField = path.get(fieldName);
            if (!searchSpecAnnotation.caseSensitiveFlag()) {
                casedValue = casedValue.toLowerCase(Locale.ROOT);
                casedField = builder.lower(casedField);
            }
            if (ops == null) { return ParsingStrategy.super.buildPredicate(builder, path, fieldName, null, value); }
            return switch (ops) {
                case STARTS_WITH -> builder.like(casedField, casedValue + "%");
                case ENDS_WITH -> builder.like(casedField, "%" + casedValue);
                case CONTAINS -> builder.like(casedField, "%" + casedValue + "%");
                case DOESNT_START_WITH -> builder.notLike(casedField, casedValue + "%");
                case DOESNT_END_WITH -> builder.notLike(casedField, "%" + casedValue);
                case DOESNT_CONTAIN -> builder.notLike(casedField, "%" + casedValue + "%");
                case GREATER_THAN -> builder.greaterThan(casedField, casedValue);
                case GREATER_THAN_EQUALS -> builder.greaterThanOrEqualTo(casedField, casedValue);
                // Preserve the upstream asymmetry: equality and these comparisons use the original path/value.
                case LESS_THAN -> builder.lessThan(path.get(fieldName), casedValue);
                case LESS_THAN_EQUALS -> builder.lessThanOrEqualTo(path.get(fieldName), casedValue);
                default -> ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
            };
        }
        return ParsingStrategy.super.buildPredicate(builder, path, fieldName, ops, value);
    }
}
