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
import com.sipios.springsearch.strategies.ParsingStrategy;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Original JPA specification, using Java reflection instead of Kotlin reflection. */
public final class SpecificationImpl<T> implements Specification<T> {
    private final SearchCriteria criteria;
    private final SearchSpec searchSpecAnnotation;
    public SpecificationImpl(SearchCriteria criteria, SearchSpec searchSpecAnnotation) {
        this.criteria = criteria;
        this.searchSpecAnnotation = searchSpecAnnotation;
    }
    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        String[] nestedKey = criteria.getKey().split("\\.", -1);
        Path<?> nestedRoot = root;
        for (int i = 0; i < nestedKey.length - 1; i++) {
            nestedRoot = nestedRoot.get(nestedKey[i]);
        }
        String criteriaKey = nestedKey[nestedKey.length - 1];
        Class<?> fieldClass = nestedRoot.get(criteriaKey).getJavaType();
        boolean isCollectionField = isCollectionType(nestedRoot.getJavaType(), criteriaKey);
        ParsingStrategy strategy = ParsingStrategy.getStrategy(fieldClass, searchSpecAnnotation, isCollectionField);
        Object value = parseValue(strategy, fieldClass, criteriaKey, criteria.getValue());
        return strategy.buildPredicate(builder, nestedRoot, criteriaKey, criteria.getOperation(), value);
    }
    private boolean isCollectionType(Class<?> clazz, String fieldName) {
        try {
            Class<?> type = clazz.getDeclaredField(fieldName).getType();
            return Collection.class.isAssignableFrom(type) || type.isArray();
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
    private Object parseValue(ParsingStrategy strategy, Class<?> fieldClass, String criteriaKey, Object value) {
        try {
            return value instanceof List<?> list ? strategy.parse(list, fieldClass)
                    : strategy.parse(value == null ? null : value.toString(), fieldClass);
        } catch (Exception e) {
            // Kotlin simpleName uses Kotlin names for boxed primitives.
            String typeName = fieldClass == Integer.class || fieldClass == int.class ? "Int"
                    : fieldClass == boolean.class ? "Boolean"
                    : fieldClass == double.class ? "Double"
                    : fieldClass == float.class ? "Float" : fieldClass.getSimpleName();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not parse input for the field " + criteriaKey + " as a " + typeName);
        }
    }
}
