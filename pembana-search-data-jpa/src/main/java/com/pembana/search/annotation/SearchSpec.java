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
package com.pembana.search.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a request search parameter to a JPA specification.
 *
 * @author Thibault F
 * @author Michael Mollard
 * @author Luc Boussant-roux
 * @author Mahdi Lazraq
 * @author Vincent Escoffier
 * @author vincentescoffier (upstream Git author)
 * @author Gunnar Hillert
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchSpec {

	/**
	 * Returns HTTP query parameter name consumed by the MVC resolver.
	 * @return HTTP query parameter name consumed by the MVC resolver
	 */
	String searchParam() default "search";

	/**
	 * Selects a server-registered HTTP field profile by its SearchProfile name.
	 * @return profile name, or empty for ordinary canonical field handling
	 */
	String profile() default "";

	/**
	 * Returns whether String comparisons retain case. False folds the field with SQL
	 * lower and the input with Locale.ROOT for equality, patterns, membership, ordering
	 * and ranges. Explicit request choices override this flag; it is not accent folding.
	 * @return true to retain case in String comparisons (the default)
	 */
	boolean caseSensitiveFlag() default true;

	/**
	 * Returns exactly forbidden entity paths; these override allowed fields.
	 * @return exactly forbidden entity paths; these override allowed fields
	 */
	String[] blackListedFields() default {};

	/**
	 * Returns whether to reject lexical errors, unknown fields and unsupported typed
	 * operations. Enabled by default.
	 * @return whether to reject lexical errors, unknown fields and unsupported typed
	 * operations
	 */
	boolean strictValidation() default true;

	/**
	 * Returns exact searchable paths; an empty list preserves unrestricted legacy access.
	 * @return exact searchable paths; an empty list preserves unrestricted legacy access
	 */
	String[] allowedFields() default {};

	/**
	 * Returns maximum expression characters, or zero to explicitly disable this limit.
	 * @return maximum expression characters, or zero for the legacy unlimited behavior
	 */
	int maxQueryLength() default 4096;

	/**
	 * Returns maximum parenthesis depth, or zero to explicitly disable this limit.
	 * @return maximum parenthesis depth, or zero for the legacy unlimited behavior
	 */
	int maxNestingDepth() default 16;

}
