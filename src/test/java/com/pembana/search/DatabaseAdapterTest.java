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

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prevents a PostgreSQL matrix job from silently falling back to an embedded database.
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
class DatabaseAdapterTest {
    @Autowired
    DataSource dataSource;
    @Autowired
    Environment environment;

    @Test
    void connectsToTheSelectedDatabaseEngine() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals(environment.matchesProfiles("postgresql") ? "PostgreSQL" : "H2",
                    connection.getMetaData().getDatabaseProductName());
        }
    }
}
