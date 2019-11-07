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
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

@ExtendWith(ScanningQuotaSearchExtension.class)
class DomainQuotaRoutesTest {

    private static final String QUOTA_DOMAINS = "/quota/domains";
    private static final String LOST_LOCAL = "lost.local";
    private static final Domain FOUND_LOCAL = Domain.of("found.local");
    private static final String COUNT = "count";
    private static final String SIZE = "size";
    private MaxQuotaManager maxQuotaManager;

    @BeforeEach
    void setUp(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getQuotaSearchTestSystem().getDomainList()
            .addDomain(FOUND_LOCAL);

        maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

        RestAssured.requestSpecification = testSystem.getRequestSpecification();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void getCountShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .get(QUOTA_DOMAINS + "/" + LOST_LOCAL + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getCountShouldReturnNoContentByDefault() {
        given()
            .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getCountShouldReturnStoredValue() throws Exception {
        int value = 42;
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(value));

        Long actual =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value);
    }

    @Test
    void putCountShouldReturnNotFoundWhenDomainDoesntExist() {
        given()
            .body("123")
        .when()
            .put(QUOTA_DOMAINS + "/" + LOST_LOCAL + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putCountShouldRejectInvalid() {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
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
    void putCountShouldSetToInfiniteWhenMinusOne() {
        given()
            .body("-1")
        .when()
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).contains(QuotaCountLimit.unlimited());
    }

    @Test
    void putCountShouldRejectNegativeOtherThanMinusOne() {
        Map<String, Object> errors = given()
            .body("-2")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
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
    void putCountShouldAcceptValidValue() {
        given()
            .body("42")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).contains(QuotaCountLimit.count(42));
    }

    @Test
    void deleteCountShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .delete(QUOTA_DOMAINS + "/" + LOST_LOCAL + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void deleteCountShouldSetQuotaToEmpty() throws Exception {
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(42));

        given()
            .delete(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).isEmpty();
    }

    @Test
    void getSizeShouldReturnNotFoundWhenDomainDoesntExist() {
            when()
                .get(QUOTA_DOMAINS + "/" + LOST_LOCAL + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getSizeShouldReturnNoContentByDefault() {
        when()
            .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getSizeShouldReturnStoredValue() throws Exception {
        long value = 42;
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(value));

        long quota =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(quota).isEqualTo(value);
    }

    @Test
    void putSizeShouldRejectInvalid() {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
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
    void putSizeShouldReturnNotFoundWhenDomainDoesntExist() {
        given()
            .body("123")
        .when()
            .put(QUOTA_DOMAINS + "/" + LOST_LOCAL + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putSizeShouldSetToInfiniteWhenMinusOne() {
        given()
            .body("-1")
        .when()
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).contains(QuotaSizeLimit.unlimited());
    }

    @Test
    void putSizeShouldRejectNegativeOtherThanMinusOne() {
        Map<String, Object> errors = given()
            .body("-2")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
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
    void putSizeShouldAcceptValidValue() {
        given()
            .body("42")
        .when()
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).contains(QuotaSizeLimit.size(42));
    }

    @Test
    void deleteSizeShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .delete(QUOTA_DOMAINS + "/" + LOST_LOCAL + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void deleteSizeShouldSetQuotaToEmpty() throws Exception {
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(42));

        given()
            .delete(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).isEmpty();
    }

    @Test
    void getQuotaShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .get(QUOTA_DOMAINS + "/" + LOST_LOCAL)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getQuotaShouldReturnBothEmptyWhenDefaultValues() {
        JsonPath jsonPath =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getObject(SIZE, Long.class)).isNull();
        assertThat(jsonPath.getObject(COUNT, Long.class)).isNull();
    }

    @Test
    void getQuotaShouldReturnSizeWhenNoCount() throws Exception {
        int maxStorage = 42;
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(maxStorage));

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .asString();

        assertThatJson(json)
            .isEqualTo("{" +
                "\"global\":{\"count\":null,\"size\":null}," +
                "\"domain\":{\"count\":null,\"size\":42}," +
                "\"computed\":{\"count\":null,\"size\":42}" +
            "}");
    }

    @Test
    void getQuotaShouldReturnBothWhenNoSize() throws Exception {
        int maxMessage = 42;
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(maxMessage));

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .asString();

        assertThatJson(json)
            .isEqualTo("{" +
                "\"global\":{\"count\":null,\"size\":null}," +
                "\"domain\":{\"count\":42,\"size\":null}," +
                "\"computed\":{\"count\":42,\"size\":null}" +
            "}");
    }

    @Test
    void getQuotaShouldDisplayScopesWhenUnlimited() throws Exception {
        int maxMessage = 42;
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.unlimited());
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(42));
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(maxMessage));
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.unlimited());

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .asString();

        assertThatJson(json)
            .isEqualTo("{" +
                "\"global\":{\"count\":-1,\"size\":42}," +
                "\"domain\":{\"count\":42,\"size\":-1}," +
                "\"computed\":{\"count\":42,\"size\":-1}" +
            "}");
    }

