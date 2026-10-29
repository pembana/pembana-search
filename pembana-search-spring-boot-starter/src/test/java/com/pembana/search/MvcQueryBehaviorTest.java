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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.configuration.SearchSpecificationResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retains the Stage 4 HTTP scenario at the optional MVC integration boundary.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class MvcQueryBehaviorTest {

	@Autowired
	UsersRepository users;

	/**
	 * Creates deterministic tenant, nullable Boolean and text fixtures for each
	 * transaction.
	 */
	@BeforeEach
	void seed() {
		this.users
			.save(new Users().withUserFirstName("Ada").withUserLastName("One").withUserAddress("A").withActive(true));
		this.users
			.save(new Users().withUserFirstName("Beta").withUserLastName("Two").withUserAddress("A").withActive(false));
		this.users.save(
				new Users().withUserFirstName("Gamma").withUserLastName("Three").withUserAddress("B").withActive(true));
		this.users
			.save(new Users().withUserFirstName("Delta").withUserLastName(null).withUserAddress("B").withActive(null));
	}

	/**
	 * Exercises MVC binding, database execution and an application-owned ProblemDetail
	 * handler.
	 */
	@Test
	void httpBindingExecutesSearchAndRendersQueryErrors() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(new Endpoint(this.users))
			.setCustomArgumentResolvers(new SearchSpecificationResolver())
			.setControllerAdvice(new Errors())
			.build();
		mvc.perform(get("/query").param("filter", "userFirstName:Ada"))
			.andExpect(status().isOk())
			.andExpect(content().string("1"));
		mvc.perform(get("/query")).andExpect(status().isOk()).andExpect(content().string("4"));
		mvc.perform(get("/query").param("filter", "userLastName:One"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"))
			.andExpect(jsonPath("$.field").value("userLastName"));
		mvc.perform(get("/query").param("filter", "userFirstName:"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SYNTAX_ERROR"));
	}

	/**
	 * Consumer-style endpoint with opt-in validation and a custom parameter name.
	 *
	 * @author Gunnar Hillert
	 */
	@RestController
	static class Endpoint {

		private final UsersRepository repository;

		/**
		 * @param repository fixture database repository
		 */
		Endpoint(UsersRepository repository) {
			this.repository = repository;
		}

		/**
		 * Executes a resolved predicate without taking ownership of caller scopes.
		 * @param search resolved query, or null when the parameter is absent
		 * @return matching row count as text
		 */
		@GetMapping("/query")
		String search(@SearchSpec(searchParam = "filter", strictValidation = true,
				allowedFields = "userFirstName") @Nullable Specification<Users> search) {
			return String.valueOf((search == null) ? this.repository.count() : this.repository.count(search));
		}

	}

	/**
	 * Demonstrates consumer-controlled RFC 9457 rendering without global library advice.
	 *
	 * @author Gunnar Hillert
	 */
	@RestControllerAdvice
	static class Errors extends ResponseEntityExceptionHandler {

	}

}
