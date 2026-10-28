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
package com.sipios.springsearch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** Upstream defaults and field mappings; fluent setters replace Kotlin named arguments. */
@Entity
@Table(name = "USERS")
public final class Users {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
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

    public Users withUserId(Integer value) { userId = value; return this; }
    public Users withUserFirstName(String value) { userFirstName = value; return this; }
    public Users withIsAdmin(boolean value) { isAdmin = value; return this; }
    public Users withUserLastName(String value) { userLastName = value; return this; }
    public Users withUserEmail(String value) { userEmail = value; return this; }
    public Users withUserAddress(String value) { userAddress = value; return this; }
    public Users withUserChildrenNumber(Integer value) { userChildrenNumber = value; return this; }
    public Users withUserSalary(float value) { userSalary = value; return this; }
    public Users withUserAgeInSeconds(double value) { userAgeInSeconds = value; return this; }
    public Users withCreatedAt(Date value) { createdAt = value; return this; }
    public Users withUpdatedAt(LocalDateTime value) { updatedAt = value; return this; }
    public Users withUpdatedTimeAt(LocalTime value) { updatedTimeAt = value; return this; }
    public Users withUpdatedDateAt(LocalDate value) { updatedDateAt = value; return this; }
    public Users withUpdatedInstantAt(Instant value) { updatedInstantAt = value; return this; }
    public Users withValidityDuration(Duration value) { validityDuration = value; return this; }
    public Users withType(UserType value) { type = value; return this; }
    public Users withUuid(UUID value) { uuid = value; return this; }
    public Users withActive(Boolean value) { active = value; return this; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof Users user)) { return false; }
        return Objects.equals(userId, user.userId)
                && Objects.equals(userFirstName, user.userFirstName)
                && isAdmin == user.isAdmin
                && Objects.equals(userLastName, user.userLastName)
                && Objects.equals(userEmail, user.userEmail)
                && Objects.equals(userAddress, user.userAddress)
                && Objects.equals(userChildrenNumber, user.userChildrenNumber)
                && Float.compare(userSalary, user.userSalary) == 0
                && Double.compare(userAgeInSeconds, user.userAgeInSeconds) == 0
                && Objects.equals(createdAt, user.createdAt)
                && Objects.equals(updatedAt, user.updatedAt)
                && Objects.equals(updatedTimeAt, user.updatedTimeAt)
                && Objects.equals(updatedDateAt, user.updatedDateAt)
                && Objects.equals(updatedInstantAt, user.updatedInstantAt)
                && Objects.equals(validityDuration, user.validityDuration)
                && Objects.equals(type, user.type)
                && Objects.equals(uuid, user.uuid)
                && Objects.equals(active, user.active);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(userId);
        result = 31 * result + Objects.hashCode(userFirstName);
        result = 31 * result + Boolean.hashCode(isAdmin);
        result = 31 * result + Objects.hashCode(userLastName);
        result = 31 * result + Objects.hashCode(userEmail);
        result = 31 * result + Objects.hashCode(userAddress);
        result = 31 * result + Objects.hashCode(userChildrenNumber);
        result = 31 * result + Float.hashCode(userSalary);
        result = 31 * result + Double.hashCode(userAgeInSeconds);
        result = 31 * result + Objects.hashCode(createdAt);
        result = 31 * result + Objects.hashCode(updatedAt);
        result = 31 * result + Objects.hashCode(updatedTimeAt);
        result = 31 * result + Objects.hashCode(updatedDateAt);
        result = 31 * result + Objects.hashCode(updatedInstantAt);
        result = 31 * result + Objects.hashCode(validityDuration);
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(uuid);
        result = 31 * result + Objects.hashCode(active);
        return result;
    }
}
