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
package com.pembana.search.strategies;
import com.pembana.search.SearchOperation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Converts Collection values using the inherited matching policy.
 * @author Gunnar Hillert
 */
public final class CollectionStrategy implements ParsingStrategy {
    /** Creates a CollectionStrategy with the inherited default configuration. */
    public CollectionStrategy() { }

    /** {@inheritDoc} */
    @Override
    public Predicate buildPredicate(CriteriaBuilder builder, Path<?> path, String fieldName,
            SearchOperation ops, Object value) {
        if (ops == SearchOperation.IS && SearchOperation.EMPTY.equals(value)) {
            return builder.isEmpty(path.get(fieldName));
        }
        if (ops == SearchOperation.IS_NOT && SearchOperation.EMPTY.equals(value)) {
            return builder.isNotEmpty(path.get(fieldName));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported operation " + ops + " " + value + " for collection field " + fieldName
                + ", only IS EMPTY and IS NOT EMPTY are supported");
    }
}
