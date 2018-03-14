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
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.service.GlobalQuotaService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;

public class GlobalQuotaRoutesTest {

    private WebAdminServer webAdminServer;
    private InMemoryPerUserMaxQuotaManager maxQuotaManager;

    @Before
    public void setUp() throws Exception {
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new GlobalQuotaRoutes(new GlobalQuotaService(maxQuotaManager), new JsonTransformer(new QuotaModule())));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
    }

    @After
    public void stop() {
        webAdminServer.destroy();
    }

    @Test
    public void getQuotaCountShouldReturnNoContentWhenUndefined() {
        when()
            .get("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void getCountShouldReturnStoredValue() throws Exception {
        int value = 42;
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(value));

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
    public void putCountShouldRejectInvalid() {
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
                .containsEntry("cause", "For input string: \"invalid\""));
    }

    @Test
    public void putCountShouldRejectNegative() {
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
    public void putCountShouldHandleMinusOneAsInfinite() throws MailboxException {
        given()
            .body("-1")
        .when()
            .put("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCount.unlimited());
    }


    @Test
    public void putCountShouldAcceptValidValue() throws Exception {
        given()
            .body("42")
        .when()
            .put("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCount.count(42));
    }

    @Test
    public void deleteCountShouldSetQuotaToUnlimited() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(42));

        when()
            .delete("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    public void getQuotaSizeShouldReturnNothingByDefault() {
        when()
            .get("/quota/size")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void getSizeShouldReturnStoredValue() throws Exception {
        long value = 42;
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(value));


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
    public void putSizeShouldRejectInvalid() {
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
                .containsEntry("cause", "For input string: \"invalid\""));
    }

    @Test
    public void putSizeShouldHandleMinusOneAsInfinite() throws MailboxException {
        given()
            .body("-1")
        .when()
            .put("/quota/size")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSize.unlimited());
    }

    @Test
    public void putSizeShouldRejectNegative() {
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
    public void putSizeShouldAcceptValidValue() throws Exception {
        given()
            .body("42")
        .when()
            .put("/quota/size")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSize.size(42));
    }

    @Test
    public void deleteSizeShouldSetQuotaToUnlimited() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(42));

        when()
            .delete("/quota/count")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    public void getQuotaShouldReturnBothWhenValueSpecified() throws Exception {
        int maxStorage = 42;
        int maxMessage = 52;
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(maxStorage));
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(maxMessage));

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
    public void getQuotaShouldReturnNothingWhenNothingSet() {
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
    public void getQuotaShouldReturnOnlySizeWhenNoCount() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(42));

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
    public void getQuotaShouldReturnOnlyCountWhenNoSize() throws Exception {
        int maxMessage = 42;
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(maxMessage));


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
    public void putQuotaShouldUpdateBothQuota() throws Exception {
        given()
            .body("{\"count\":52,\"size\":42}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCount.count(52));
        softly.assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSize.size(42));
    }

    @Test
    public void putQuotaShouldSetBothQuotaToInfinite() throws Exception {
        given()
            .body("{\"count\":-1,\"size\":-1}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(maxQuotaManager.getGlobalMaxMessage()).contains(QuotaCount.unlimited());
        softly.assertThat(maxQuotaManager.getGlobalMaxStorage()).contains(QuotaSize.unlimited());
    }

    @Test
    public void putQuotaShouldUnsetCountWhenNull() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(42));
        given()
            .body("{\"count\":null,\"size\":43}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    public void putQuotaShouldUnsetSizeWhenNull() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(44));
        given()
            .body("{\"count\":45,\"size\":null}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).isEmpty();
    }

    @Test
    public void putQuotaShouldUnsetCountWhenAbsent() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(42));
        given()
            .body("{\"size\":43}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
    }

    @Test
    public void putQuotaShouldUnsetSizeWhenAbsent() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(44));
        given()
            .body("{\"count\":45}")
        .when()
            .put("/quota")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getGlobalMaxStorage()).isEmpty();
    }

}
