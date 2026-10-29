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

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.pembana.search.configuration.ResolverConf;
import com.pembana.search.configuration.SearchSpecificationResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Checks Boot 4 discovery without relying on the consumer scanning the library's package.
 *
 * @author Gunnar Hillert
 */
class ResolverAutoConfigurationTest {

	/**
	 * A consumer's named resolver replaces the default without duplicate registration.
	 */
	@Test
	void consumerCanReplaceSearchResolver() {
		HandlerMethodArgumentResolver replacement = mock(HandlerMethodArgumentResolver.class);
		new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(ResolverConf.class))
			.withBean(ResolverConf.RESOLVER_BEAN_NAME, HandlerMethodArgumentResolver.class, () -> replacement)
			.run((context) -> {
				var resolvers = new ArrayList<HandlerMethodArgumentResolver>();
				context.getBean(ResolverConf.class).addArgumentResolvers(resolvers);
				assertThat(resolvers).hasSize(1);
				assertSame(replacement, resolvers.get(0));
				assertThat(context).doesNotHaveBean(SearchSpecificationResolver.class);
			});
	}

	@Test
	void bootImportsDiscoverTheResolverConfiguration() {
		assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
			.contains(ResolverConf.class.getName());
	}

	@Test
	void servletConsumersReceiveTheArgumentResolver() {
		new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(ResolverConf.class))
			.run((context) -> {
				assertThat(context).hasSingleBean(ResolverConf.class);
				var resolvers = new ArrayList<HandlerMethodArgumentResolver>();
				context.getBean(ResolverConf.class).addArgumentResolvers(resolvers);
				assertThat(resolvers).hasExactlyElementsOfTypes(SearchSpecificationResolver.class);
			});
	}

	@Test
	void directJpaConsumersDoNotRequireAWebContext() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ResolverConf.class))
			.run((context) -> assertThat(context).doesNotHaveBean(ResolverConf.class));
	}

	@Test
	void missingMvcClassesDoNotPreventStartup() {
		new ApplicationContextRunner().withClassLoader(new FilteredClassLoader(WebMvcConfigurer.class))
			.withConfiguration(AutoConfigurations.of(ResolverConf.class))
			.run((context) -> assertThat(context).doesNotHaveBean(ResolverConf.class));
	}

}
