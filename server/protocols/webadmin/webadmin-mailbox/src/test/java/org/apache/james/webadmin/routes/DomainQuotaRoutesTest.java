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
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;

@ExtendWith(ScanningQuotaSearchExtension.class)
class DomainQuotaRoutesTest {

    private static final String QUOTA_DOMAINS = "/quota/domains";
    private static final String PERDU_COM = "perdu.com";
    private static final Domain TROUVÉ_COM = Domain.of("trouvé.com");
    private static final String COUNT = "count";
    private static final String SIZE = "size";
    private MaxQuotaManager maxQuotaManager;

    @BeforeEach
    void setUp(WebAdminQuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getQuotaSearchTestSystem().getDomainList()
            .addDomain(TROUVÉ_COM);

        maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();

        RestAssured.requestSpecification = testSystem.getRequestSpecification();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void getCountShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .get(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getCountShouldReturnNoContentByDefault() {
        given()
            .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getCountShouldReturnStoredValue() throws Exception {
        int value = 42;
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(value));

        Long actual =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
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
            .put(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putCountShouldRejectInvalid() {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
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
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).contains(QuotaCount.unlimited());
    }

    @Test
    void putCountShouldRejectNegativeOtherThanMinusOne() {
        Map<String, Object> errors = given()
            .body("-2")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
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
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).contains(QuotaCount.count(42));
    }

    @Test
    void deleteCountShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .delete(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void deleteCountShouldSetQuotaToEmpty() throws Exception {
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(42));

        given()
            .delete(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).isEmpty();
    }

    @Test
    void getSizeShouldReturnNotFoundWhenDomainDoesntExist() {
            when()
                .get(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getSizeShouldReturnNoContentByDefault() {
        when()
            .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getSizeShouldReturnStoredValue() throws Exception {
        long value = 42;
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.size(value));

        long quota =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
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
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
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
            .put(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putSizeShouldSetToInfiniteWhenMinusOne() {
        given()
            .body("-1")
        .when()
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).contains(QuotaSize.unlimited());
    }

    @Test
    void putSizeShouldRejectNegativeOtherThanMinusOne() {
        Map<String, Object> errors = given()
            .body("-2")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
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
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).contains(QuotaSize.size(42));
    }

    @Test
    void deleteSizeShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .delete(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void deleteSizeShouldSetQuotaToEmpty() throws Exception {
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.size(42));

        given()
            .delete(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name() + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).isEmpty();
    }

    @Test
    void getQuotaShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .get(QUOTA_DOMAINS + "/" + PERDU_COM)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getQuotaShouldReturnBothEmptyWhenDefaultValues() {
        JsonPath jsonPath =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
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
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.size(maxStorage));

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
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
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(maxMessage));

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
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
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.unlimited());
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(42));
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(maxMessage));
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.unlimited());

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
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
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(maxMessage));
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(32));
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(36));

        String json =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
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
            .put(QUOTA_DOMAINS + "/" + PERDU_COM)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putQuotaShouldUpdateBothQuota() {
        given()
            .body("{\"count\":52,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).contains(QuotaCount.count(52));
        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).contains(QuotaSize.size(42));
    }

    @Test
    void putQuotaShouldBeAbleToRemoveBothQuota() {
        given()
            .body("{\"count\":null,\"count\":null}")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM.name())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).isEmpty();
        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).isEmpty();
    }

}
