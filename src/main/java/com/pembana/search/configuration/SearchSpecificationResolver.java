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
package com.pembana.search.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pembana.search.SpecificationsBuilder;
import com.pembana.search.annotation.SearchSpec;

/**
 * Resolves annotated Spring MVC search parameters without imposing consumer authorization
 * scopes.
 *
 * @author Spring Search contributors
 * @author Gunnar Hillert
 */
public final class SearchSpecificationResolver implements HandlerMethodArgumentResolver {

	/** Creates a SearchSpecificationResolver with the inherited default configuration. */
	public SearchSpecificationResolver() {
	}

	private static final Logger logger = LoggerFactory.getLogger(SearchSpecificationResolver.class);

	/** {@inheritDoc} */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == Specification.class
				&& parameter.hasParameterAnnotation(SearchSpec.class);
	}

	/** {@inheritDoc} */
	@Override
	public Specification<?> resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		SearchSpec def = parameter.getParameterAnnotation(SearchSpec.class);
		return buildSpecification(parameter.getGenericParameterType().getClass(),
				webRequest.getParameter(def.searchParam()), def);
	}

	private <T> Specification<T> buildSpecification(Class<T> specClass, String search, SearchSpec annotation) {
		logger.debug("Building specification for class {}", specClass);
		logger.debug("Search value found is {}", search);
		if (search == null || search.isEmpty()) {
			return null;
		}
		return new SpecificationsBuilder<T>(annotation).withSearch(search).build();
	}

}
