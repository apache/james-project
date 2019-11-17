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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.memory.InMemorySieveQuotaRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class SieveQuotaRoutesTest {

    private static final String USER_NAME_A = "userA";
    private static final String PASSWORD_A = "123456";
    private static final Username USERNAME_A = Username.of(USER_NAME_A);
    private static final DomainList NO_DOMAIN_LIST = null;

    private WebAdminServer webAdminServer;
    private SieveQuotaRepository sieveRepository;

    @BeforeEach
    void setUp() throws UsersRepositoryException {
        sieveRepository = new InMemorySieveQuotaRepository();

        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        usersRepository.addUser(Username.of(USER_NAME_A), PASSWORD_A);

        webAdminServer = WebAdminUtils.createWebAdminServer(new SieveQuotaRoutes(sieveRepository, usersRepository, new JsonTransformer()))
            .start();

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
        QuotaSizeLimit value = QuotaSizeLimit.size(1000L);
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
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(500L));
        long requiredSize = 1024L;

        given()
            .body(requiredSize)
            .put("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(sieveRepository.getDefaultQuota().asLong()).isEqualTo(requiredSize);
    }

    @Test
    void updateGlobalSieveQuotaShouldReturn400WhenInvalidNumberFormatInTheBody() {
        given()
            .body("invalid")
            .put("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", is("unrecognized integer number 'invalid'"));
    }

    @Test
    void updateGlobalSieveQuotaShouldReturn400WhenInvalidIntegerFormatInTheBody() {
        given()
            .body("1900.999")
            .put("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", is("unrecognized integer number '1900.999'"));
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
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(1024L));

        given()
            .delete("/sieve/quota/default")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getPerUserQuotaShouldReturn204WhenNoQuotaSetForUser() {
        given()
            .get("/sieve/quota/users/" + USER_NAME_A)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void getPerUserSieveQuotaShouldReturnStoredValue() throws Exception {
        QuotaSizeLimit value = QuotaSizeLimit.size(1024L);
        sieveRepository.setQuota(USERNAME_A, value);

        long actual =
            given()
                .get("/sieve/quota/users/" + USER_NAME_A)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value.asLong());
    }

    @Test
    void getPerUserSieveQuotaShouldReturn404WhenUserDoesNotExist() {
        given()
            .get("/sieve/quota/users/not_exist")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void updatePerUserSieveQuotaShouldUpdateStoredValue() throws Exception {
        sieveRepository.setQuota(USERNAME_A, QuotaSizeLimit.size(500L));
        long requiredSize = 1024L;

        given()
            .body(requiredSize)
            .put("/sieve/quota/users/" + USER_NAME_A)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(sieveRepository.getQuota(USERNAME_A).asLong()).isEqualTo(requiredSize);
    }

    @Test
    void updatePerUserSieveQuotaShouldReturn400WhenInvalidNumberFormatInTheBody() {
        given()
            .body("invalid")
            .put("/sieve/quota/users/" + USER_NAME_A)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", is("unrecognized integer number 'invalid'"));
    }

    @Test
    void updatePerUserSieveQuotaShouldReturn400WhenInvalidIntegerFormatInTheBody() {
        given()
            .body("89884743.9999")
            .put("/sieve/quota/users/" + USERNAME_A.asString())
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", is("unrecognized integer number '89884743.9999'"));
    }

    @Test
    void updatePerUserSieveQuotaShouldReturn400WhenRequestedSizeNotPositiveInteger() {
        given()
            .body(-100L)
            .put("/sieve/quota/users/" + USER_NAME_A)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void updatePerUserSieveQuotaShouldReturn404WhenUserDoesNotExist() {
        given()
            .body(500L)
            .put("/sieve/quota/users/not_exist")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void removePerUserSieveQuotaShouldReturn204WhenNoQuotaSetForUser() {
        given()
            .delete("/sieve/quota/users/" + USER_NAME_A)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removePerUserSieveQuotaShouldRemoveQuotaForUser() throws Exception {
        sieveRepository.setQuota(USERNAME_A, QuotaSizeLimit.size(1024));

        given()
            .delete("/sieve/quota/users/" + USER_NAME_A)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removePerUserSieveQuotaShouldReturn404WhenUserDoesNotExist() {
        given()
            .delete("/sieve/quota/users/not_exist")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }
}
