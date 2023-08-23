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

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.base.Strings;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

class UserQuotaRoutesTest {

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(Duration.ofMillis(100))
        .and().pollDelay(Duration.ofMillis(100))
        .await();

    private static final String QUOTA_USERS = "/quota/users";
    private static final String LOST_LOCAL = "lost.local";
    private static final String STRANGE_LOCAL = "strange.local";
    private static final Username BOB = Username.of("bob@" + LOST_LOCAL);
    private static final Username ESCAPED_BOB = Username.of("bob%40" + LOST_LOCAL);
    private static final Username JOE = Username.of("joe@" + LOST_LOCAL);
    private static final Username JACK = Username.of("jack@" + LOST_LOCAL);
    private static final Username GUY_WITH_STRANGE_DOMAIN = Username.of("guy@" + STRANGE_LOCAL);
    private static final String PASSWORD = "secret";
    private static final String COUNT = "count";
    private static final String SIZE = "size";

    @BeforeEach
    void setUp(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
        DomainList domainList = testSystem.getQuotaSearchTestSystem().getDomainList();
        domainList.addDomain(Domain.of(LOST_LOCAL));
        domainList.addDomain(Domain.of(STRANGE_LOCAL));

        UsersRepository usersRepository = testSystem.getQuotaSearchTestSystem().getUsersRepository();
        usersRepository.addUser(BOB, PASSWORD);
        usersRepository.addUser(JACK, PASSWORD);
        usersRepository.addUser(GUY_WITH_STRANGE_DOMAIN, PASSWORD);

        RestAssured.requestSpecification = testSystem.getRequestSpecification()
            .urlEncodingEnabled(false); // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    interface GetUsersQuotaRouteContract {

        @Test
        default void getUsersQuotaShouldReturnAllUsersWhenNoParameters(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", containsInAnyOrder(
                            BOB.asString(),
                            JACK.asString(),
                            GUY_WITH_STRANGE_DOMAIN.asString()));
                });
        }

        @Test
        default void getUsersQuotaShouldFilterOnDomain(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .param("domain", LOST_LOCAL)
                    .when()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", containsInAnyOrder(
                            BOB.asString(),
                            JACK.asString()));
                });
        }

        @Test
        default void getUsersQuotaShouldLimitValues(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .param("limit", 2)
                    .when()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", containsInAnyOrder(
                            BOB.asString(),
                            GUY_WITH_STRANGE_DOMAIN.asString()));
                });
        }

        @Test
        default void getUsersQuotaShouldAcceptOffset(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .param("offset", 1)
                    .when()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", containsInAnyOrder(
                            JACK.asString(),
                            GUY_WITH_STRANGE_DOMAIN.asString()));
                });
        }

        @Test
        default void getUsersQuotaShouldFilterOnMinOccupationRatio(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(100));
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(49));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(51));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .param("minOccupationRatio", 0.5)
                    .when()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", containsInAnyOrder(
                            JACK.asString(),
                            GUY_WITH_STRANGE_DOMAIN.asString()));
                });
        }

        @Test
        default void getUsersQuotaShouldFilterOnMaxOccupationRatio(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(100));
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(49));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(51));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .param("maxOccupationRatio", 0.5)
                    .when()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", containsInAnyOrder(
                            JACK.asString(),
                            BOB.asString()));
                });
        }

        @Test
        default void getUsersQuotaShouldOrderByUsername(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(100));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(51));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(50));
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(49));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                        .get("/quota/users")
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .body("username", contains(
                            BOB.asString(),
                            GUY_WITH_STRANGE_DOMAIN.asString(),
                            JACK.asString()));
                });
        }

        @Test
        default void minOccupationRatioShouldNotBeNegative() {
            given()
                .param("minOccupationRatio", -0.5)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("minOccupationRatio can not be negative"));
        }

        @Test
        default void minOccupationRatioShouldAcceptZero() {
            given()
                .param("minOccupationRatio", 0)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200);
        }

        @Test
        default void minOccupationRatioShouldNotBeString() {
            given()
                .param("minOccupationRatio", "invalid")
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Can not parse minOccupationRatio"))
                .body("details", equalTo("For input string: \"invalid\""));
        }

        @Test
        default void maxOccupationRatioShouldNotBeNegative() {
            given()
                .param("maxOccupationRatio", -0.5)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("maxOccupationRatio can not be negative"));
        }

        @Test
        default void maxOccupationRatioShouldAcceptZero() {
            given()
                .param("maxOccupationRatio", 0)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200);
        }

        @Test
        default void maxOccupationRatioShouldNotBeString() {
            given()
                .param("maxOccupationRatio", "invalid")
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Can not parse maxOccupationRatio"))
                .body("details", equalTo("For input string: \"invalid\""));
        }

        @Test
        default void limitShouldNotBeNegative() {
            given()
                .param("limit", -2)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("limit can not be negative"));
        }

        @Test
        default void limitShouldNotBeZero() {
            given()
                .param("limit", 0)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("limit can not be equal to zero"));
        }

        @Test
        default void limitShouldNotBeString() {
            given()
                .param("limit", "invalid")
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Can not parse limit"))
                .body("details", equalTo("For input string: \"invalid\""));
        }

        @Test
        default void offsetShouldNotBeNegative() {
            given()
                .param("offset", -2)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("offset can not be negative"));
        }

        @Test
        default void offsetShouldAcceptZero() {
            given()
                .param("offset", 0)
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.OK_200);
        }

        @Test
        default void offsetShouldNotBeString() {
            given()
                .param("offset", "invalid")
            .when()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Can not parse offset"))
                .body("details", equalTo("For input string: \"invalid\""));
        }

        @Test
        default void getUsersQuotaShouldReturnUserDetails(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(100));
            appendMessage(testSystem.getQuotaSearchTestSystem(), BOB, withSize(10));
            appendMessage(testSystem.getQuotaSearchTestSystem(), JACK, withSize(11));
            appendMessage(testSystem.getQuotaSearchTestSystem(), GUY_WITH_STRANGE_DOMAIN, withSize(50));

            testSystem.getQuotaSearchTestSystem().await();

            CALMLY_AWAIT.atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    String jsonAsString =
                        with()
                            .param("minOccupationRatio", 0.5)
                            .get("/quota/users")
                        .then()
                            .statusCode(HttpStatus.OK_200)
                            .extract()
                            .body()
                            .asString();

                    assertThatJson(jsonAsString)
                        .when(IGNORING_ARRAY_ORDER)
                        .isEqualTo("[" +
                            "    {" +
                            "        \"detail\": {" +
                            "            \"global\": {" +
                            "                \"count\": null," +
                            "                \"size\": 100" +
                            "            }," +
                            "            \"domain\": null," +
                            "            \"user\": null," +
                            "            \"computed\": {" +
                            "                \"count\": null," +
                            "                \"size\": 100" +
                            "            }," +
                            "            \"occupation\": {" +
                            "                \"size\": 50," +
                            "                \"count\": 1," +
                            "                \"ratio\": {" +
                            "                    \"size\": 0.5," +
                            "                    \"count\": 0.0," +
                            "                    \"max\": 0.5" +
                            "                }" +
                            "            }" +
                            "        }," +
                            "        \"username\": \"guy@strange.local\"" +
                            "    }" +
                            "]");
                });
        }


        default void appendMessage(QuotaSearchTestSystem testSystem, Username username, MessageManager.AppendCommand appendCommand) throws MailboxException {
            MailboxManager mailboxManager = testSystem.getMailboxManager();
            MailboxSession session = mailboxManager.createSystemSession(username);

            MailboxPath mailboxPath = MailboxPath.inbox(username);
            mailboxManager.createMailbox(mailboxPath, session);
            mailboxManager.getMailbox(mailboxPath, session)
                .appendMessage(appendCommand, session);
        }

        default MessageManager.AppendCommand withSize(int size) {
            byte[] bytes = Strings.repeat("a", size).getBytes(StandardCharsets.UTF_8);
            return MessageManager.AppendCommand.from(new ByteContent(bytes));
        }
    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class ScanningGetUsersQuotaRouteTest implements GetUsersQuotaRouteContract {

    }

    @Nested
    @ExtendWith(OpenSearchQuotaSearchExtension.class)
    class OpenSearchGetUsersQuotaRouteTest implements GetUsersQuotaRouteContract {

    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class GetCount {

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
        void getCountShouldReturnStoredValue(WebAdminQuotaSearchTestSystem testSystem) throws MailboxException {
            int value = 42;
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(value));

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
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class GetSize {
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
        void getSizeShouldReturnStoredValue(WebAdminQuotaSearchTestSystem testSystem) throws MailboxException {
            long value = 42;
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(value));


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
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class PutCount {
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
            Map<String, Object> errors = with()
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
        void putCountShouldSetToInfiniteWhenMinusOne(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("-1")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).contains(QuotaCountLimit.unlimited());
        }

        @Test
        void putCountShouldRejectNegativeOtherThanMinusOne() {
            Map<String, Object> errors = with()
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
        void putCountShouldAcceptValidValue(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("42")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).contains(QuotaCountLimit.count(42));
        }

        @Test
        @Disabled("no link between quota and mailbox for now")
        void putCountShouldRejectTooSmallValue(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("42")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).isEqualTo(42);
        }
    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class PutSize {
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
            Map<String, Object> errors = with()
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
        void putSizeShouldSetToInfiniteWhenMinusOne(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("-1")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSizeLimit.unlimited());
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
        void putSizeShouldAcceptValidValue(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("42")
                .put(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB))).contains(QuotaSizeLimit.size(42));
        }
    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class DeleteCount {

        @Test
        void deleteCountShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .delete(QUOTA_USERS + "/" + JOE.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteCountShouldSetQuotaToEmpty(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(42));

            with()
                .delete(QUOTA_USERS + "/" + BOB.asString() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB))).isEmpty();
        }
    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class DeleteSize {
        @Test
        void deleteSizeShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .delete(QUOTA_USERS + "/" + JOE.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteSizeShouldSetQuotaToEmpty(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            with()
                .delete(QUOTA_USERS + "/" + BOB.asString() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB))).isEmpty();
        }
    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class GetQuota {
        @Test
        void getQuotaShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .get(QUOTA_USERS + "/" + JOE.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getQuotaShouldReturnBothWhenValueSpecified(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1111));
            maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(22));
            maxQuotaManager.setDomainMaxStorage(Domain.of(LOST_LOCAL), QuotaSizeLimit.size(34));
            maxQuotaManager.setDomainMaxMessage(Domain.of(LOST_LOCAL), QuotaCountLimit.count(23));
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(52));

            JsonPath jsonPath =
                when()
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
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnOccupation(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();
            InMemoryCurrentQuotaManager currentQuotaManager = testSystem.getQuotaSearchTestSystem().getCurrentQuotaManager();
            QuotaOperation quotaIncrease = new QuotaOperation(userQuotaRootResolver.forUser(BOB), QuotaCountUsage.count(20), QuotaSizeUsage.size(40));

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(80));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(100));
            currentQuotaManager.increase(quotaIncrease).block();

            JsonPath jsonPath =
                when()
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
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnOccupationWhenUnlimited(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();
            InMemoryCurrentQuotaManager currentQuotaManager = testSystem.getQuotaSearchTestSystem().getCurrentQuotaManager();
            QuotaOperation quotaIncrease = new QuotaOperation(userQuotaRootResolver.forUser(BOB), QuotaCountUsage.count(20), QuotaSizeUsage.size(40));

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.unlimited());
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.unlimited());
            currentQuotaManager.increase(quotaIncrease).block();

            JsonPath jsonPath =
                when()
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
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnOnlySpecifiedValues(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1111));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(18));
            maxQuotaManager.setDomainMaxMessage(Domain.of(LOST_LOCAL), QuotaCountLimit.count(52));

            JsonPath jsonPath =
                when()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(18);
            softly.assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(18);
            softly.assertThat(jsonPath.getObject("user." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain." + COUNT, Long.class)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getObject("global." + COUNT, Long.class)).isNull();
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnGlobalValuesWhenNoUserValuesDefined(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1111));
            maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(12));

            JsonPath jsonPath =
                when()
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
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnBothWhenValueSpecifiedAndEscaped(WebAdminQuotaSearchTestSystem testSystem) throws MailboxException {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            int maxStorage = 42;
            int maxMessage = 52;
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(maxStorage));
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(maxMessage));

            JsonPath jsonPath =
                when()
                    .get(QUOTA_USERS + "/" + ESCAPED_BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("user." + SIZE)).isEqualTo(maxStorage);
            softly.assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(maxMessage);
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnBothEmptyWhenDefaultValues() {
            JsonPath jsonPath =
                when()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getObject(SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject(COUNT, Long.class)).isNull();
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnSizeWhenNoCount(WebAdminQuotaSearchTestSystem testSystem) throws MailboxException {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            int maxStorage = 42;
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(maxStorage));

            JsonPath jsonPath =
                when()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("user." + SIZE)).isEqualTo(maxStorage);
            softly.assertThat(jsonPath.getObject("user." + COUNT, Long.class)).isNull();
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnBothWhenNoSize(WebAdminQuotaSearchTestSystem testSystem) throws MailboxException {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            int maxMessage = 42;
            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(maxMessage));


            JsonPath jsonPath =
                when()
                    .get(QUOTA_USERS + "/" + BOB.asString())
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getObject("user." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getLong("user." + COUNT)).isEqualTo(maxMessage);
            softly.assertAll();
        }
    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class PutQuota {

        @Test
        void putQuotaShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .put(QUOTA_USERS + "/" + JOE.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putQuotaWithNegativeCountShouldFail(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            Map<String, Object> errors = with()
                .body("{\"count\":-2,\"size\":42}")
                .put(QUOTA_USERS + "/" + BOB.asString())
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
        void putQuotaWithNegativeCountShouldNotUpdatePreviousQuota(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":-2,\"size\":42}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaWithNegativeSizeShouldFail(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            Map<String, Object> errors = with()
                .body("{\"count\":52,\"size\":-3}")
                .put(QUOTA_USERS + "/" + BOB.asString())
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
        void putQuotaWithNegativeSizeShouldNotUpdatePreviousQuota(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":52,\"size\":-3}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldUpdateBothQuota(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("{\"count\":52,\"size\":42}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldUpdateBothQuotaWhenEscaped(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            with()
                .body("{\"count\":52,\"size\":42}")
                .put(QUOTA_USERS + "/" + ESCAPED_BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldRemoveCount(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(BOB), QuotaCountLimit.count(52));

            with()
                .body("{\"count\":null,\"size\":42}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .isEmpty();
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldRemoveSize(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":52,\"size\":null}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .isEmpty();
            softly.assertAll();
        }

        @Test
        void putQuotaShouldRemoveBoth(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
            MaxQuotaManager maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
            UserQuotaRootResolver userQuotaRootResolver = testSystem.getQuotaSearchTestSystem().getQuotaRootResolver();

            maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(BOB), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":null,\"size\":null}")
                .put(QUOTA_USERS + "/" + BOB.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(BOB)))
                .isEmpty();
            softly.assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(BOB)))
                .isEmpty();
            softly.assertAll();
        }

    }

    @Nested
    @ExtendWith(ScanningQuotaSearchExtension.class)
    class PostRecomputeQuotas {
        @Test
        void actionRequestParameterShouldBeCompulsory() {
            when()
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'task' query parameter is compulsory. Supported values are [RecomputeCurrentQuotas]"));
        }

        @Test
        void postShouldFailUponEmptyAction() {
            given()
                .queryParam("task", "")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'task' query parameter cannot be empty or blank. Supported values are [RecomputeCurrentQuotas]"));
        }

        @Test
        void postShouldFailUponInvalidAction() {
            given()
                .queryParam("task", "invalid")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("Invalid value supplied for query parameter 'task': invalid. Supported values are [RecomputeCurrentQuotas]"));
        }

        @Test
        void postShouldFailWhenUsersPerSecondIsNotAnInt() {
            given()
                .queryParam("task", "RecomputeCurrentQuotas")
                .queryParam("usersPerSecond", "abc")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("Illegal value supplied for query parameter 'usersPerSecond', expecting a strictly positive optional integer"));
        }

        @Test
        void postShouldFailWhenUsersPerSecondIsNegative() {
            given()
                .queryParam("task", "RecomputeCurrentQuotas")
                .queryParam("usersPerSecond", "-1")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'usersPerSecond' needs to be strictly positive"));
        }

        @Test
        void postShouldFailWhenUsersPerSecondIsZero() {
            given()
                .queryParam("task", "RecomputeCurrentQuotas")
                .queryParam("usersPerSecond", "0")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'usersPerSecond' needs to be strictly positive"));
        }

        @Test
        void postShouldCreateANewTask() {
            given()
                .queryParam("task", "RecomputeCurrentQuotas")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .body("taskId", notNullValue());
        }

        @Test
        void postShouldCreateANewTaskWhenConcurrencyParametersAreSpecified() {
            given()
                .queryParam("task", "RecomputeCurrentQuotas")
                .queryParam("usersPerSecond", "1")
                .post("/quota/users")
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .body("taskId", notNullValue());
        }

        @Test
        void recomputeAllShouldCompleteWhenNoUser() {
            String taskId = with()
                .queryParam("task", "RecomputeCurrentQuotas")
                .post("/quota/users")
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(taskId))
                .body("type", is("recompute-current-quotas"))
                .body("additionalInformation.processedQuotaRoots", is(0))
                .body("additionalInformation.failedQuotaRoots", hasSize(0))
                .body("additionalInformation.runningOptions.usersPerSecond", is(1))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()));
        }

        @Test
        void runningOptionsShouldBePartOfTaskDetails() {
            String taskId = with()
                .queryParam("task", "RecomputeCurrentQuotas")
                .queryParam("usersPerSecond", "20")
                .post("/quota/users")
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("taskId", is(taskId))
                .body("type", is("recompute-current-quotas"))
                .body("additionalInformation.runningOptions.usersPerSecond", is(20));
        }
    }
}
