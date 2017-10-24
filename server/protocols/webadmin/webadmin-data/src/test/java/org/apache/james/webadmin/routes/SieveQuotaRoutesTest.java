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
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.apache.james.webadmin.routes.SieveQuotaRoutes.ROOT_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public class SieveQuotaRoutesTest {

    private static final String USER_A = "userA";

    private WebAdminServer webAdminServer;
    private SieveQuotaRepository sieveRepository;

    @BeforeEach
    public void setUp() throws Exception {
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
    public void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    public void getGlobalSieveQuotaShouldReturn404WhenNoQuotaSet() {
        given()
            .get(SieveQuotaRoutes.ROOT_PATH)
        .then()
            .statusCode(404);
    }

    @Test
    public void getGlobalSieveQuotaShouldReturnStoredValue() throws Exception {
        long value = 1000L;
        sieveRepository.setQuota(value);

        long actual =
            given()
                .get(SieveQuotaRoutes.ROOT_PATH)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value);
    }

    @Test
    public void updateGlobalSieveQuotaShouldUpdateStoredValue() throws Exception {
        sieveRepository.setQuota(500L);
        long requiredSize = 1024L;

        given()
            .body(requiredSize)
            .put(SieveQuotaRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(sieveRepository.getQuota()).isEqualTo(requiredSize);
    }

    @Test
    public void updateGlobalSieveQuotaShouldReturn400WhenMalformedJSON() {
        given()
            .body("invalid")
            .put(SieveQuotaRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void updateGlobalSieveQuotaShouldReturn400WhenRequestedSizeNotPositiveInteger() {
        given()
            .body(-100L)
            .put(SieveQuotaRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void removeGlobalSieveQuotaShouldReturn404WhenNoQuotaSet() {
        given()
            .delete(SieveQuotaRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void removeGlobalSieveQuotaShouldRemoveGlobalSieveQuota() throws Exception {
        sieveRepository.setQuota(1024L);

        given()
            .delete(SieveQuotaRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void getPerUserQuotaShouldReturn404WhenNoQuotaSetForUser() {
        given()
            .get(ROOT_PATH + SEPARATOR + USER_A)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getPerUserSieveQuotaShouldReturnedStoredValue() throws Exception {
        long value = 1024L;
        sieveRepository.setQuota(USER_A, value);

        long actual =
            given()
                .get(ROOT_PATH + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value);
    }

    @Test
    public void updatePerUserSieveQuotaShouldUpdateStoredValue() throws Exception {
        sieveRepository.setQuota(USER_A, 500L);
        long requiredSize = 1024L;

        given()
            .body(requiredSize)
            .put(ROOT_PATH + SEPARATOR + USER_A)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(sieveRepository.getQuota(USER_A)).isEqualTo(requiredSize);
    }

    @Test
    public void updatePerUserSieveQuotaShouldReturn400WhenMalformedJSON() {
        given()
            .body("invalid")
            .put(ROOT_PATH + SEPARATOR + USER_A)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void updatePerUserSieveQuotaShouldReturn400WhenRequestedSizeNotPositiveInteger() {
        given()
            .body(-100L)
            .put(ROOT_PATH + SEPARATOR + USER_A)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void removePerUserSieveQuotaShouldReturn404WhenNoQuotaSetForUser() {
        given()
            .delete(ROOT_PATH + SEPARATOR + USER_A)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void removePerUserSieveQuotaShouldRemoveQuotaForUser() throws Exception {
        sieveRepository.setQuota(USER_A, 1024L);

        given()
            .delete(ROOT_PATH + SEPARATOR + USER_A)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }
}
