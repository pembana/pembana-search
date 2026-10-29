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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Upstream defaults and field mappings; fluent setters replace Kotlin named arguments.
 *
 * @author Michael Mollard
 * @author HamidReza Ireh
 * @author Diego Corredor
 * @author fernandorubio (upstream Git author)
 * @author Mahdi Lazraq
 * @author vincentescoffier (upstream Git author)
 * @author Vincent Escoffier
 * @author Gunnar Hillert
 */
@Entity
@Table(name = "USERS")
public final class Users {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Integer userId = null;

	@Column(name = "FirstName")
	public String userFirstName = "John";

	@Column(name = "isAdmin")
	public boolean isAdmin = true;

	@Column(name = "LastName")
	public String userLastName = "Doe";

	@Column(name = "email")
	public String userEmail = "john.doe@wanahoo.fr";

	@Column(name = "PostalAddress")
	public String userAddress = "1 rue de l'angleterre";

	@Column(name = "NumberOfChildren")
	public Integer userChildrenNumber = 3;

	@Column(name = "Salary")
	public float userSalary = 3000.0F;

	@Column(name = "AgeInSeconds")
	public double userAgeInSeconds = 1261440000.0;

	@Column
	public Date createdAt = new Date();

	@Column
	public LocalDateTime updatedAt = LocalDateTime.now();

	@Column
	public LocalTime updatedTimeAt = LocalTime.now();

	@Column
	public LocalDate updatedDateAt = LocalDate.now();

	@Column
	public Instant updatedInstantAt = Instant.now();

	@Column
	public Duration validityDuration = Duration.ofDays(30);

	@Column(name = "UserType")
	public UserType type = UserType.TEAM_MEMBER;

	@Column
	public UUID uuid = UUID.randomUUID();

	@Column
	public Boolean active = true;

	public Users withUserId(Integer value) {
		this.userId = value;
		return this;
	}

	public Users withUserFirstName(String value) {
		this.userFirstName = value;
		return this;
	}

	public Users withIsAdmin(boolean value) {
		this.isAdmin = value;
		return this;
	}

	public Users withUserLastName(String value) {
		this.userLastName = value;
		return this;
	}

	public Users withUserEmail(String value) {
		this.userEmail = value;
		return this;
	}

	public Users withUserAddress(String value) {
		this.userAddress = value;
		return this;
	}

	public Users withUserChildrenNumber(Integer value) {
		this.userChildrenNumber = value;
		return this;
	}

	public Users withUserSalary(float value) {
		this.userSalary = value;
		return this;
	}

	public Users withUserAgeInSeconds(double value) {
		this.userAgeInSeconds = value;
		return this;
	}

	public Users withCreatedAt(Date value) {
		this.createdAt = value;
		return this;
	}

	public Users withUpdatedAt(LocalDateTime value) {
		this.updatedAt = value;
		return this;
	}

	public Users withUpdatedTimeAt(LocalTime value) {
		this.updatedTimeAt = value;
		return this;
	}

	public Users withUpdatedDateAt(LocalDate value) {
		this.updatedDateAt = value;
		return this;
	}

	public Users withUpdatedInstantAt(Instant value) {
		this.updatedInstantAt = value;
		return this;
	}

	public Users withValidityDuration(Duration value) {
		this.validityDuration = value;
		return this;
	}

	public Users withType(UserType value) {
		this.type = value;
		return this;
	}

	public Users withUuid(UUID value) {
		this.uuid = value;
		return this;
	}

	public Users withActive(Boolean value) {
		this.active = value;
		return this;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Users user)) {
			return false;
		}
		return Objects.equals(this.userId, user.userId) && Objects.equals(this.userFirstName, user.userFirstName)
				&& this.isAdmin == user.isAdmin && Objects.equals(this.userLastName, user.userLastName)
				&& Objects.equals(this.userEmail, user.userEmail) && Objects.equals(this.userAddress, user.userAddress)
				&& Objects.equals(this.userChildrenNumber, user.userChildrenNumber)
				&& Float.compare(this.userSalary, user.userSalary) == 0
				&& Double.compare(this.userAgeInSeconds, user.userAgeInSeconds) == 0
				&& Objects.equals(this.createdAt, user.createdAt) && Objects.equals(this.updatedAt, user.updatedAt)
				&& Objects.equals(this.updatedTimeAt, user.updatedTimeAt)
				&& Objects.equals(this.updatedDateAt, user.updatedDateAt)
				&& Objects.equals(this.updatedInstantAt, user.updatedInstantAt)
				&& Objects.equals(this.validityDuration, user.validityDuration) && Objects.equals(this.type, user.type)
				&& Objects.equals(this.uuid, user.uuid) && Objects.equals(this.active, user.active);
	}

	@Override
	public int hashCode() {
		int result = 0;
		result = 31 * result + Objects.hashCode(this.userId);
		result = 31 * result + Objects.hashCode(this.userFirstName);
		result = 31 * result + Boolean.hashCode(this.isAdmin);
		result = 31 * result + Objects.hashCode(this.userLastName);
		result = 31 * result + Objects.hashCode(this.userEmail);
		result = 31 * result + Objects.hashCode(this.userAddress);
		result = 31 * result + Objects.hashCode(this.userChildrenNumber);
		result = 31 * result + Float.hashCode(this.userSalary);
		result = 31 * result + Double.hashCode(this.userAgeInSeconds);
		result = 31 * result + Objects.hashCode(this.createdAt);
		result = 31 * result + Objects.hashCode(this.updatedAt);
		result = 31 * result + Objects.hashCode(this.updatedTimeAt);
		result = 31 * result + Objects.hashCode(this.updatedDateAt);
		result = 31 * result + Objects.hashCode(this.updatedInstantAt);
		result = 31 * result + Objects.hashCode(this.validityDuration);
		result = 31 * result + Objects.hashCode(this.type);
		result = 31 * result + Objects.hashCode(this.uuid);
		result = 31 * result + Objects.hashCode(this.active);
		return result;
	}

}
