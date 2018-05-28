/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.webadmin.routes;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.User;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.base.Strings;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;

class UserQuotaRoutesTest {

    private static final String QUOTA_USERS = "/quota/users";
    private static final String PERDU_COM = "perdu.com";
    private static final String STRANGE_ORG = "strange.org";
    private static final User BOB = User.fromUsername("bob@" + PERDU_COM);
    private static final User ESCAPED_BOB = User.fromUsername("bob%40" + PERDU_COM);
    private static final User JOE = User.fromUsername("joe@" + PERDU_COM);
    private static final User JACK = User.fromUsername("jack@" + PERDU_COM);
    private static final User THE_GUY_WITH_STRANGE_DOMAIN = User.fromUsername("guy@" + STRANGE_ORG);
    private static final String PASSWORD = "secret";
    private static final String COUNT = "count";
    private static final String SIZE = "size";

    interface SetUp {
        @BeforeEach
        default void setUp(RestQuotaSearchTestSystem testSystem) throws Exception {
            DomainList domainList = testSystem.getQuotaSearchTestSystem().getDomainList();
            domainList.addDomain(Domain.of(PERDU_COM));
            domainList.addDomain(Domain.of(STRANGE_ORG));

            UsersRepository usersRepository = testSystem.getQuotaSearchTestSystem().getUsersRepository();
            usersRepository.addUser(BOB.asString(), PASSWORD);
            usersRepository.addUser(JACK.asString(), PASSWORD);
            usersRepository.addUser(THE_GUY_WITH_STRANGE_DOMAIN.asString(), PASSWORD);

            RestAssured.requestSpecification = testSystem.getRequestSpecification();
            RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        }
    }

    interface GetUsersQuotaRouteContract {

        @Test
        default void getUsersQuotaShouldReturnAllUsersWhenNoParameters(RestQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), THE_GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            given()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body("username", containsInAnyOrder(
                    BOB.asString(),
                    JACK.asString(),
                    THE_GUY_WITH_STRANGE_DOMAIN.asString()));
        }

