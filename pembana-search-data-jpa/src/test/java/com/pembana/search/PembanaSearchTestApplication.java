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
package com.pembana.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot configuration for the compatibility and database suites.
 *
 * @author Thibault F
 * @author Michael Mollard
 * @author Gunnar Hillert
 */
@SpringBootApplication
public class PembanaSearchTestApplication {

	/**
	 * @param args command-line arguments for the test-only application
	 */
	public static void main(String[] args) {
		SpringApplication.run(PembanaSearchTestApplication.class, args);
	}

}
