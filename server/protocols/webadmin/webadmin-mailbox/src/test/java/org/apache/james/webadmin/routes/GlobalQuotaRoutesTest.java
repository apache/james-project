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
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

@ExtendWith(ScanningQuotaSearchExtension.class)
class GlobalQuotaRoutesTest {
    private MaxQuotaManager maxQuotaManager;

    @BeforeEach
    void setUp(WebAdminQuotaSearchTestSystem testSystem) {
        RestAssured.requestSpecification = testSystem.getRequestSpecification();

        maxQuotaManager = testSystem.getQuotaSearchTestSystem().getMaxQuotaManager();
    }

    @Test
    void getQuotaCountShouldReturnNoContentWhenUndefined() {
        when()
            .get("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getCountShouldReturnStoredValue() throws Exception {
        int value = 42;
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(value));

        Long actual =
            when()
                .get("/quota/count")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value);
    }

    @Test
    void putCountShouldRejectInvalid() {
        Map<String, Object> errors = given()
            .body("invalid")
        .when()
            .put("/quota/count")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        SoftAssertions.assertSoftly(softly ->
            softly.assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
                .containsEntry("details", "For input string: \"invalid\""));
    }

    @Test
    void putCountShouldRejectNegative() {
        Map<String, Object> errors = given()
            .body("-2")
        .when()
            .put("/quota/count")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        SoftAssertions.assertSoftly(softly ->
            softly
                .assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1"));
    }

    @Test
    void putCountShouldHandleMinusOneAsInfinite() throws Exception {
        given()
            .body("-1")
        .when()
            .put("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCountLimit.unlimited());
    }

    @Test
    void putCountShouldAcceptValidValue() throws Exception {
        given()
            .body("42")
        .when()
            .put("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCountLimit.count(42));
    }

    @Test
    void deleteCountShouldSetQuotaToUnlimited() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));

        when()
            .delete("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    void getQuotaSizeShouldReturnNothingByDefault() {
        when()
            .get("/quota/size")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getSizeShouldReturnStoredValue() throws Exception {
        long value = 42;
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(value));


        long quota =
            when()
                .get("/quota/size")
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
        .when()
            .put("/quota/size")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        SoftAssertions.assertSoftly(softly ->
            softly
                .assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
                .containsEntry("details", "For input string: \"invalid\""));
    }

    @Test
    void putSizeShouldHandleMinusOneAsInfinite() throws Exception {
        given()
            .body("-1")
        .when()
            .put("/quota/size")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSizeLimit.unlimited());
    }

    @Test
    void putSizeShouldRejectNegative() {
        Map<String, Object> errors = given()
            .body("-2")
        .when()
            .put("/quota/size")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        SoftAssertions.assertSoftly(softly ->
            softly
                .assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1"));
    }

    @Test
    void putSizeShouldAcceptValidValue() throws Exception {
        given()
            .body("42")
        .when()
            .put("/quota/size")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSizeLimit.size(42));
    }

    @Test
    void deleteSizeShouldSetQuotaToUnlimited() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(42));

        when()
            .delete("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    void getQuotaShouldReturnBothWhenValueSpecified() throws Exception {
        int maxStorage = 42;
        int maxMessage = 52;
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(maxStorage));
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(maxMessage));

        JsonPath jsonPath =
            when()
                .get("/quota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jsonPath.getLong("size")).isEqualTo(maxStorage);
            softly.assertThat(jsonPath.getLong("count")).isEqualTo(maxMessage);
        });
    }

    @Test
    void getQuotaShouldReturnNothingWhenNothingSet() {
        JsonPath jsonPath =
            when()
                .get("/quota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jsonPath.getObject("size", Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("count", Long.class)).isNull();
        });
    }

    @Test
    void getQuotaShouldReturnOnlySizeWhenNoCount() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(42));

        JsonPath jsonPath =
            when()
                .get("/quota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jsonPath.getLong("size")).isEqualTo(42);
            softly.assertThat(jsonPath.getObject("count", Long.class)).isNull();
        });
    }

    @Test
    void getQuotaShouldReturnOnlyCountWhenNoSize() throws Exception {
        int maxMessage = 42;
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(maxMessage));


        JsonPath jsonPath =
            when()
                .get("/quota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jsonPath.getObject("size", Long.class)).isNull();
            softly.assertThat(jsonPath.getLong("count")).isEqualTo(maxMessage);
        });
    }

    @Test
    void putQuotaShouldUpdateBothQuota() throws Exception {
        given()
            .body("{\"count\":52,\"size\":42}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCountLimit.count(52));
        softly.assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSizeLimit.size(42));
        softly.assertAll();
    }

    @Test
    void putQuotaShouldSetBothQuotaToInfinite() throws Exception {
        given()
            .body("{\"count\":-1,\"size\":-1}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCountLimit.unlimited());
        softly.assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSizeLimit.unlimited());
        softly.assertAll();
    }

    @Test
    void putQuotaWithNegativeCountShouldFail() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(43));

        Map<String, Object> errors = given()
            .body("{\"count\":-2,\"size\":43}")
            .when()
            .put("/quota")
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
    void putQuotaWithNegativeCountShouldNotUpdatePreviousQuota() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(43));

        given()
            .body("{\"count\":-2,\"size\":43}")
            .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCountLimit.count(42));
        assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSizeLimit.size(43));
    }

    @Test
    void putQuotaWithNegativeSizeShouldFail() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(43));

        Map<String, Object> errors = given()
            .body("{\"count\":42,\"size\":-2}")
            .when()
            .put("/quota")
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
    void putQuotaWithNegativeSizeShouldNotUpdatePreviousQuota() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(43));

        given()
            .body("{\"count\":42,\"size\":-2}")
            .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCountLimit.count(42));
        assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSizeLimit.size(43));
    }

    @Test
    void putQuotaShouldUnsetCountWhenNull() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));
        given()
            .body("{\"count\":null,\"size\":43}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    void putQuotaShouldUnsetSizeWhenNull() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(44));
        given()
            .body("{\"count\":45,\"size\":null}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).isEmpty();
    }

    @Test
    void putQuotaShouldUnsetCountWhenAbsent() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(42));
        given()
            .body("{\"size\":43}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    void putQuotaShouldUnsetSizeWhenAbsent() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(44));
        given()
            .body("{\"count\":45}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).isEmpty();
    }

}
