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
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface ParsingStrategy {
    default Object parse(String value, Class<?> fieldClass) throws Exception { return value; }
    default Object parse(List<?> value, Class<?> fieldClass) throws Exception {
        if (value == null) { return null; }
        List<Object> parsed = new ArrayList<>();
        for (Object item : value) { parsed.add(parse(String.valueOf(item), fieldClass)); }
        return parsed;
    }
    default Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName,
            SearchOperation ops, Object value) {
        if (ops == null) { return null; }
        return switch (ops) {
            case IN_ARRAY -> getInClause(builder, path, fieldName, value);
            case NOT_IN_ARRAY -> builder.not(getInClause(builder, path, fieldName, value));
            case IS, IS_NOT -> {
                if (!SearchOperation.NULL.equals(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unsupported operation " + ops + " " + value + " for field " + fieldName);
                }
                yield ops == SearchOperation.IS ? builder.isNull(path.get(fieldName))
                        : builder.isNotNull(path.get(fieldName));
            }
            case EQUALS -> builder.equal(path.get(fieldName), value);
            case NOT_EQUALS -> builder.notEqual(path.get(fieldName), value);
            case STARTS_WITH -> builder.like(path.get(fieldName), value + "%");
            case ENDS_WITH -> builder.like(path.get(fieldName), "%" + value);
            case CONTAINS -> builder.like(path.<String>get(fieldName).as(String.class), "%" + value + "%");
            case DOESNT_START_WITH -> builder.notLike(path.get(fieldName), value + "%");
            case DOESNT_END_WITH -> builder.notLike(path.get(fieldName), "%" + value);
            case DOESNT_CONTAIN -> builder.notLike(path.<String>get(fieldName).as(String.class), "%" + value + "%");
            default -> null;
        };
    }
    default CriteriaBuilder.In<Object> getInClause(CriteriaBuilder builder, Path<?> path,
            String fieldName, Object value) {
        CriteriaBuilder.In<Object> clause = builder.in(path.get(fieldName));
        for (Object item : (List<?>) value) { clause.value(item); }
        return clause;
    }
    static ParsingStrategy getStrategy(Class<?> fieldClass, SearchSpec annotation, boolean collection) {
        if (collection) { return new CollectionStrategy(); }
        if (fieldClass == Boolean.class || fieldClass == boolean.class) { return new BooleanStrategy(); }
        if (fieldClass == Date.class) { return new DateStrategy(); }
        if (fieldClass == Double.class || fieldClass == double.class) { return new DoubleStrategy(); }
        if (fieldClass == Float.class || fieldClass == float.class) { return new FloatStrategy(); }
        if (fieldClass == Integer.class || fieldClass == int.class) { return new IntStrategy(); }
        if (Enum.class.isAssignableFrom(fieldClass)) { return new EnumStrategy(); }
        if (fieldClass == Duration.class) { return new DurationStrategy(); }
        if (fieldClass == LocalDate.class) { return new LocalDateStrategy(); }
        if (fieldClass == LocalTime.class) { return new LocalTimeStrategy(); }
        if (fieldClass == LocalDateTime.class) { return new LocalDateTimeStrategy(); }
        if (fieldClass == Instant.class) { return new InstantStrategy(); }
        if (fieldClass == UUID.class) { return new UUIDStrategy(); }
        return new StringStrategy(annotation);
    }
    Companion Companion = new Companion();
    final class Companion {
        private Companion() { }
        public ParsingStrategy getStrategy(Class<?> type, SearchSpec annotation, boolean collection) {
            return ParsingStrategy.getStrategy(type, annotation, collection);
        }
    }
}
