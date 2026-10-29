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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

/**
 * Book fixture for Pembana Search database verification.
 *
 * @author vincentescoffier (upstream Git author)
 * @author Gunnar Hillert
 */
@Entity
@Table(name = "book")
public class Book {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	public Long id;

	@ManyToOne
	@JoinColumn(name = "author_id")
	public Author author;

	@Column(name = "title")
	public String title;

	@Override
	public final boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null) {
			return false;
		}
		Class<?> otherType = (other instanceof HibernateProxy proxy)
				? proxy.getHibernateLazyInitializer().getPersistentClass() : other.getClass();
		Class<?> thisType = (this instanceof HibernateProxy proxy)
				? proxy.getHibernateLazyInitializer().getPersistentClass() : getClass();
		if (thisType != otherType) {
			return false;
		}
		Book book = (Book) other;
		return this.id != null && this.id.equals(book.id);
	}

	@Override
	public final int hashCode() {
		return (this instanceof HibernateProxy proxy)
				? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
	}

}
