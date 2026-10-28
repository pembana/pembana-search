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
import com.sipios.springsearch.configuration.ResolverConf;
import com.sipios.springsearch.configuration.SearchSpecificationResolver;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Directly checks the Java port of MVC glue that the upstream suite does not exercise. */
class JavaResolverTest {
    private void endpoint(@SearchSpec(searchParam = "filter", caseSensitiveFlag = false,
            blackListedFields = {"secret"}) Specification<?> query) { }
    private void unannotated(Specification<?> query) { }
    private void wrongType(@SearchSpec String query) { }

    @Test
    void bindsConfiguredParameterAndPreservesDiagnostics() throws Exception {
        SearchSpecificationResolver resolver = new SearchSpecificationResolver();
        MethodParameter parameter = parameter("endpoint", Specification.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletWebRequest webRequest = new ServletWebRequest(request);
        assertNull(resolver.resolveArgument(parameter, null, webRequest, null));
        request.setParameter("filter", "");
        assertNull(resolver.resolveArgument(parameter, null, webRequest, null));
        request.setParameter("filter", "name:*ABA*");
        assertNotNull(resolver.resolveArgument(parameter, null, webRequest, null));
        request.setParameter("filter", "secret:value");
        ResponseStatusException blacklist = assertThrows(ResponseStatusException.class,
                () -> resolver.resolveArgument(parameter, null, webRequest, null));
        assertEquals(400, blacklist.getStatusCode().value());
        assertEquals("Field secret is blacklisted", blacklist.getReason());
        request.setParameter("filter", "name:");
        ResponseStatusException syntax = assertThrows(ResponseStatusException.class,
                () -> resolver.resolveArgument(parameter, null, webRequest, null));
        assertEquals(400, syntax.getStatusCode().value());
        assertTrue(syntax.getReason().startsWith("Invalid search query: [line 1:"));
    }

    @Test
    void registersResolverAndSelectsOnlyAnnotatedSpecifications() throws Exception {
        var resolvers = new ArrayList<HandlerMethodArgumentResolver>();
        new ResolverConf().addArgumentResolvers(resolvers);
        assertEquals(1, resolvers.size());
        HandlerMethodArgumentResolver resolver = resolvers.get(0);
        assertTrue(resolver.supportsParameter(parameter("endpoint", Specification.class)));
        assertFalse(resolver.supportsParameter(parameter("unannotated", Specification.class)));
        assertFalse(resolver.supportsParameter(parameter("wrongType", String.class)));
    }

    private MethodParameter parameter(String name, Class<?> type) throws NoSuchMethodException {
        return new MethodParameter(JavaResolverTest.class.getDeclaredMethod(name, type), 0);
    }
}
