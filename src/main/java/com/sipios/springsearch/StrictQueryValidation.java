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
package com.sipios.springsearch;

import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Opt-in checks performed before type conversion or provider predicate construction.
 * @author Gunnar Hillert
 */
final class StrictQueryValidation {
    private static final Set<Class<?>> ORDERED_TYPES = Set.of(Integer.class, int.class,
            Double.class, double.class, Float.class, float.class, Date.class, Duration.class,
            LocalDate.class, LocalTime.class, LocalDateTime.class, Instant.class, String.class);

    /** Prevents construction of the stateless validator. */
    private StrictQueryValidation() { }

    /**
     * Rejects unsupported combinations without changing legacy conversion or matching.
     * @param type resolved entity attribute type
     * @param collection whether the attribute is collection-valued
     * @param field complete entity path for diagnostics
     * @param operation requested operation after wildcard/range expansion
     * @param value unconverted scalar or membership list
     */
    static void validate(Class<?> type, boolean collection, String field, SearchOperation operation, Object value) {
        if (collection) {
            if ((operation == SearchOperation.IS || operation == SearchOperation.IS_NOT)
                    && SearchOperation.EMPTY.equals(value)) { return; }
            throw unsupported(field, operation);
        }
        if (operation == SearchOperation.IS || operation == SearchOperation.IS_NOT) {
            if (SearchOperation.NULL.equals(value)) { return; }
            throw unsupported(field, operation);
        }
        boolean bool = type == Boolean.class || type == boolean.class;
        if (!ORDERED_TYPES.contains(type) && !bool && !type.isEnum() && type != UUID.class) {
            throw new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_FIELD_TYPE, field,
                    "Unsupported field type " + type.getSimpleName() + " for field " + field);
        }
        boolean supported = operation != null && switch (operation) {
            case EQUALS, NOT_EQUALS, IN_ARRAY, NOT_IN_ARRAY -> true;
            case GREATER_THAN, GREATER_THAN_EQUALS, LESS_THAN, LESS_THAN_EQUALS -> ORDERED_TYPES.contains(type);
            case STARTS_WITH, ENDS_WITH, CONTAINS, DOESNT_START_WITH, DOESNT_END_WITH, DOESNT_CONTAIN -> type == String.class;
            default -> false;
        };
        if (!supported) { throw unsupported(field, operation); }
        if (bool) {
            List<?> values = value instanceof List<?> list ? list : java.util.Collections.singletonList(value);
            for (Object item : values) {
                if (!"true".equalsIgnoreCase(String.valueOf(item)) && !"false".equalsIgnoreCase(String.valueOf(item))) {
                    throw new InvalidSearchQueryException(QueryErrorCode.INVALID_VALUE, field,
                            "Expected true or false for field " + field);
                }
            }
        }
    }

    /**
     * Describes an unsupported operator without exposing provider exception details.
     * @param field complete entity path
     * @param operation rejected operation
     * @return a bad-request diagnostic
     */
    private static InvalidSearchQueryException unsupported(String field, SearchOperation operation) {
        return new InvalidSearchQueryException(QueryErrorCode.UNSUPPORTED_OPERATION, field,
                "Unsupported operation " + operation + " for field " + field);
    }
}
