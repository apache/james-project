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
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.memory.InMemorySieveQuotaRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public class SieveQuotaRoutesTest {

    private static final User USER_A = User.fromUsername("userA");

    private WebAdminServer webAdminServer;
    private SieveQuotaRepository sieveRepository;

    @BeforeEach
    void setUp() throws Exception {
        sieveRepository = new InMemorySieveQuotaRepository();
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new DefaultMetricFactory(),
                new SieveQuotaRoutes(sieveRepository, new JsonTransformer()));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void getGlobalSieveQuotaShouldReturn204WhenNoQuotaSet() {
        given()
            .get("/sieve/quota/default")
        .then()
            .statusCode(204);
    }

    @Test
    void getGlobalSieveQuotaShouldReturnStoredValue() throws Exception {
        QuotaSize value = QuotaSize.size(1000L);
        sieveRepository.setDefaultQuota(value);

        long actual =
            given()
                .get("/sieve/quota/default")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value.asLong());
    }

    @Test
    void updateGlobalSieveQuotaShouldUpdateStoredValue() throws Exception {
        sieveRepository.setDefaultQuota(QuotaSize.size(500L));
        long requiredSize = 1024L;

        given()
            .body(requiredSize)
            .put("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(sieveRepository.getDefaultQuota().asLong()).isEqualTo(requiredSize);
    }

    @Test
    void updateGlobalSieveQuotaShouldReturn400WhenMalformedJSON() {
        given()
            .body("invalid")
            .put("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void updateGlobalSieveQuotaShouldReturn400WhenRequestedSizeNotPositiveInteger() {
        given()
            .body(-100L)
            .put("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void removeGlobalSieveQuotaShouldReturn204WhenNoQuotaSet() {
        given()
            .delete("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removeGlobalSieveQuotaShouldRemoveGlobalSieveQuota() throws Exception {
        sieveRepository.setDefaultQuota(QuotaSize.size(1024L));

        given()
            .delete("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getPerUserQuotaShouldReturn204WhenNoQuotaSetForUser() {
        given()
            .get("/sieve/quota/users/" + USER_A.asString())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getPerUserSieveQuotaShouldReturnStoredValue() throws Exception {
        QuotaSize value = QuotaSize.size(1024L);
        sieveRepository.setQuota(USER_A, value);

        long actual =
            given()
                .get("/sieve/quota/users/" + USER_A.asString())
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value.asLong());
    }

    @Test
    void updatePerUserSieveQuotaShouldUpdateStoredValue() throws Exception {
        sieveRepository.setQuota(USER_A, QuotaSize.size(500L));
        long requiredSize = 1024L;

        given()
            .body(requiredSize)
            .put("/sieve/quota/users/" + USER_A.asString())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(sieveRepository.getQuota(USER_A).asLong()).isEqualTo(requiredSize);
    }

    @Test
    void updatePerUserSieveQuotaShouldReturn400WhenMalformedJSON() {
        given()
            .body("invalid")
            .put("/sieve/quota/users/" + USER_A.asString())
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void updatePerUserSieveQuotaShouldReturn400WhenRequestedSizeNotPositiveInteger() {
        given()
            .body(-100L)
            .put("/sieve/quota/users/" + USER_A.asString())
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void removePerUserSieveQuotaShouldReturn204WhenNoQuotaSetForUser() {
        given()
            .delete("/sieve/quota/users/" + USER_A.asString())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removePerUserSieveQuotaShouldRemoveQuotaForUser() throws Exception {
        sieveRepository.setQuota(USER_A, QuotaSize.size(1024));

        given()
            .delete("/sieve/quota/users/" + USER_A.asString())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }
}
