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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the search argument resolver for servlet applications with Spring MVC
 * available.
 *
 * @author Thibault F
 * @author Gunnar Hillert
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ResolverConf implements WebMvcConfigurer {

	/** Bean name applications can replace to supply their own request binding. */
	public static final String RESOLVER_BEAN_NAME = "pembanaSearchSpecificationResolver";

	private final @Nullable ObjectProvider<HandlerMethodArgumentResolver> resolverProvider;

	/** Creates a ResolverConf with the inherited default configuration. */
	public ResolverConf() {
		this.resolverProvider = null;
	}

	/**
	 * Defers resolver lookup until MVC registration, honoring a consumer's named bean.
	 * @param resolverProvider provider for the replaceable search resolver
	 */
	@Autowired
	public ResolverConf(@Qualifier(RESOLVER_BEAN_NAME) ObjectProvider<HandlerMethodArgumentResolver> resolverProvider) {
		this.resolverProvider = resolverProvider;
	}

	/**
	 * Supplies default binding when the application has not provided a replacement.
	 * @return default search resolver
	 */
	@Bean(RESOLVER_BEAN_NAME)
	@ConditionalOnMissingBean(name = RESOLVER_BEAN_NAME)
	public SearchSpecificationResolver searchSpecificationResolver() {
		return new SearchSpecificationResolver();
	}

	/** {@inheritDoc} */
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		argumentResolvers
			.add((this.resolverProvider != null) ? this.resolverProvider.getObject() : searchSpecificationResolver());
	}

}
