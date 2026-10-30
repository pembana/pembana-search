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

import java.util.HashMap;
import java.util.Map;

import com.pembana.search.core.CriterionTransformer;
import com.pembana.search.core.ValueConversion;

/**
 * Explicit immutable consumer-scoped registrations. Callbacks must be thread-safe.
 *
 * @author Gunnar Hillert
 */
public final class QueryExtensions {

	final Map<Class<?>, ValueConversion> types;

	final Map<String, ValueConversion> fields;

	final Map<String, PredicateRegistration> factories;

	final Map<String, CriterionTransformer> transformations;

	final boolean optionalToOne;

	final int maxExpansionNodes;

	final int maxExpansionDepth;

	private QueryExtensions(Builder builder) {
		this.optionalToOne = builder.optionalToOne;
		this.types = Map.copyOf(builder.types);
		this.fields = Map.copyOf(builder.fields);
		this.factories = Map.copyOf(builder.factories);
		this.transformations = Map.copyOf(builder.transformations);
		this.maxExpansionNodes = builder.maxExpansionNodes;
		this.maxExpansionDepth = builder.maxExpansionDepth;
	}

	/**
	 * Creates a registration builder; no classpath discovery is performed.
	 * @return empty builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	boolean isEmpty() {
		return !this.optionalToOne && this.types.isEmpty() && this.fields.isEmpty() && this.factories.isEmpty()
				&& this.transformations.isEmpty();
	}

	/**
	 * Builds immutable registries and rejects duplicate registrations immediately.
	 *
	 * @author Gunnar Hillert
	 */
	public static final class Builder {

		private final Map<Class<?>, ValueConversion> types = new HashMap<>();

		private final Map<String, ValueConversion> fields = new HashMap<>();

		private final Map<String, PredicateRegistration> factories = new HashMap<>();

		private final Map<String, CriterionTransformer> transformations = new HashMap<>();

		private boolean optionalToOne;

		private int maxExpansionNodes = 1024;

		private int maxExpansionDepth = 64;

		private Builder() {
		}

		/**
		 * Keeps missing to-one relations eligible for other matching OR branches.
		 * @param enabled true to use left joins for traversed to-one paths; false retains
		 * legacy navigation
		 * @return this builder
		 */
		public Builder optionalToOne(boolean enabled) {
			this.optionalToOne = enabled;
			return this;
		}

		/**
		 * Registers exact Java-type conversion, after any field converter.
		 * @param conversion immutable conversion contract
		 * @return this builder
		 */
		public Builder forType(ValueConversion conversion) {
			put(this.types, conversion.type(), conversion);
			return this;
		}

		/**
		 * Registers field conversion with priority over exact-type conversion.
		 * @param field canonical field
		 * @param conversion typed conversion contract
		 * @return this builder
		 */
		public Builder forField(String field, ValueConversion conversion) {
			verify(field);
			put(this.fields, field, conversion);
			return this;
		}

		/**
		 * Registers a logical field factory, including virtual fields.
		 * @param field canonical field
		 * @param registration logical type, operations and factory
		 * @return this builder
		 */
		public Builder factory(String field, PredicateRegistration registration) {
			verify(field);
			put(this.factories, field, registration);
			return this;
		}

		/**
		 * Registers a canonical leaf transformation, before conversion and factories.
		 * @param field source field
		 * @param transformer trusted transformation callback
		 * @return this builder
		 */
		public Builder transform(String field, CriterionTransformer transformer) {
			verify(field);
			put(this.transformations, field, transformer);
			return this;
		}

		/**
		 * Sets finite expansion work limits, independent of raw input limits.
		 * @param nodes positive maximum visited expansion nodes
		 * @param depth positive maximum expansion nesting
		 * @return this builder
		 */
		public Builder limits(int nodes, int depth) {
			if (nodes <= 0 || depth <= 0 || depth > 256) {
				throw new IllegalArgumentException("Positive expansion limits and depth at most 256 are required");
			}
			this.maxExpansionNodes = nodes;
			this.maxExpansionDepth = depth;
			return this;
		}

		/**
		 * Snapshots registrations without retaining mutable builder state.
		 * @return isolated immutable registry
		 */
		public QueryExtensions build() {
			for (var entry : this.fields.entrySet()) {
				PredicateRegistration factory = this.factories.get(entry.getKey());
				if (factory != null && factory.type() != entry.getValue().type()) {
					throw new IllegalArgumentException(
							"Field converter and factory disagree on type: " + entry.getKey());
				}
			}
			return new QueryExtensions(this);
		}

		private static void verify(String field) {
			if (!field.matches("[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)*")) {
				throw new IllegalArgumentException("Invalid extension field: " + field);
			}
		}

		private static <K, V> void put(Map<K, V> map, K key, V value) {
			java.util.Objects.requireNonNull(value);
			if (map.putIfAbsent(key, value) != null) {
				throw new IllegalArgumentException("Duplicate extension registration: " + key);
			}
		}

	}

}
