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

import com.fasterxml.jackson.databind.util.StdDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.sipios.springsearch.SearchTestFixtures.searchSpec;
import static com.sipios.springsearch.SearchTestFixtures.setOf;

/** One Java method for each original Kotlin scenario; see docs/stage2/scenarios.json. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = SpringSearchApplication.class)
@Transactional
class SpringSearchApplicationTest {

    @Autowired
    UsersRepository userRepository;

    @Autowired
    AuthorRepository authorRepository;

    @Test
    void run() throws Exception {
    }

    @Test
    void canAddUsers() throws Exception {
        userRepository.save(new Users());

        Assertions.assertEquals(1, StreamSupport.stream(userRepository.findAll().spliterator(), false).count());
    }

    @Test
    void canGetUserWithId() throws Exception {
        var userId = userRepository.save(new Users()).userId;
        userRepository.save(new Users());

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch(("userId:" + userId)).build();
        Assertions.assertEquals(userId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithName() throws Exception {
        var aliceId = userRepository.save(new Users().withUserFirstName("Alice")).userId;
        userRepository.save(new Users().withUserFirstName("Bob"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:Alice").build();
        Assertions.assertEquals(aliceId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithFirstNameAndLastName() throws Exception {
        var aliceId = userRepository.save(new Users().withUserFirstName("Alice").withUserLastName("One")).userId;
        userRepository.save(new Users().withUserFirstName("Alice").withUserLastName("Two"));
        userRepository.save(new Users().withUserFirstName("Bob").withUserLastName("One"));
        userRepository.save(new Users().withUserFirstName("Bob").withUserLastName("Two"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:Alice AND userLastName:One").build();
        Assertions.assertEquals(aliceId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithFrenchName() throws Exception {
        var edouardProstId = userRepository.save(new Users().withUserFirstName("Édouard").withUserLastName("Pröst")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:Édouard AND userLastName:Pröst").build();
        Assertions.assertEquals(edouardProstId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithChineseName() throws Exception {
        var sunDemingId = userRepository.save(new Users().withUserFirstName("孫德明")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:孫德明").build();
        Assertions.assertEquals(sunDemingId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithChineseNameNoEncoding() throws Exception {
        var sunDemingId = userRepository.save(new Users().withUserFirstName("孫德明")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:孫德明").build();
        Assertions.assertEquals(sunDemingId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithSpecialCharactersName() throws Exception {
        var hackermanId = userRepository.save(new Users().withUserFirstName("&@#*\"''^^^$``%=+§__hack3rman__")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:&@#*\"''^^^$``%=+§__hack3rman__").build();
        Assertions.assertEquals(hackermanId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUserWithSpaceInNameWithString() throws Exception {
        var robertJuniorId = userRepository.save(new Users().withUserFirstName("robert junior")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:'robert junior'").build();
        Assertions.assertEquals(robertJuniorId, userRepository.findAll(specification).get(0).userId);
    }

    @Test
    void canGetUsersWithPartialStartingName() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("robert")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("roberta")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röbert"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:robe*").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robertId, robertaId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithPartialEndingName() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("robert")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("roubert")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röbęrt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:*ert").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robertId, robertaId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithPartialNameAndSpecialCharacter() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("rob*rt")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("rob*rta")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röb*rt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:rob**").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robertId, robertaId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithPartialNameContaining() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("Robert")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("Roberta")).userId;
        var toborobeId = userRepository.save(new Users().withUserFirstName("Toborobe")).userId;
        var obertaId = userRepository.save(new Users().withUserFirstName("oberta")).userId;
        userRepository.save(new Users().withUserFirstName("Robot"));
        userRepository.save(new Users().withUserFirstName("Röbert"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:*obe*").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                robertId,
                robertaId,
                toborobeId,
                obertaId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithPartialNameContainingWithSpecialCharacter() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("Rob*rt")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("rob*rta")).userId;
        var lobertaId = userRepository.save(new Users().withUserFirstName("Lob*rta")).userId;
        var tobertaId = userRepository.save(new Users().withUserFirstName("Tob*rta")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röb*rt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:*ob**").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                robertId,
                robertaId,
                lobertaId,
                tobertaId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithPartialNameContainingSpecialCharacterUsingSimpleString() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("Rob*rt")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("rob*rta")).userId;
        var lobertaId = userRepository.save(new Users().withUserFirstName("Lob*rta")).userId;
        var tobertaId = userRepository.save(new Users().withUserFirstName("Tob*rta")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röb*rt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:'*ob**'").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                robertId,
                robertaId,
                lobertaId,
                tobertaId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithPartialNameContainingWithSpecialCharacterUsingDoubleString() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("Rob*rt")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("rob*rta")).userId;
        var lobertaId = userRepository.save(new Users().withUserFirstName("Lob*rta")).userId;
        var tobertaId = userRepository.save(new Users().withUserFirstName("Tob*rta")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röb*rt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName:\"*ob**\"").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                robertId,
                robertaId,
                lobertaId,
                tobertaId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersNotContaining() throws Exception {
        var lobertaId = userRepository.save(new Users().withUserFirstName("Lobérta")).userId;
        var tobertaId = userRepository.save(new Users().withUserFirstName("Toberta")).userId;
        var robotId = userRepository.save(new Users().withUserFirstName("robot")).userId;
        var roobertId = userRepository.save(new Users().withUserFirstName("röbert")).userId;
        var unusedUserId1 = userRepository.save(new Users().withUserFirstName("Robèrt")).userId;
        var unusedUserId2 = userRepository.save(new Users().withUserFirstName("robèrta")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName!*è*").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                lobertaId,
                tobertaId,
                robotId,
                roobertId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersNotStartingWith() throws Exception {
        var aliceId = userRepository.save(new Users().withUserFirstName("Alice")).userId;
        var aliceId2 = userRepository.save(new Users().withUserFirstName("alice")).userId;
        var bobId = userRepository.save(new Users().withUserFirstName("bob")).userId;
        userRepository.save(new Users().withUserFirstName("Bob"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName!B*").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(aliceId, aliceId2, bobId), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersNotEndingWith() throws Exception {
        var bobId = userRepository.save(new Users().withUserFirstName("bob")).userId;
        var alicEId = userRepository.save(new Users().withUserFirstName("alicE")).userId;
        var boBId = userRepository.save(new Users().withUserFirstName("boB")).userId;
        userRepository.save(new Users().withUserFirstName("alice"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName!*e").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(boBId, alicEId, bobId), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithBigFamily() throws Exception {
        var userWith5ChildrenId = userRepository.save(new Users().withUserChildrenNumber(5)).userId;
        var userWith6ChildrenId = userRepository.save(new Users().withUserChildrenNumber(6)).userId;
        var user2With5ChildrenId = userRepository.save(new Users().withUserChildrenNumber(5)).userId;
        userRepository.save(new Users().withUserChildrenNumber(1));
        userRepository.save(new Users().withUserChildrenNumber(2));
        userRepository.save(new Users().withUserChildrenNumber(4));
        userRepository.save(new Users().withUserChildrenNumber(2));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber>4").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                user2With5ChildrenId,
                userWith5ChildrenId,
                userWith6ChildrenId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithSmallFamily() throws Exception {
        userRepository.save(new Users().withUserChildrenNumber(5));
        userRepository.save(new Users().withUserChildrenNumber(6));
        userRepository.save(new Users().withUserChildrenNumber(5));
        var userWith1ChildrenId = userRepository.save(new Users().withUserChildrenNumber(1)).userId;
        var userWith2ChildrenId = userRepository.save(new Users().withUserChildrenNumber(2)).userId;
        userRepository.save(new Users().withUserChildrenNumber(4));
        var user2With2ChildrenId = userRepository.save(new Users().withUserChildrenNumber(2)).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber<4").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                user2With2ChildrenId,
                userWith1ChildrenId,
                userWith2ChildrenId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithChildrenEquals() throws Exception {
        var user1With4ChildrenId = userRepository.save(new Users().withUserChildrenNumber(4)).userId;
        var user2With4ChildrenId = userRepository.save(new Users().withUserChildrenNumber(4)).userId;
        var user3With4ChildrenId = userRepository.save(new Users().withUserChildrenNumber(4)).userId;
        userRepository.save(new Users().withUserChildrenNumber(5));
        userRepository.save(new Users().withUserChildrenNumber(1));
        userRepository.save(new Users().withUserChildrenNumber(2));
        userRepository.save(new Users().withUserChildrenNumber(6));
        userRepository.save(new Users().withUserChildrenNumber(2));
        userRepository.save(new Users().withUserChildrenNumber(5));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber:4").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                user1With4ChildrenId,
                user2With4ChildrenId,
                user3With4ChildrenId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithChildrenNotEquals() throws Exception {
        var userWith5ChildrenId = userRepository.save(new Users().withUserChildrenNumber(5)).userId;
        var userWith1ChildId = userRepository.save(new Users().withUserChildrenNumber(1)).userId;
        var userWith6ChildrenId = userRepository.save(new Users().withUserChildrenNumber(6)).userId;
        userRepository.save(new Users().withUserChildrenNumber(2));
        userRepository.save(new Users().withUserChildrenNumber(2));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber!2").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                userWith1ChildId,
                userWith5ChildrenId,
                userWith6ChildrenId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithSmallerSalary() throws Exception {
        var smallerSalaryUserId = userRepository.save(new Users().withUserSalary(2223.3F)).userId;
        var smallerSalaryUser2Id = userRepository.save(new Users().withUserSalary(1500.2F)).userId;
        userRepository.save(new Users().withUserSalary(4000.0F));
        userRepository.save(new Users().withUserSalary(2550.7F));
        userRepository.save(new Users().withUserSalary(2300.0F));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userSalary<2300").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                smallerSalaryUserId,
                smallerSalaryUser2Id
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithHigherFloatSalary() throws Exception {
        var higherSalaryUserId = userRepository.save(new Users().withUserSalary(4000.1F)).userId;
        var higherSalaryUser2Id = userRepository.save(new Users().withUserSalary(5350.7F)).userId;
        userRepository.save(new Users().withUserSalary(2323.3F));
        userRepository.save(new Users().withUserSalary(1500.2F));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userSalary>4000.001").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                higherSalaryUserId,
                higherSalaryUser2Id
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithMedianSalary() throws Exception {
        var medianUserId = userRepository.save(new Users().withUserSalary(2323.3F)).userId;
        userRepository.save(new Users().withUserSalary(1500.2F));
        userRepository.save(new Users().withUserSalary(4000.1F));
        userRepository.save(new Users().withUserSalary(5350.7F));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userSalary<4000.1 AND userSalary>1500.2").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(medianUserId), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithAgeHigher() throws Exception {
        var olderUserId = userRepository.save(new Users().withUserAgeInSeconds(23222223.3)).userId;
        userRepository.save(new Users().withUserAgeInSeconds(23222223.2));
        userRepository.save(new Users().withUserAgeInSeconds(23222223.0));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userAgeInSeconds>23222223.2").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(olderUserId), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithAgeLower() throws Exception {
        var youngerUserId = userRepository.save(new Users().withUserAgeInSeconds(23222223.0)).userId;
        userRepository.save(new Users().withUserAgeInSeconds(23222223.2));
        userRepository.save(new Users().withUserAgeInSeconds(23222223.3));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userAgeInSeconds<23222223.2").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(youngerUserId), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithAgeEqual() throws Exception {
        var middleUserId = userRepository.save(new Users().withUserAgeInSeconds(23222223.2)).userId;
        userRepository.save(new Users().withUserAgeInSeconds(23222223.3));
        userRepository.save(new Users().withUserAgeInSeconds(23222223.0));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userAgeInSeconds:23222223.2").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(middleUserId), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithParentheses() throws Exception {
        var userOneWithHigherSalaryId = userRepository.save(new Users().withUserSalary(1500.2F).withUserLastName("One")).userId;
        var userTwoWithHigherSalaryId = userRepository.save(new Users().withUserSalary(1500.2F).withUserLastName("Two")).userId;
        userRepository.save(new Users().withUserSalary(1500.1F).withUserLastName("One"));
        userRepository.save(new Users().withUserSalary(1500.1F).withUserLastName("Two"));
        userRepository.save(new Users().withUserSalary(1500.1F).withUserLastName("Three"));
        userRepository.save(new Users().withUserSalary(1500.2F).withUserLastName("Three"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userSalary>1500.1 AND ( userLastName:One OR userLastName:Two )").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                userOneWithHigherSalaryId,
                userTwoWithHigherSalaryId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithInterlinkedConditions() throws Exception {
        var userOneWithSmallerSalaryId = userRepository.save(new Users().withUserSalary(1501F).withUserLastName("One")).userId;
        var userOeId = userRepository.save(new Users().withUserSalary(1501F).withUserLastName("Oe")).userId;
        userRepository.save(new Users().withUserSalary(1501F).withUserLastName("One one"));
        userRepository.save(new Users().withUserSalary(1501F).withUserLastName("Oneone"));
        userRepository.save(new Users().withUserSalary(1501F).withUserLastName("O n e"));
        userRepository.save(new Users().withUserSalary(1502F).withUserLastName("One"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userSalary<1502 AND ( ( userLastName:One OR userLastName:one ) OR userLastName!*n* )").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                userOneWithSmallerSalaryId,
                userOeId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithInterlinkedConditionsNoSpaces() throws Exception {
        var userOneWithSmallerSalaryId = userRepository.save(new Users().withUserSalary(1501F).withUserLastName("One")).userId;
        var userOeId = userRepository.save(new Users().withUserSalary(1501F).withUserLastName("Oe")).userId;
        userRepository.save(new Users().withUserSalary(1501F).withUserLastName("One one"));
        userRepository.save(new Users().withUserSalary(1501F).withUserLastName("Oneone"));
        userRepository.save(new Users().withUserSalary(1501F).withUserLastName("O n e"));
        userRepository.save(new Users().withUserSalary(1502F).withUserLastName("One"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userSalary<1502 AND ((userLastName:One OR userLastName:one) OR userLastName!*n*)").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(
                userOneWithSmallerSalaryId,
                userOeId
            ), specificationUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersByBoolean() throws Exception {
        userRepository.save(new Users().withIsAdmin(true));
        userRepository.save(new Users().withIsAdmin(false));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("isAdmin:true").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, specificationUsers.size());
    }

    @Test
    void canGetUsersEarlierThanDate() throws Exception {
        var sdf = new StdDateFormat();
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-01")));
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-03")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt<'2019-01-02'").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, specificationUsers.size());
    }

    @Test
    void canGetUsersAfterDate() throws Exception {
        var sdf = new StdDateFormat();
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-01")));
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-03")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt>'2019-01-02'").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, specificationUsers.size());

        specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt>'2019-01-04'").build();
        specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(0, specificationUsers.size());
    }

    @Test
    void canGetUsersAfterEqualDate() throws Exception {
        var sdf = new StdDateFormat();
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-01")));
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-03")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt>:'2019-01-01'").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(2, specificationUsers.size());

        specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt>:'2019-01-04'").build();
        specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(0, specificationUsers.size());
    }

    @Test
    void canGetUsersEarlierEqualDate() throws Exception {
        var sdf = new StdDateFormat();
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-01")));
        userRepository.save(new Users().withCreatedAt(sdf.parse("2019-01-03")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt<:'2019-01-01'").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, specificationUsers.size());

        specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt<:'2019-01-03'").build();
        specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(2, specificationUsers.size());
    }

    @Test
    void canGetUsersAtPreciseDate() throws Exception {
        var sdf = new StdDateFormat();
        var date = sdf.parse("2019-01-01");
        userRepository.save(new Users().withCreatedAt(date));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch(("createdAt:'" + sdf.format(date) + "'")).build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, specificationUsers.size());
    }

    @Test
    void canGetUsersAtPreciseDateNotEqual() throws Exception {
        var sdf = new StdDateFormat();
        var date = sdf.parse("2019-01-01");
        userRepository.save(new Users().withCreatedAt(date));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("createdAt!'2019-01-02'").build();
        var specificationUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, specificationUsers.size());
    }

    @Test
    void canGetUsersWithCaseInsensitiveLowerCaseSearch() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("ROBERT")).userId;
        var robertaId = userRepository.save(new Users().withUserFirstName("roberta")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röbęrt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userFirstName:robe*").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robertId, robertaId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithCaseInsensitiveUpperCaseSearch() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("ROBERT")).userId;
        var roubertId = userRepository.save(new Users().withUserFirstName("roubert")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röbęrt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userFirstName:*ert").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robertId, roubertId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithCaseInsensitiveContainsSearch() throws Exception {
        var robertId = userRepository.save(new Users().withUserFirstName("ROBERT")).userId;
        var roubertId = userRepository.save(new Users().withUserFirstName("roubert")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));
        userRepository.save(new Users().withUserFirstName("röbęrt"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userFirstName:*er*").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robertId, roubertId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithCaseInsensitiveDoesntContainSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("ROBERT"));
        var roubertId = userRepository.save(new Users().withUserFirstName("roubert")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userFirstName!*rob*").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(roubertId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithCaseInsensitiveDoesntStartSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("ROBERT"));
        var roubertId = userRepository.save(new Users().withUserFirstName("roubert")).userId;
        userRepository.save(new Users().withUserFirstName("robot"));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userFirstName!rob*").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(roubertId), robeUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithCaseInsensitiveDoesntEndSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("ROBERT"));
        userRepository.save(new Users().withUserFirstName("roubert"));
        var robotId = userRepository.save(new Users().withUserFirstName("robot")).userId;

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userFirstName!*rt").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(robotId), robotUsers.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUsersWithUserTypeEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("Hamid").withType(UserType.TEAM_MEMBER));
        userRepository.save(new Users().withUserFirstName("Reza").withType(UserType.TEAM_MEMBER));
        userRepository.save(new Users().withUserFirstName("Ireh").withType(UserType.TEAM_MEMBER));
        userRepository.save(new Users().withUserFirstName("robot").withType(UserType.ADMINISTRATOR));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("type:ADMINISTRATOR").build();
        var robeUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robeUsers.size());
        Assertions.assertEquals("robot", robeUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUserTypeNotEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withType(UserType.TEAM_MEMBER));
        userRepository.save(new Users().withUserFirstName("Ireh").withType(UserType.ADMINISTRATOR));
        userRepository.save(new Users().withUserFirstName("robot").withType(UserType.TEAM_MEMBER));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("type!TEAM_MEMBER").build();
        var irehUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, irehUsers.size());
        Assertions.assertEquals("Ireh", irehUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedAtGreaterSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedAt>'2020-01-11T09:20:30'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdateInstantAtGreaterSearch() throws Exception {
        userRepository.save(
            new Users().withUserFirstName("HamidReza").withUpdatedInstantAt(Instant.parse("2020-01-10T10:15:30Z"))
        );
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedInstantAt(Instant.parse("2020-01-11T10:20:30Z")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedInstantAt>'2020-01-11T09:20:30Z'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdateInstantAtGreaterThanEqualSearch() throws Exception {
        userRepository.save(
            new Users().withUserFirstName("john").withUpdatedInstantAt(Instant.parse("2020-01-10T10:15:30Z"))
        );
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedInstantAt(Instant.parse("2020-01-11T10:20:30Z")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedInstantAt>:'2020-01-11T09:20:30Z'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdateInstantAtLessThanEqualSearch() throws Exception {
        userRepository.save(
            new Users().withUserFirstName("john").withUpdatedInstantAt(Instant.parse("2020-01-10T10:15:30Z"))
        );
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedInstantAt(Instant.parse("2020-01-11T10:20:30Z")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedInstantAt<:'2020-01-11T09:20:30Z'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("john", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedAtLessSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedAt<'2020-01-11T09:20:30'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedAtEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedAt:'2020-01-10T10:15:30'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedAtNotEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedAt!'2020-01-11T10:20:30'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedAtGreaterThanEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:20:30")));
        userRepository.save(new Users().withUserFirstName("robot2").withUpdatedAt(LocalDateTime.parse("2020-01-12T10:20:30")));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedAt>:'2020-01-11T10:20:30'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        Assertions.assertFalse(users.stream().anyMatch(user -> Objects.equals(user.userFirstName, "john" )));
    }

    @Test
    void canGetUsersWithUpdatedAtLessThanEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:20:30")));
        userRepository.save(new Users().withUserFirstName("robot2").withUpdatedAt(LocalDateTime.parse("2020-01-12T10:20:30")));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedAt<:'2020-01-11T10:20:30'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        Assertions.assertFalse(users.stream().anyMatch(user -> Objects.equals(user.userFirstName, "robot2" )));
    }

    @Test
    void canGetUsersWithUpdatedDateAtGreaterSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedDateAt(LocalDate.parse("2020-01-11")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt>'2020-01-10'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedDateAtLessSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedDateAt(LocalDate.parse("2020-01-11")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt<'2020-01-11'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedDateAtEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedDateAt(LocalDate.parse("2020-01-11")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt:'2020-01-10'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedDateAtNotEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedDateAt(LocalDate.parse("2020-01-11")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt!'2020-01-11'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedDateAtLessThanEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedDateAt(LocalDate.parse("2020-01-11")));
        userRepository.save(new Users().withUserFirstName("robot2").withUpdatedDateAt(LocalDate.parse("2020-01-12")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt<:'2020-01-11'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        Assertions.assertFalse(users.stream().anyMatch(user -> Objects.equals(user.userFirstName, "robot2" )));
    }

    @Test
    void canGetUsersWithUpdatedDateAtGreaterThanEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedDateAt(LocalDate.parse("2020-01-11")));
        userRepository.save(new Users().withUserFirstName("robot2").withUpdatedDateAt(LocalDate.parse("2020-01-12")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt>:'2020-01-11'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        Assertions.assertFalse(users.stream().anyMatch(user -> Objects.equals(user.userFirstName, "john" )));
    }

    @Test
    void canGetUsersWithUpdatedTimeAtGreaterSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedTimeAt(LocalTime.parse("10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedTimeAt(LocalTime.parse("10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedTimeAt>'10:15:30'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedTimeAtLessSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedTimeAt(LocalTime.parse("10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedTimeAt(LocalTime.parse("10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedTimeAt<'10:16:30'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedTimeAtEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedTimeAt(LocalTime.parse("10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedTimeAt(LocalTime.parse("10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedTimeAt:'10:15:30'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUpdatedTimeAtLessThanEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedTimeAt(LocalTime.parse("10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedTimeAt(LocalTime.parse("10:20:30")));
        userRepository.save(new Users().withUserFirstName("robot2").withUpdatedTimeAt(LocalTime.parse("10:25:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedTimeAt<:'10:20:30'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        Assertions.assertFalse(users.stream().anyMatch(user -> Objects.equals(user.userFirstName, "robot2" )));
    }

    @Test
    void canGetUsersWithUpdatedTimeAtGreaterThanEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedTimeAt(LocalTime.parse("10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedTimeAt(LocalTime.parse("10:20:30")));
        userRepository.save(new Users().withUserFirstName("robot2").withUpdatedTimeAt(LocalTime.parse("10:25:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedTimeAt>:'10:20:30'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        Assertions.assertFalse(users.stream().anyMatch(user -> Objects.equals(user.userFirstName, "john" )));
    }

    @Test
    void canGetUsersWithUpdatedTimeAtNotEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withUpdatedTimeAt(LocalTime.parse("10:15:30")));
        userRepository.save(new Users().withUserFirstName("robot").withUpdatedTimeAt(LocalTime.parse("10:20:30")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedTimeAt!'10:20:30'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithDurationGreaterSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withValidityDuration(Duration.parse("PT10H")));
        userRepository.save(new Users().withUserFirstName("robot").withValidityDuration(Duration.parse("PT15H")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("validityDuration>'PT10H'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithDurationLessSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withValidityDuration(Duration.parse("PT10H")));
        userRepository.save(new Users().withUserFirstName("robot").withValidityDuration(Duration.parse("PT15H")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("validityDuration<'PT11H'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithDurationEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withValidityDuration(Duration.parse("PT10H")));
        userRepository.save(new Users().withUserFirstName("robot").withValidityDuration(Duration.parse("PT15H")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("validityDuration:'PT10H'").build();
        var hamidrezaUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, hamidrezaUsers.size());
        Assertions.assertEquals("HamidReza", hamidrezaUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithDurationNotEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("HamidReza").withValidityDuration(Duration.parse("PT10H")));
        userRepository.save(new Users().withUserFirstName("robot").withValidityDuration(Duration.parse("PT15H")));

        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("validityDuration!'PT10H'").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals("robot", robotUsers.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithUUIDEqualSearch() throws Exception {
        var userUUID = UUID.randomUUID();
        userRepository.save(new Users().withUserFirstName("Diego").withUuid(userUUID));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch(("uuid:'" + userUUID + "'")).build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals(userUUID, robotUsers.get(0).uuid);
    }

    @Test
    void canGetUsersWithUUIDNotEqualSearch() throws Exception {
        var userUUID = UUID.randomUUID();
        var user2UUID = UUID.randomUUID();
        userRepository.save(new Users().withUserFirstName("Diego").withUuid(userUUID));
        userRepository.save(new Users().withUserFirstName("Diego two").withUuid(user2UUID));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch(("uuid!'" + userUUID + "'")).build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals(user2UUID, robotUsers.get(0).uuid);
    }

    @Test
    void canGetUsersWithNumberOfChildrenLessOrEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber<:2").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(1, users.size());
        Assertions.assertEquals("john", users.get(0).userFirstName);
    }

    @Test
    void canGetUsersWithNumberOfChildrenGreaterOrEqualSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber>:3").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
    }

    @Test
    void canGetUsersWithNumberOfChildrenLessSearch() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber<3").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(1, users.size());
        Assertions.assertEquals("john", users.get(0).userFirstName);
    }

    @Test
    void canGetUserWithNameIn() throws Exception {
        var johnId = userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2)).userId;
        var janeId = userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3)).userId;
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName IN [\"john\", \"jane\"]").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(johnId, janeId), users.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithNameInEmptyList() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName IN []").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(users.isEmpty());
    }

    @Test
    void canGetUserWithNameNotIn() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        var joeId = userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4)).userId;
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName NOT IN [\"john\", \"jane\"]").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(joeId), users.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithChildrenNumberNotIn() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        var joeId = userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4)).userId;
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber NOT IN [2, 3]").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(joeId), users.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithChildrenNumberIn() throws Exception {
        var johnId = userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2)).userId;
        var janeId = userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3)).userId;
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(4));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber IN [2, 3]").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(janeId, johnId), users.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithTypeIn() throws Exception {
        var johnId = userRepository.save(new Users().withUserFirstName("john").withType(UserType.TEAM_MEMBER)).userId;
        var janeId = userRepository.save(new Users().withUserFirstName("jane").withType(UserType.ADMINISTRATOR)).userId;
        userRepository.save(new Users().withUserFirstName("joe").withType(UserType.MANAGER));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("type IN [ADMINISTRATOR, TEAM_MEMBER]").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(janeId, johnId), users.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetUserWithIn() throws Exception {
        var johnId = userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10"))).userId;
        var janeId = userRepository.save(new Users().withUserFirstName("jane").withUpdatedDateAt(LocalDate.parse("2020-01-15"))).userId;
        userRepository.save(new Users().withUserFirstName("joe").withUpdatedDateAt(LocalDate.parse("2021-01-10")));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt IN ['2020-01-10', '2020-01-15']").build();
        var users = userRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(setOf(janeId, johnId), users.stream().map(user -> user.userId).collect(Collectors.toSet())));
    }

    @Test
    void canGetAuthorsWithEmptyBook() throws Exception {
        var johnBook = new Book();
        var john = new Author();
        john.name = "john";
        john.addBook(johnBook);
        authorRepository.save(john);
        var janeBook = new Book();
        var jane = new Author();
        jane.name = "jane";
        jane.addBook(janeBook);
        authorRepository.save(jane);
        var specification = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS EMPTY").build();
        var users = authorRepository.findAll(specification);
        Assertions.assertTrue(users.isEmpty());
    }

    @Test
    void cantSearchForEmptyWithNonFieldProperties() throws Exception {
        var johnBook = new Book();
        var john = new Author();
        john.name = "john";
        john.addBook(johnBook);
        authorRepository.save(john);
        var janeBook = new Book();
        var jane = new Author();
        jane.name = "jane";
        jane.addBook(janeBook);
        authorRepository.save(jane);
        var specification = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("name IS EMPTY").build();
        Assertions.assertThrows(ResponseStatusException.class, () -> { authorRepository.findAll(specification) ; });
        var specification2 = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("name IS NOT EMPTY").build();
        Assertions.assertThrows(ResponseStatusException.class, () -> { authorRepository.findAll(specification2) ; });
    }
    @Test
    void canGetAuthorsWithEmptyBookWithResult() throws Exception {
        var johnBook = new Book();
        var john = new Author();
        john.name = "john";
        john.addBook(johnBook);
        authorRepository.save(john);
        var jane = new Author();
        jane.name = "jane";
        authorRepository.save(jane);
        var specification = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS EMPTY").build();
        var users = authorRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(users.size(), 1));
        Assertions.assertTrue(Objects.equals(users.get(0).name, jane.name));
    }

    @Test
    void canGetAuthorsWithBooksNotEmpty() throws Exception {
        var johnBook = new Book();
        var john = new Author();
        john.name = "john";
        john.addBook(johnBook);
        authorRepository.save(john);
        var jane = new Author();
        jane.name = "jane";
        authorRepository.save(jane);
        var specification = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS NOT EMPTY").build();
        var users = authorRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(users.size(), 1));
        Assertions.assertTrue(Objects.equals(users.get(0).name, john.name));
    }

    @Test
    void canGetAuthorsWithBooksNotEmptyAllResult() throws Exception {
        var johnBook = new Book();
        var john = new Author();
        john.name = "john";
        john.addBook(johnBook);
        authorRepository.save(john);
        var jane = new Author();
        jane.name = "jane";
        var janeBook = new Book();
        jane.addBook(janeBook);
        authorRepository.save(jane);
        var specification = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS NOT EMPTY").build();
        var users = authorRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(users.size(), 2));
    }

    @Test
    void canGetAuthorsWithBooksNotEmptyNoResult() throws Exception {
        var john = new Author();
        john.name = "john";
        authorRepository.save(john);
        var jane = new Author();
        jane.name = "jane";
        authorRepository.save(jane);
        var specification = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS NOT EMPTY").build();
        var users = authorRepository.findAll(specification);
        Assertions.assertTrue(Objects.equals(users.size(), 0));
    }
    @Test
    void cantGetAuthorsWithBooksNull() throws Exception {
        var john = new Author();
        john.name = "john";
        authorRepository.save(john);
        var jane = new Author();
        jane.name = "jane";
        authorRepository.save(jane);
        var spec = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS NULL").build();
        Assertions.assertThrows(ResponseStatusException.class, () -> { authorRepository.findAll(spec) ; });
        var specNotNull = new SpecificationsBuilder<Author>(
            searchSpec("", true, new String[0])
        ).withSearch("books IS NOT NULL").build();
        Assertions.assertThrows(ResponseStatusException.class, () -> { authorRepository.findAll(specNotNull) ; });
    }

    @Test
    void canGetUsersWithNumberOfChildrenBetween() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(5));
        userRepository.save(new Users().withUserFirstName("jean").withUserChildrenNumber(10));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userChildrenNumber BETWEEN 4 AND 10").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }

    @Test
    void canGetUsersWithUpdatedDateBetween() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("jane").withUpdatedDateAt(LocalDate.parse("2020-01-11")));
        userRepository.save(new Users().withUserFirstName("joe").withUpdatedDateAt(LocalDate.parse("2020-01-12")));
        userRepository.save(new Users().withUserFirstName("jean").withUpdatedDateAt(LocalDate.parse("2020-01-13")));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt BETWEEN 2020-01-12 AND 2020-01-13").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }

    @Test
    void canGetUsersWithUpdatedDateNotBetween() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("jane").withUpdatedDateAt(LocalDate.parse("2020-01-11")));
        userRepository.save(new Users().withUserFirstName("joe").withUpdatedDateAt(LocalDate.parse("2020-01-12")));
        userRepository.save(new Users().withUserFirstName("jean").withUpdatedDateAt(LocalDate.parse("2020-01-13")));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("updatedDateAt NOT BETWEEN 2020-01-12 AND 2020-01-13").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("john", "jane"), setNames);
    }

    @Test
    void canGetUsersWithUpdatedDateBetweenAndIdIn() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("jane").withUpdatedDateAt(LocalDate.parse("2020-01-11")));
        var joeId = userRepository.save(new Users().withUserFirstName("joe").withUpdatedDateAt(LocalDate.parse("2020-01-12"))).userId;
        var jeanId = userRepository.save(new Users().withUserFirstName("jean").withUpdatedDateAt(LocalDate.parse("2020-01-13"))).userId;
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch(("updatedDateAt BETWEEN 2020-01-11 AND 2020-01-13 AND userId IN [" + joeId + ", " + jeanId + "]")).build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }

    @Test
    void canGetUsersWithUserFirstNameBetween() throws Exception {
        userRepository.save(new Users().withUserFirstName("abel"));
        userRepository.save(new Users().withUserFirstName("bob"));
        userRepository.save(new Users().withUserFirstName("connor"));
        userRepository.save(new Users().withUserFirstName("david"));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName BETWEEN 'aaron' AND 'cyrano'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(3, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("abel", "bob", "connor"), setNames);
    }
    @Test
    void canGetUsersWithUserFirstNameNotBetween() throws Exception {
        userRepository.save(new Users().withUserFirstName("abel"));
        userRepository.save(new Users().withUserFirstName("bob"));
        userRepository.save(new Users().withUserFirstName("connor"));
        userRepository.save(new Users().withUserFirstName("david"));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName NOT BETWEEN 'aaron' AND 'cyrano'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(1, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("david"), setNames);
    }
    @Test
    void canGetUsersWithUserFirstNameGt() throws Exception {
        userRepository.save(new Users().withUserFirstName("abel"));
        userRepository.save(new Users().withUserFirstName("bob"));
        userRepository.save(new Users().withUserFirstName("connor"));
        userRepository.save(new Users().withUserFirstName("david"));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName > barry'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(3, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("connor", "david", "bob"), setNames);
    }

    @Test
    void canGetUsersWithUserFirstNameLt() throws Exception {
        userRepository.save(new Users().withUserFirstName("abel"));
        userRepository.save(new Users().withUserFirstName("bob"));
        userRepository.save(new Users().withUserFirstName("connor"));
        userRepository.save(new Users().withUserFirstName("david"));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName < barry'").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(1, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("abel"), setNames);
    }

    @Test
    void canGetUsersWithUserFirstNameCaseSensitive() throws Exception {
        userRepository.save(new Users().withUserFirstName("abel"));
        userRepository.save(new Users().withUserFirstName("Aaron"));
        userRepository.save(new Users().withUserFirstName("connor"));
        userRepository.save(new Users().withUserFirstName("david"));
        // create spec with case sensitive flag
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName : A*").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(1, users.size());
        Assertions.assertEquals("Aaron", users.get(0).userFirstName);
    }
    // test for a wrong search, should throw an exception during the parse
    @Test
    void badRequestWithWrongSearch() throws Exception {
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Users>(
                searchSpec("", true, new String[0])
            ).withSearch("userFirstName : ").build();
        });
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Users>(
                searchSpec("", true, new String[0])
            ).withSearch("updatedDateAt BETWEEN  AND 2020-01-11").build();
        });
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Users>(
                searchSpec("", true, new String[0])
            ).withSearch("updatedDateAt BETWEEN 2020-01-11 AND").build();
        });
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Author>(
                searchSpec("", true, new String[0])
            ).withSearch("books IS EMPT ").build();
        });
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Author>(
                searchSpec("", true, new String[0])
            ).withSearch("books IS NOT EMPT ").build();
        });
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Users>(
                searchSpec("", true, new String[0])
            ).withSearch("userId IN [").build();
        });
    }

    @Test
    void canGetUsersWithNullColumn() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withType(null));
        userRepository.save(new Users().withUserFirstName("jane").withType(UserType.ADMINISTRATOR));
        userRepository.save(new Users().withUserFirstName("joe").withType(UserType.MANAGER));
        userRepository.save(new Users().withUserFirstName("jean").withType(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("type IS NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("john", "jean"), setNames);
    }

    @Test
    void canGetUsersWithNotNullColumn() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withType(null));
        userRepository.save(new Users().withUserFirstName("jane").withType(UserType.ADMINISTRATOR));
        userRepository.save(new Users().withUserFirstName("joe").withType(UserType.MANAGER));
        userRepository.save(new Users().withUserFirstName("jean").withType(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("type IS NOT NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("jane", "joe"), setNames);
    }

    @Test
    void canGetUsersWithNotNullFirstName() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withType(null));
        userRepository.save(new Users().withUserFirstName("jane").withType(UserType.ADMINISTRATOR));
        userRepository.save(new Users().withUserFirstName("joe").withType(UserType.MANAGER));
        userRepository.save(new Users().withUserFirstName("jean").withType(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", true, new String[0])
        ).withSearch("userFirstName IS NOT NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(4, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("john", "jane", "joe", "jean"), setNames);
    }

    @Test
    void canGetUserWithNullSalary() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserSalary(100.0F));
        userRepository.save(new Users().withUserFirstName("jane").withUserSalary(1000.0F));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userSalary IS NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(0, users.size());
    }

    @Test
    void canNotSearchABlackListedField() throws Exception {
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            new SpecificationsBuilder<Users>(
                searchSpec("", false, new String[] {"userFirstName"})
            ).withSearch("userFirstName : A* AND userId : 3").build();
        });
    }

    @Test
    void canGetUsersWithUUIDNull() throws Exception {
        var userUUID = UUID.randomUUID();
        userRepository.save(new Users().withUserFirstName("Diego").withUuid(userUUID));
        userRepository.save(new Users().withUserFirstName("Diego two").withUuid(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("uuid IS NULL").build();
        var robotUsers = userRepository.findAll(specification);
        Assertions.assertEquals(1, robotUsers.size());
        Assertions.assertEquals(null, robotUsers.get(0).uuid);
    }

    @Test
    void canGetUsersWithUpdatedDateAtNull() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedDateAt(LocalDate.parse("2020-01-10")));
        userRepository.save(new Users().withUserFirstName("jane").withUpdatedDateAt(LocalDate.parse("2020-01-11")));
        userRepository.save(new Users().withUserFirstName("joe").withUpdatedDateAt(null));
        userRepository.save(new Users().withUserFirstName("jean").withUpdatedDateAt(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("updatedDateAt IS NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }

    @Test
    void canGetUsersWithUpdatedDateTimeAtNotNull() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUpdatedAt(LocalDateTime.parse("2020-01-10T10:15:30")));
        userRepository.save(new Users().withUserFirstName("jane").withUpdatedAt(LocalDateTime.parse("2020-01-11T10:15:30")));
        userRepository.save(new Users().withUserFirstName("joe").withUpdatedAt(null));
        userRepository.save(new Users().withUserFirstName("jean").withUpdatedAt(LocalDateTime.parse("2020-01-13T10:15:30")));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("updatedAt IS NOT NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(3, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("john", "jane", "jean"), setNames);
    }
    @Test
    void canGetUsersWithActiveNull() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withActive(true));
        userRepository.save(new Users().withUserFirstName("jane").withActive(false));
        userRepository.save(new Users().withUserFirstName("joe").withActive(null));
        userRepository.save(new Users().withUserFirstName("jean").withActive(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("active IS NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }
    @Test
    void canGetUsersWithUserChildrenNumberNull() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withUserChildrenNumber(2));
        userRepository.save(new Users().withUserFirstName("jane").withUserChildrenNumber(3));
        userRepository.save(new Users().withUserFirstName("joe").withUserChildrenNumber(null));
        userRepository.save(new Users().withUserFirstName("jean").withUserChildrenNumber(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("userChildrenNumber IS NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }
    @Test
    void canGetUsersWithCreatedAtNull() throws Exception {
        userRepository.save(new Users().withUserFirstName("john").withCreatedAt(new Date()));
        userRepository.save(new Users().withUserFirstName("jane").withCreatedAt(new Date()));
        userRepository.save(new Users().withUserFirstName("joe").withCreatedAt(null));
        userRepository.save(new Users().withUserFirstName("jean").withCreatedAt(null));
        var specification = new SpecificationsBuilder<Users>(
            searchSpec("", false, new String[0])
        ).withSearch("createdAt IS NULL").build();
        var users = userRepository.findAll(specification);
        Assertions.assertEquals(2, users.size());
        var setNames = users.stream().map(user -> user.userFirstName).collect(Collectors.toSet());
        Assertions.assertEquals(setOf("joe", "jean"), setNames);
    }}