    @Test
    void getQuotaShouldDisplayScopedInformation() throws Exception {
        int maxMessage = 42;
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(maxMessage));
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(32));
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(36));

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .asString();

        assertThatJson(json)
            .isEqualTo("{" +
                "\"global\":{\"count\":32,\"size\":36}," +
                "\"domain\":{\"count\":42,\"size\":null}," +
                "\"computed\":{\"count\":42,\"size\":36}" +
            "}");
    }

    @Test
    void putQuotaShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .put(QUOTA_DOMAINS + "/" + LOST_LOCAL)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putQuotaShouldUpdateBothQuota() {
        given()
            .body("{\"count\":52,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).contains(QuotaCountLimit.count(52));
        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).contains(QuotaSizeLimit.size(42));
    }

    @Test
    void putQuotaWithNegativeCountShouldFail() throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(52));
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(42));
        Map<String, Object> errors = given()
            .body("{\"count\":-5,\"size\":30}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
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
    void putQuotaWithNegativeCountShouldNotUpdatePreviousQuota() throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(52));
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(42));
        given()
            .body("{\"count\":-5,\"size\":30}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON);

        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).contains(QuotaCountLimit.count(52));
        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).contains(QuotaSizeLimit.size(42));
    }

    @Test
    void putQuotaWithNegativeSizeShouldFail() throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(52));
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(42));
        Map<String, Object> errors = given()
            .body("{\"count\":40,\"size\":-19}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
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
    void putQuotaWithNegativeSizeShouldNotUpdatePreviousQuota() throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(FOUND_LOCAL, QuotaCountLimit.count(52));
        maxQuotaManager.setDomainMaxStorage(FOUND_LOCAL, QuotaSizeLimit.size(42));
        given()
            .body("{\"count\":40,\"size\":-19}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON);

        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).contains(QuotaCountLimit.count(52));
        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).contains(QuotaSizeLimit.size(42));
    }

    @Test
    void putQuotaShouldBeAbleToRemoveBothQuota() {
        given()
            .body("{\"count\":52,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);


        given()
            .body("{\"count\":null,\"size\":null}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).isEmpty();
        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).isEmpty();
    }

    @Test
    void putQuotaShouldBeAbleToRemoveCountQuota() {
        given()
            .body("{\"count\":52,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);


        given()
            .body("{\"count\":null,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).contains(QuotaSizeLimit.size(42));
        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).isEmpty();
    }

    @Test
    void putQuotaShouldBeAbleToRemoveSizeQuota() {
        given()
            .body("{\"count\":52,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);


        given()
            .body("{\"count\":52,\"size\":null}")
            .put(QUOTA_DOMAINS + "/" + FOUND_LOCAL.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(FOUND_LOCAL)).isEmpty();
        assertThat(maxQuotaManager.getDomainMaxMessage(FOUND_LOCAL)).contains(QuotaCountLimit.count(52));
    }


}
