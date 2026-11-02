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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.configuration.SearchRequestBody;
import com.pembana.search.configuration.SearchSpecificationResolver;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises server-selected profiles through GET/form and JSON on both databases.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class HttpSearchProfileTest {

	@Autowired
	UsersRepository users;

	/** Creates case variants and a second field for endpoint isolation. */
	@BeforeEach
	void seed() {
		this.users.save(new Users().withUserFirstName("Ada").withUserLastName("One"));
		this.users.save(new Users().withUserFirstName("ADA").withUserLastName("Other"));
		this.users.save(new Users().withUserFirstName("Beta").withUserLastName("Two"));
	}

	/** Public names, aliases and defaults work in query parameters and JSON. */
	@Test
	void publicAliasesAndCaseOptions() throws Exception {
		mvc().perform(get("/profiles").param("q", "label:ada"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
		mvc().perform(post("/profiles").contentType("application/json").content("{\"search\":\"label:ada\"}"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
		mvc().perform(get("/profiles").param("q", "label:ADA").param("fieldCaseSensitivity[label]", "sensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA"));
		mvc()
			.perform(post("/profiles").contentType("application/json")
				.content("{\"search\":\"label:ADA\",\"fieldCaseSensitivity\":{\"label\":\"sensitive\"}}"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA"));
		mvc().perform(post("/profiles-form").contentType("application/x-www-form-urlencoded").param("q", "label:ada"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
	}

	/** Canonical fields and unpermitted operations cannot bypass the profile. */
	@Test
	void permissionsAndCanonicalRestrictions() throws Exception {
		for (String input : List.of("userFirstName:Ada", "missing:Ada", "name:*Ada*")) {
			mvc().perform(get("/profiles").param("q", input))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"));
			mvc().perform(post("/profiles").contentType("application/json").content("{\"search\":\"" + input + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"));
		}
		mvc().perform(get("/denied-profile").param("search", "label:Ada"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"));
		mvc().perform(get("/profiles").param("q", "label:"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SYNTAX_ERROR"));
		mvc().perform(get("/profiles").param("q", "label:" + "x".repeat(4096)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("QUERY_LIMIT_EXCEEDED"));
	}

	/** Profile selection belongs to each endpoint and never leaks to ordinary binding. */
	@Test
	void endpointIsolationAndEmptySearch() throws Exception {
		var requests = mvc();
		requests.perform(get("/other-profile").param("search", "name:One"))
			.andExpect(status().isOk())
			.andExpect(content().string("Ada"));
		requests.perform(get("/profiles").param("q", "name:ada").param("profile", "other"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
		requests.perform(get("/ordinary").param("search", "userFirstName:Ada"))
			.andExpect(status().isOk())
			.andExpect(content().string("Ada"));
		requests.perform(get("/profiles")).andExpect(status().isOk()).andExpect(content().string("ADA,Ada,Beta"));
		requests.perform(get("/profiles").param("q", ""))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada,Beta"));
		requests.perform(post("/profiles").contentType("application/json").content("{\"search\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada,Beta"));
		requests.perform(post("/profiles").contentType("application/json")
				.content("{\"search\":\"name:Ada\",\"profile\":\"other\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
	}

	/** Missing registrations and duplicate profile names are configuration failures. */
	@Test
	void invalidConfigurationDoesNotFallBack() {
		assertThatThrownBy(() -> mvc().perform(get("/missing-profile")))
			.hasRootCauseInstanceOf(IllegalStateException.class)
			.hasRootCauseMessage("Unknown search profile: missing");
		assertThatThrownBy(() -> new SearchSpecificationResolver(JsonMapper.builder().build(),
				List.of(profile("public", "userFirstName"), profile("public", "userLastName"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Duplicate search profile name: public");
	}

	/** Supplies immutable public policy independently of bean names. */
	static SearchProfile profile(String name, String target) {
		return new SearchProfile(name, Map.of("name", new SearchField(target, Set.of(ProfileOperation.EQUALS))),
				Map.of("label", "name"), List.of(), new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
	}

	/** Builds a request harness with both application-owned profiles. */
	private MockMvc mvc() {
		return MockMvcBuilders.standaloneSetup(new Endpoint(this.users))
			.setCustomArgumentResolvers(new SearchSpecificationResolver(JsonMapper.builder().build(),
					List.of(profile("public", "userFirstName"), profile("other", "userLastName"))))
			.setControllerAdvice(new Errors())
			.build();
	}

	/**
	 * Application endpoints choose their public field contract.
	 *
	 * @author Gunnar Hillert
	 */
	@RestController
	static class Endpoint {

		private final UsersRepository users;

		/** Retains the application repository. */
		Endpoint(UsersRepository users) {
			this.users = users;
		}

		/** Searches public names via query parameters or form fields. */
		@GetMapping("/profiles")
		String get(@SearchSpec(profile = "public", searchParam = "q",
				allowedFields = { "userFirstName", "userLastName" }) @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** Applies the same public profile to submitted form fields. */
		@PostMapping("/profiles-form")
		String form(@SearchSpec(profile = "public", searchParam = "q") @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** Searches the same profile from a strict JSON envelope. */
		@PostMapping("/profiles")
		String post(@SearchRequestBody @SearchSpec(profile = "public") @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** Uses a different profile for the same public field name. */
		@GetMapping("/other-profile")
		String other(@SearchSpec(profile = "other") @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** Retains ordinary canonical field handling without a selected profile. */
		@GetMapping("/ordinary")
		String ordinary(@SearchSpec @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** Canonical restrictions still apply after alias resolution. */
		@GetMapping("/denied-profile")
		String denied(@SearchSpec(profile = "public",
				blackListedFields = "userFirstName") @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** A misspelled configured name cannot produce an unrestricted search. */
		@GetMapping("/missing-profile")
		String missing(@SearchSpec(profile = "missing") @Nullable Specification<Users> search) {
			return execute(search);
		}

		/** Executes a specification without changing optional-search behavior. */
		private String execute(@Nullable Specification<Users> search) {
			Iterable<Users> rows = (search == null) ? this.users.findAll() : this.users.findAll(search);
			return java.util.stream.StreamSupport.stream(rows.spliterator(), false)
				.map((user) -> user.userFirstName)
				.sorted()
				.collect(java.util.stream.Collectors.joining(","));
		}

	}

	/**
	 * Application-owned ProblemDetail rendering.
	 *
	 * @author Gunnar Hillert
	 */
	@RestControllerAdvice
	static class Errors extends ResponseEntityExceptionHandler {

	}

}