        @Test
        default void getUsersQuotaShouldFilterOnDomain(RestQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), THE_GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            given()
                .param("domain", PERDU_COM)
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body("username", containsInAnyOrder(
                    BOB.asString(),
                    JACK.asString()));
        }

        @Test
        default void getUsersQuotaShouldLimitValues(RestQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), THE_GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            given()
                .param("limit", 2)
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body("username", containsInAnyOrder(
                    BOB.asString(),
                    THE_GUY_WITH_STRANGE_DOMAIN.asString()));
        }

        @Test
        default void getUsersQuotaShouldAcceptOffset(RestQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), THE_GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            given()
                .param("offset", 1)
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body("username", containsInAnyOrder(
                    JACK.asString(),
                    THE_GUY_WITH_STRANGE_DOMAIN.asString()));
        }

        @Test
        default void getUsersQuotaShouldFilterOnMinOccupationRatio(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(100));
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(49));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), THE_GUY_WITH_STRANGE_DOMAIN, withSize(51));

            testSystem.getQuotaSearchTestSystem().await();

            given()
                .param("minOccupationRatio", 0.5)
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body("username", containsInAnyOrder(
                    JACK.asString(),
                    THE_GUY_WITH_STRANGE_DOMAIN.asString()));
        }

        @Test
        default void getUsersQuotaShouldFilterOnMaxOccupationRatio(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(100));
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(49));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), THE_GUY_WITH_STRANGE_DOMAIN, withSize(51));

            testSystem.getQuotaSearchTestSystem().await();

            given()
                .param("maxOccupationRatio", 0.5)
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body("username", containsInAnyOrder(
                    JACK.asString(),
                    BOB.asString()));
        }

        default void appendMessage(QuotaSearchTestSystem testSystem, User user, MessageManager.AppendCommand appendCommand) throws MailboxException, UsersRepositoryException, DomainListException {
            MailboxManager mailboxManager = testSystem.getMailboxManager();
            MailboxSession session = mailboxManager.createSystemSession(user.asString());

            MailboxPath mailboxPath = MailboxPath.inbox(session);
            mailboxManager.createMailbox(mailboxPath, session);
            mailboxManager.getMailbox(mailboxPath, session)
                .appendMessage(appendCommand, session);
        }

        default MessageManager.AppendCommand withSize(int size) {
            byte[] bytes = Strings.repeat("a", size).getBytes(StandardCharsets.UTF_8);
            return MessageManager.AppendCommand.from(new ByteArrayInputStream(bytes));
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class ScanningGetUsersQuotaRouteTest implements GetUsersQuotaRouteContract, SetUp {

    }

    @Nested
    @ExtendWith(ElasticSearchRestQuotaSearchExtension.class)
    class ElasticSearchGetUsersQuotaRouteTest implements GetUsersQuotaRouteContract, SetUp {

    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class GetCount implements SetUp {

        @Test
        void getCountShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .get(QUOTA_USERS + "/" + JOE.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getCountShouldReturnNoContentByDefault() {
            given()
                .get(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getCountShouldReturnStoredValue(RestQuotaSearchTestSystem testSystem) throws MailboxException {
            int value = 42;
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(value));

            Long actual =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
                    .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(Long.class);

            assertThat(actual).isEqualTo(value);
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class GetSize implements SetUp {
        @Test
        void getSizeShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .get(QUOTA_USERS + "/" + JOE.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getSizeShouldReturnNoContentByDefault() {
            when()
                .get(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getSizeShouldReturnStoredValue(RestQuotaSearchTestSystem testSystem) throws MailboxException {
            long value = 42;
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.size(value));


            long quota =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(Long.class);

            assertThat(quota).isEqualTo(value);
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class PutCount implements SetUp {
        @Test
        void putCountShouldReturnNotFoundWhenUserDoesntExist() {
            given()
                .body("invalid")
            .when()
                .put(QUOTA_USERS + "/" + JOE.asString() + "/" + COUNT)
                .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putCountShouldAcceptEscapedUsers() {
            given()
                .body("35")
            .when()
                .put(QUOTA_USERS + "/" + ESCAPED_BOB.asString() + "/" + COUNT)
                .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putCountShouldRejectInvalid() {
            Map<String, Object> errors = given()
                .body("invalid")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
                .containsEntry("details", "For input string: \"invalid\"");
        }

        @Test
        void putCountShouldSetToInfiniteWhenMinusOne(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("-1")
            .when()
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).contains(QuotaCount.unlimited());
        }

        @Test
        void putCountShouldRejectNegativeOtherThanMinusOne() {
            Map<String, Object> errors = given()
                .body("-2")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1");
        }

        @Test
        void putCountShouldAcceptValidValue(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("42")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).contains(QuotaCount.count(42));
        }

        @Test
        @Disabled("no link between quota and mailbox for now")
        void putCountShouldRejectTooSmallValue(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("42")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).isEqualTo(42);
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class PutSize implements SetUp {
        @Test
        void putSizeAcceptEscapedUsers() {
            given()
                .body("36")
            .when()
                .put(QUOTA_USERS + "/" + ESCAPED_BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putSizeShouldRejectInvalid() {
            Map<String, Object> errors = given()
                .body("invalid")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
                .containsEntry("details", "For input string: \"invalid\"");
        }

        @Test
        void putSizeShouldReturnNotFoundWhenUserDoesntExist() {
            given()
                .body("123")
            .when()
                .put(QUOTA_USERS + "/" + JOE.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putSizeShouldSetToInfiniteWhenMinusOne(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("-1")
            .when()
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSize.unlimited());
        }

        @Test
        void putSizeShouldRejectNegativeOtherThanMinusOne() {
            Map<String, Object> errors = given()
                .body("-2")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1");
        }

        @Test
        void putSizeShouldAcceptValidValue(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("42")
            .when()
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB))).contains(QuotaSize.size(42));
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class DeleteCount implements SetUp {

        @Test
        void deleteCountShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .delete(QUOTA_USERS + "/" + JOE.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteCountShouldSetQuotaToEmpty(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(42));

            given()
                .delete(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).isEmpty();
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class DeleteSize implements SetUp {
        @Test
        void deleteSizeShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .delete(QUOTA_USERS + "/" + JOE.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteSizeShouldSetQuotaToEmpty(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.size(42));

            given()
                .delete(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB))).isEmpty();
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class GetQuota implements SetUp {
        @Test
        void getQuotaShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .get(QUOTA_USERS + "/" + JOE.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        public void getQuotaShouldReturnBothWhenValueSpecified(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(1111));
            maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(22));
            maxQuotaManager.setDomainMaxStorage(Domain.of(PERDU_COM), QuotaSize.size(34));
            maxQuotaManager.setDomainMaxMessage(Domain.of(PERDU_COM), QuotaCount.count(23));
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.size(42));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(52));

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(42);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("user." + SIZE)).isEqualTo(42);
            softly.assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("domain." + SIZE)).isEqualTo(34);
            softly.assertThat(jsonPath.getLong("domain." + COUNT)).isEqualTo(23);
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("global." + COUNT)).isEqualTo(22);
        }

        @Test
        public void getQuotaShouldReturnOccupation(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();
            InMemoryCurrentQuotaManager currentQuotaManager = testSystem.getQuotaSearchTestSystem().getCurrentQuotaManager();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.size(80));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(100));
            currentQuotaManager.increase(userQuotaRootResolver.forUser(BOB), 20, 40);

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("occupation.count")).isEqualTo(20);
            softly.assertThat(jsonPath.getLong("occupation.size")).isEqualTo(40);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.count")).isEqualTo(0.2);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.size")).isEqualTo(0.5);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.max")).isEqualTo(0.5);
        }

        @Test
        public void getQuotaShouldReturnOccupationWhenUnlimited(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();
            InMemoryCurrentQuotaManager currentQuotaManager = testSystem.getQuotaSearchTestSystem().getCurrentQuotaManager();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.unlimited());
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.unlimited());
            currentQuotaManager.increase(userQuotaRootResolver.forUser(BOB), 20, 40);

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("occupation.count")).isEqualTo(20);
            softly.assertThat(jsonPath.getLong("occupation.size")).isEqualTo(40);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.count")).isEqualTo(0);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.size")).isEqualTo(0);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.max")).isEqualTo(0);
        }

        @Test
        public void getQuotaShouldReturnOnlySpecifiedValues(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(1111));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(18));
            maxQuotaManager.setDomainMaxMessage(Domain.of(PERDU_COM), QuotaCount.count(52));

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(52);
            softly.assertThat(jsonPath.getObject("user." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain." + COUNT, Long.class)).isEqualTo(18);
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getObject("global." + COUNT, Long.class)).isNull();
        }

        @Test
        public void getQuotaShouldReturnGlobalValuesWhenNoUserValuesDefined(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(1111));
            maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(12));

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(12);
            softly.assertThat(jsonPath.getObject("user", Object.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain", Object.class)).isNull();
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("global." + COUNT)).isEqualTo(12);
        }

        @Test
        void getQuotaShouldReturnBothWhenValueSpecifiedAndEscaped(RestQuotaSearchTestSystem testSystem) throws MailboxException {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            int maxStorage = 42;
            int maxMessage = 52;
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.size(maxStorage));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(maxMessage));

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + ESCAPED_BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            assertThat(jsonPath.getLong("user." + SIZE)).isEqualTo(maxStorage);
            assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(maxMessage);
        }

        @Test
        void getQuotaShouldReturnBothEmptyWhenDefaultValues() {
            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            assertThat(jsonPath.getObject(SIZE, Long.class)).isNull();
            assertThat(jsonPath.getObject(COUNT, Long.class)).isNull();
        }

        @Test
        void getQuotaShouldReturnSizeWhenNoCount(RestQuotaSearchTestSystem testSystem) throws MailboxException {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            int maxStorage = 42;
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSize.size(maxStorage));

            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            assertThat(jsonPath.getLong("user." + SIZE)).isEqualTo(maxStorage);
            assertThat(jsonPath.getObject("user." + COUNT, Long.class)).isNull();
        }

        @Test
        void getQuotaShouldReturnBothWhenNoSize(RestQuotaSearchTestSystem testSystem) throws MailboxException {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            int maxMessage = 42;
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCount.count(maxMessage));


            JsonPath jsonPath =
                given()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            assertThat(jsonPath.getObject("user." + SIZE, Long.class)).isNull();
            assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(maxMessage);
        }
    }

    @Nested
    @ExtendWith(ScanningRestQuotaSearchExtension.class)
    class PutQuota implements SetUp {

        @Test
        void putQuotaShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .put(QUOTA_USERS + "/" + JOE.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putQuotaShouldUpdateBothQuota(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("{\"count\":52,\"size\":42}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCount.count(52));
            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSize.size(42));
        }

        @Test
        void putQuotaShouldUpdateBothQuotaWhenEscaped(RestQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            given()
                .body("{\"count\":52,\"size\":42}")
                .put(QUOTA_USERS + "/" + ESCAPED_BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCount.count(52));
            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSize.size(42));
        }
    }
}
