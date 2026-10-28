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

import com.sipios.springsearch.anotation.SearchSpec;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

/** Also runnable through main on a classpath with every Kotlin dependency removed. */
public class JavaCallerSmokeTest {
    @Entity(name = "JavaSearchSample")
    @Table(name = "java_search_sample")
    public static class Sample {
        @Id
        private Long id;
        private String name;
        public Sample() { }
        Sample(long id, String name) { this.id = id; this.name = name; }
    }

    private static void searchEndpoint(@SearchSpec(caseSensitiveFlag = false) Specification<Sample> spec) { }

    @Test
    void javaCallerConstructsAndExecutesSpecification() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        SearchSpec settings = JavaCallerSmokeTest.class
                .getDeclaredMethod("searchEndpoint", Specification.class)
                .getParameters()[0].getAnnotation(SearchSpec.class);
        if (!"search".equals(settings.searchParam()) || settings.caseSensitiveFlag()) {
            throw new AssertionError("Java annotation configuration was not preserved");
        }
        // Match the selected corpus database while keeping bootstrap independent of Spring.
        boolean postgres = "postgresql".equals(System.getProperty("spring.profiles.active"));
        Configuration configuration = new Configuration().addAnnotatedClass(Sample.class)
                .setProperty("hibernate.connection.driver_class", postgres ? "org.postgresql.Driver" : "org.h2.Driver")
                .setProperty("hibernate.connection.url", postgres
                        ? environment("POSTGRES_URL", "jdbc:postgresql://localhost:5432/search_tests")
                        : "jdbc:h2:mem:java-caller;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", postgres ? environment("POSTGRES_USER", "search_tests") : "sa")
                .setProperty("hibernate.connection.password", postgres ? environment("POSTGRES_PASSWORD", "search_tests") : "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        try (SessionFactory factory = configuration.buildSessionFactory(); Session session = factory.openSession()) {
            session.beginTransaction();
            session.persist(new Sample(1L, "Sabal"));
            session.persist(new Sample(2L, "Other"));
            session.flush();
            SpecificationsBuilder<Sample> builder = new SpecificationsBuilder<>(settings);
            CriteriaQuery<Sample> all = session.getCriteriaBuilder().createQuery(Sample.class);
            if (builder.build().toPredicate(all.from(Sample.class), all, session.getCriteriaBuilder()) != null) {
                throw new AssertionError("An unconfigured builder should have no predicate");
            }
            CriteriaQuery<Sample> query = session.getCriteriaBuilder().createQuery(Sample.class);
            Root<Sample> root = query.from(Sample.class);
            Specification<Sample> spec = builder.withSearch("name:*ABA*").build();
            query.select(root).where(spec.toPredicate(root, query, session.getCriteriaBuilder()));
            var matches = session.createQuery(query).getResultList();
            if (matches.size() != 1 || !Long.valueOf(1L).equals(matches.get(0).id)) {
                throw new AssertionError("Java caller did not execute the configured case-insensitive pattern");
            }
            session.getTransaction().rollback();
        }
        System.out.println("Java caller smoke check passed");
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
