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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.pembana.search.annotation.SearchSpec;
import com.pembana.search.configuration.SearchRequestBody;
import com.pembana.search.configuration.SearchSpecificationResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET/JSON case choices, error binding and mandatory scopes on both databases.
 *
 * @author Gunnar Hillert
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = PembanaSearchTestApplication.class)
@Transactional
class CaseOptionsBindingTest {

	@Autowired
	UsersRepository users;

	/** Creates exact/case variants plus an otherwise matching row outside the scope. */
	@BeforeEach
	void seed() {
		this.users.save(new Users().withUserFirstName("Ada").withUserLastName("One").withUserAddress("A"));
		this.users.save(new Users().withUserFirstName("ADA").withUserLastName("oNe").withUserAddress("A"));
		this.users.save(new Users().withUserFirstName("Beta").withUserLastName(null).withUserAddress("A"));
		this.users.save(new Users().withUserFirstName("ADA").withUserLastName("One").withUserAddress("B"));
	}

	/**
	 * Missing options preserve the configured true default and absent-search behavior.
	 */
	@Test
	void getDefaultsAndGlobalChoice() throws Exception {
		var mvc = mvc();
		mvc.perform(get("/case-search")).andExpect(status().isOk()).andExpect(content().string("ADA,Ada,Beta"));
		mvc.perform(get("/case-search").param("search", "userFirstName:ada"))
			.andExpect(status().isOk())
			.andExpect(content().string(""));
		mvc.perform(get("/case-search").param("search", "userFirstName:ada").param("caseSensitivity", "insensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
	}

	/** GET property choices take precedence over globals for exact query paths. */
	@Test
	void getPropertyPrecedence() throws Exception {
		mvc()
			.perform(get("/case-search").param("search", "userFirstName:ada AND userLastName:One")
				.param("caseSensitivity", "insensitive")
				.param("fieldCaseSensitivity[userLastName]", "sensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("Ada"));
		mvc()
			.perform(get("/case-search").param("search", "userFirstName:ada")
				.param("caseSensitivity", "sensitive")
				.param("fieldCaseSensitivity[userFirstName]", "insensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
	}

	/**
	 * Inherit falls through to globals and configured false policies without flipping
	 * defaults.
	 */
	@Test
	void inheritedChoicesAndFalseAnnotation() throws Exception {
		mvc()
			.perform(get("/case-search").param("search", "userFirstName:ada")
				.param("caseSensitivity", "insensitive")
				.param("fieldCaseSensitivity[userFirstName]", "inherit"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
		mvc()
			.perform(get("/case-search-insensitive").param("search", "userFirstName:ada")
				.param("caseSensitivity", "inherit"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
		mvc()
			.perform(get("/case-search-insensitive").param("search", "userFirstName:ada")
				.param("caseSensitivity", "sensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string(""));
	}

	/**
	 * JSON has the same flat envelope and precedence as GET, independently of GET
	 * aliases.
	 */
	@Test
	void jsonPrecedenceAndConfiguredSearchAlias() throws Exception {
		mvc().perform(post("/case-search").contentType(MediaType.APPLICATION_JSON).content("""
				{"search":"userFirstName:ada AND userLastName:One", "caseSensitivity":"insensitive",
				 "fieldCaseSensitivity":{"userLastName":"sensitive"}}
				""")).andExpect(status().isOk()).andExpect(content().string("Ada"));
		mvc()
			.perform(get("/case-search-filter").param("filter", "userFirstName:ada")
				.param("caseSensitivity", "insensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
		mvc()
			.perform(post("/case-search").contentType("application/vnd.search+json")
				.content("{\"search\":\"userFirstName:ada\",\"caseSensitivity\":\"insensitive\"}"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
	}

	/**
	 * Empty/omitted JSON expressions retain the no-filter contract without accepting
	 * unused overrides.
	 */
	@Test
	void jsonAbsentSearchAndInheritance() throws Exception {
		for (String payload : List.of("{}", "{\"search\":\"\"}",
				"{\"caseSensitivity\":\"inherit\",\"fieldCaseSensitivity\":{}}")) {
			mvc().perform(post("/case-search").contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isOk())
				.andExpect(content().string("ADA,Ada,Beta"));
		}
		mvc()
			.perform(post("/case-search").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fieldCaseSensitivity\":{\"userFirstName\":\"sensitive\"}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
	}

	/**
	 * Property choices apply to repeated references without weakening the mandatory
	 * scope.
	 */
	@Test
	void groupingAndScopesArePreserved() throws Exception {
		mvc()
			.perform(
					get("/case-search").param("search", "userFirstName:ADA OR (userFirstName:ada AND userLastName:One)")
						.param("caseSensitivity", "insensitive")
						.param("fieldCaseSensitivity[userFirstName]", "sensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA"));
	}

	/**
	 * Explicit options also bind to membership and retain legacy behavior when omitted.
	 */
	@Test
	void membershipUsesRequestPolicy() throws Exception {
		mvc().perform(get("/case-search-insensitive").param("search", "userFirstName IN [ada]"))
			.andExpect(status().isOk())
			.andExpect(content().string(""));
		mvc()
			.perform(get("/case-search").param("search", "userFirstName IN [ada]")
				.param("fieldCaseSensitivity[userFirstName]", "insensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada"));
	}

	/** Invalid global/property enum values fail rather than silently inheriting. */
	@Test
	void rejectsInvalidChoices() throws Exception {
		for (String value : List.of("", "false", "true", "INSENSITIVE", "other")) {
			mvc().perform(get("/case-search").param("search", "userFirstName:Ada").param("caseSensitivity", value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
		}
		mvc()
			.perform(get("/case-search").param("search", "userFirstName:Ada")
				.param("fieldCaseSensitivity[userFirstName]", "other"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("userFirstName"));
	}

	/**
	 * Duplicate reserved parameters are ambiguous even when their values are identical.
	 */
	@Test
	void rejectsDuplicateGetParameters() throws Exception {
		for (String name : List.of("search", "caseSensitivity", "fieldCaseSensitivity[userFirstName]")) {
			mvc()
				.perform(get("/case-search").param("search", "userFirstName:Ada")
					.param(name, "sensitive", "insensitive"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
		}
	}

	/** Malformed brackets, empty keys and unused keys produce structured errors. */
	@Test
	void rejectsMalformedAndUnknownPropertyKeys() throws Exception {
		for (String key : List.of("fieldCaseSensitivity", "fieldCaseSensitivity.name", "fieldCaseSensitivity[]",
				"fieldCaseSensitivity[[name]]", "fieldCaseSensitivity[name]extra", "fieldCaseSensitivity[name")) {
			mvc().perform(get("/case-search").param("search", "userFirstName:Ada").param(key, "sensitive"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
		}
		mvc()
			.perform(get("/case-search").param("search", "userFirstName:Ada")
				.param("fieldCaseSensitivity[missing]", "sensitive"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("missing"));
	}

	/** A map in the request never grants access to a field excluded by the annotation. */
	@Test
	void fieldOptionsDoNotOverrideAllowlist() throws Exception {
		mvc()
			.perform(get("/case-search").param("search", "userId:1").param("fieldCaseSensitivity[userId]", "sensitive"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"));
	}

	/**
	 * Non-text property choices fail at type resolution while globals ignore non-text
	 * attributes.
	 */
	@Test
	void validatesPropertyTypes() throws Exception {
		mvc()
			.perform(get("/case-search").param("search", "userChildrenNumber:3")
				.param("fieldCaseSensitivity[userChildrenNumber]", "insensitive"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
		mvc()
			.perform(
					get("/case-search").param("search", "userChildrenNumber:3").param("caseSensitivity", "insensitive"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada,Beta"));
	}

	/** JSON never coerces booleans, nulls, arrays, numeric options or unknown members. */
	@Test
	void rejectsInvalidJsonShapes() throws Exception {
		for (String payload : List.of("[]", "null", "1", "\"text\"", "{\"search\":false}", "{\"search\":null}",
				"{\"caseSensitivity\":false}", "{\"caseSensitivity\":null}", "{\"fieldCaseSensitivity\":[]}",
				"{\"fieldCaseSensitivity\":null}", "{\"fieldCaseSensitivity\":{\"userFirstName\":null}}",
				"{\"fieldCaseSensitivity\":{\"\":\"sensitive\"}}", "{\"caseSensitivity\":\"other\"}",
				"{\"unknown\":\"value\"}")) {
			mvc().perform(post("/case-search").contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
		}
	}

	/**
	 * Malformed, trailing and duplicate JSON must fail even if a mapper is permissive.
	 */
	@Test
	void rejectsMalformedAndAmbiguousJson() throws Exception {
		var mapper = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.disable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.build();
		var mvc = mvc(new SearchSpecificationResolver(mapper));
		for (String payload : List.of("", "{", "{} {}",
				"{\"caseSensitivity\":\"sensitive\",\"caseSensitivity\":\"insensitive\"}",
				"{\"fieldCaseSensitivity\":{\"userFirstName\":\"sensitive\",\"userFirstName\":\"insensitive\"}}")) {
			mvc.perform(post("/case-search").contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
		}
	}

	/**
	 * Body and reserved query options are not silently merged with an undocumented
	 * priority.
	 */
	@Test
	void rejectsMixedJsonAndQueryOptions() throws Exception {
		mvc()
			.perform(post("/case-search").contentType(MediaType.APPLICATION_JSON)
				.param("caseSensitivity", "sensitive")
				.content("{\"search\":\"userFirstName:ada\",\"caseSensitivity\":\"insensitive\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
	}

	/**
	 * Application mapper stream constraints are preserved rather than bypassed by a
	 * default mapper.
	 */
	@Test
	void respectsApplicationMapperConstraints() throws Exception {
		JsonMapper mapper = JsonMapper
			.builder(JsonFactory.builder()
				.streamReadConstraints(StreamReadConstraints.builder().maxStringLength(8).build())
				.build())
			.build();
		mvc(new SearchSpecificationResolver(mapper))
			.perform(post("/case-search").contentType(MediaType.APPLICATION_JSON)
				.content("{\"search\":\"userFirstName:Ada\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEARCH_OPTIONS"));
	}

	/**
	 * Existing POST endpoints keep their own request bodies and GET/form search binding.
	 */
	@Test
	void legacyPostBodyIsNotConsumedBySearchBinding() throws Exception {
		mvc()
			.perform(post("/case-search-legacy").contentType(MediaType.APPLICATION_JSON)
				.param("search", "userFirstName:ada")
				.param("caseSensitivity", "insensitive")
				.content("{\"other\":1}"))
			.andExpect(status().isOk())
			.andExpect(content().string("ADA,Ada|{\"other\":1}"));
	}

	/**
	 * Builds a standalone MVC harness using the default resolver.
	 * @return MVC harness
	 */
	private MockMvc mvc() {
		return mvc(new SearchSpecificationResolver());
	}

	/**
	 * Builds the MVC harness without changing application-owned error rendering.
	 * @param resolver binding implementation under test
	 * @return MVC harness
	 */
	private MockMvc mvc(SearchSpecificationResolver resolver) {
		return MockMvcBuilders.standaloneSetup(new Endpoint(this.users))
			.setCustomArgumentResolvers(resolver)
			.setControllerAdvice(new Errors())
			.build();
	}

	/**
	 * Application endpoint owns fields, entity scope and response representation.
	 *
	 * @author Gunnar Hillert
	 */
	@RestController
	static class Endpoint {

		private final UsersRepository users;

		/**
		 * Retains the application repository.
		 * @param users fixture repository
		 */
		Endpoint(UsersRepository users) {
			this.users = users;
		}

		/**
		 * Binds normal GET queries with existing true defaults.
		 * @param search resolved optional query
		 * @return scoped names
		 */
		@GetMapping("/case-search")
		String get(@SearchSpec(strictValidation = true, allowedFields = { "userFirstName", "userLastName",
				"userChildrenNumber" }) @Nullable Specification<Users> search) {
			return execute(search);
		}

		/**
		 * Binds JSON using its stable search member even with a custom GET alias.
		 * @param search resolved optional query
		 * @return scoped names
		 */
		@PostMapping(path = "/case-search",
				consumes = { MediaType.APPLICATION_JSON_VALUE, "application/vnd.search+json" })
		String post(@SearchRequestBody @SearchSpec(searchParam = "filter", strictValidation = true, allowedFields = {
				"userFirstName", "userLastName", "userChildrenNumber" }) @Nullable Specification<Users> search) {
			return execute(search);
		}

		/**
		 * Retains legacy query binding when a controller owns an unrelated JSON body.
		 * @param search query-parameter search specification
		 * @param body application-owned body
		 * @return scoped names plus the original body
		 */
		@PostMapping(path = "/case-search-legacy", consumes = MediaType.APPLICATION_JSON_VALUE)
		String legacyPost(@SearchSpec(allowedFields = "userFirstName") @Nullable Specification<Users> search,
				@RequestBody String body) {
			return execute(search) + "|" + body;
		}

		/**
		 * Retains a consumer-configured false annotation default.
		 * @param search resolved optional query
		 * @return scoped names
		 */
		@GetMapping("/case-search-insensitive")
		String insensitive(@SearchSpec(caseSensitiveFlag = false, strictValidation = true, allowedFields = {
				"userFirstName", "userLastName", "userChildrenNumber" }) @Nullable Specification<Users> search) {
			return execute(search);
		}

		/**
		 * Retains the existing configurable GET search parameter name.
		 * @param search resolved optional query
		 * @return scoped names
		 */
		@GetMapping("/case-search-filter")
		String alias(@SearchSpec(searchParam = "filter", strictValidation = true, allowedFields = { "userFirstName",
				"userLastName", "userChildrenNumber" }) @Nullable Specification<Users> search) {
			return execute(search);
		}

		/**
		 * Composes mandatory scope outside every user-controlled Boolean expression.
		 * @param search resolved optional query
		 * @return scoped sorted names
		 */
		private String execute(@Nullable Specification<Users> search) {
			Specification<Users> scope = (root, query, builder) -> builder.equal(root.get("userAddress"), "A");
			return String.join(",",
					this.users.findAll((search == null) ? scope : scope.and(search))
						.stream()
						.map((user) -> user.userFirstName)
						.sorted()
						.toList());
		}

	}

	/**
	 * Consumer-owned structured exception rendering, never installed by the library.
	 *
	 * @author Gunnar Hillert
	 */
	@RestControllerAdvice
	static class Errors extends ResponseEntityExceptionHandler {

	}

}
