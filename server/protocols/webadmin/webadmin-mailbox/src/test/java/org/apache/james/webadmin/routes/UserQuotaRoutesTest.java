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
import static org.apache.james.mailbox.model.Quota.UNLIMITED;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserQuotaService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;

public class UserQuotaRoutesTest {

    private static final String QUOTA_USERS = "/quota/users";
    private static final String PERDU_COM = "perdu.com";
    private static final String BOB = "bob@" + PERDU_COM;
    private static final String JOE = "joe@" + PERDU_COM;
    private static final String PASSWORD = "secret";
    private static final String COUNT = "count";
    private static final String SIZE = "size";
    private WebAdminServer webAdminServer;
    private InMemoryPerUserMaxQuotaManager maxQuotaManager;
    private MemoryUsersRepository usersRepository;

    @Before
    public void setUp() throws Exception {
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        MemoryDomainList memoryDomainList = new MemoryDomainList(new InMemoryDNSService());
        memoryDomainList.setAutoDetect(false);
        memoryDomainList.addDomain(PERDU_COM);
        usersRepository = MemoryUsersRepository.withVirtualHosting();
        usersRepository.setDomainList(memoryDomainList);
        usersRepository.addUser(BOB, PASSWORD);
        UserQuotaService userQuotaService = new UserQuotaService(maxQuotaManager);
        UserQuotaRoutes userQuotaRoutes = new UserQuotaRoutes(usersRepository, userQuotaService, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new NoopMetricFactory(),
            userQuotaRoutes);
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @After
    public void stop() {
        webAdminServer.destroy();
    }

    @Test
    public void getCountShouldReturnNotFoundWhenUserDoesntExist() {
        when()
            .get(QUOTA_USERS + "/" + JOE + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getCountShouldReturnUnlimitedByDefault() throws UsersRepositoryException {
        long quota =
            given()
                .get(QUOTA_USERS + "/" + BOB + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(quota).isEqualTo(UNLIMITED);
    }

    @Test
    public void getCountShouldReturnStoredValue() throws Exception {
        int value = 42;
        maxQuotaManager.setMaxMessage(QuotaRoot.forUser(BOB), value);

        Long actual =
            given()
                .get(QUOTA_USERS + "/" + BOB + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value);
    }

    @Test
    public void putCountShouldReturnNotFoundWhenUserDoesntExist() {
        given()
            .body("invalid")
        .when()
            .put(QUOTA_USERS + "/" + JOE + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }


    @Test
    public void putCountShouldRejectInvalid() throws Exception {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_USERS + "/" + BOB + "/" + COUNT)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater than 0")
            .containsEntry("cause", "For input string: \"invalid\"");
    }

    @Test
    public void putCountShouldRejectNegative() throws Exception {
        Map<String, Object> errors = given()
            .body("-1")
            .put(QUOTA_USERS + "/" + BOB + "/" + COUNT)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater than 0");
    }

    @Test
    public void putCountShouldAcceptValidValue() throws Exception {
        given()
            .body("42")
            .put(QUOTA_USERS + "/" + BOB + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxMessage(QuotaRoot.forUser(BOB))).isEqualTo(42);
    }


    @Test
    @Ignore("no link between quota and mailbox for now")
    public void putCountShouldRejectTooSmallValue() throws Exception {
        given()
            .body("42")
            .put(QUOTA_USERS + "/" + BOB + "/" + COUNT)
            .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxMessage(QuotaRoot.forUser(BOB))).isEqualTo(42);
    }

    @Test
    public void deleteCountShouldReturnNotFoundWhenUserDoesntExist() {
        when()
            .delete(QUOTA_USERS + "/" + JOE + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }


    @Test
    public void deleteCountShouldSetQuotaToUnlimited() throws Exception {
        maxQuotaManager.setMaxMessage(QuotaRoot.forUser(BOB), 42);

        given()
            .delete(QUOTA_USERS + "/" + BOB + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxMessage(QuotaRoot.forUser(BOB))).isEqualTo(UNLIMITED);
    }

    @Test
    public void getSizeShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .get(QUOTA_USERS + "/" + JOE + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getSizeShouldReturnUnlimitedByDefault() throws UsersRepositoryException {
        long quota =
            given()
                .get(QUOTA_USERS + "/" + BOB + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(quota).isEqualTo(UNLIMITED);
    }

    @Test
    public void getSizeShouldReturnStoredValue() throws Exception {
        long value = 42;
        maxQuotaManager.setMaxStorage(QuotaRoot.forUser(BOB), value);


        long quota =
            given()
                .get(QUOTA_USERS + "/" + BOB + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(quota).isEqualTo(value);
    }

    @Test
    public void putSizeShouldRejectInvalid() throws Exception {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_USERS + "/" + BOB + "/" + SIZE)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater than 0")
            .containsEntry("cause", "For input string: \"invalid\"");
    }

    @Test
    public void putSizeShouldReturnNotFoundWhenUserDoesntExist() throws Exception {
        given()
            .body("123")
        .when()
            .put(QUOTA_USERS + "/" + JOE + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void putSizeShouldRejectNegative() throws Exception {
        Map<String, Object> errors = given()
            .body("-1")
            .put(QUOTA_USERS + "/" + BOB + "/" + SIZE)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater than 0");
    }

    @Test
    public void putSizeShouldAcceptValidValue() throws Exception {
        given()
            .body("42")
            .put(QUOTA_USERS + "/" + BOB + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxStorage(QuotaRoot.forUser(BOB))).isEqualTo(42);
    }

    @Test
    public void deleteSizeShouldReturnNotFoundWhenUserDoesntExist() throws Exception {
        when()
            .delete(QUOTA_USERS + "/" + JOE + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void deleteSizeShouldSetQuotaToUnlimited() throws Exception {
        maxQuotaManager.setMaxStorage(QuotaRoot.forUser(BOB), 42);

        given()
            .delete(QUOTA_USERS + "/" + BOB + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxStorage(QuotaRoot.forUser(BOB))).isEqualTo(UNLIMITED);
    }

    @Test
    public void getQuotaShouldReturnNotFoundWhenUserDoesntExist() throws Exception {
        when()
            .get(QUOTA_USERS + "/" + JOE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getQuotaShouldReturnBothWhenValueSpecified() throws Exception {
        int maxStorage = 42;
        int maxMessage = 52;
        maxQuotaManager.setMaxStorage(QuotaRoot.forUser(BOB), maxStorage);
        maxQuotaManager.setMaxMessage(QuotaRoot.forUser(BOB), maxMessage);

        JsonPath jsonPath =
            given()
                .get(QUOTA_USERS + "/" + BOB)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getLong(SIZE)).isEqualTo(maxStorage);
        assertThat(jsonPath.getLong(COUNT)).isEqualTo(maxMessage);
    }

    @Test
    public void getQuotaShouldReturnBothDefaultValues() throws Exception {
        JsonPath jsonPath =
            given()
                .get(QUOTA_USERS + "/" + BOB)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getLong(SIZE)).isEqualTo(UNLIMITED);
        assertThat(jsonPath.getLong(COUNT)).isEqualTo(UNLIMITED);
    }

    @Test
    public void getQuotaShouldReturnBothWhenNoCount() throws Exception {
        int maxStorage = 42;
        maxQuotaManager.setMaxStorage(QuotaRoot.forUser(BOB), maxStorage);

        JsonPath jsonPath =
            given()
                .get(QUOTA_USERS + "/" + BOB)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getLong(SIZE)).isEqualTo(maxStorage);
        assertThat(jsonPath.getLong(COUNT)).isEqualTo(UNLIMITED);
    }

    @Test
    public void getQuotaShouldReturnBothWhenNoSize() throws Exception {
        int maxMessage = 42;
        maxQuotaManager.setMaxMessage(QuotaRoot.forUser(BOB), maxMessage);


        JsonPath jsonPath =
            given()
                .get(QUOTA_USERS + "/" + BOB)
                .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getLong(SIZE)).isEqualTo(UNLIMITED);
        assertThat(jsonPath.getLong(COUNT)).isEqualTo(maxMessage);
    }

    @Test
    public void putQuotaShouldReturnNotFoundWhenUserDoesntExist() throws Exception {
        when()
            .put(QUOTA_USERS + "/" + JOE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void putQuotaShouldUpdateBothQuota() throws Exception {
        given()
            .body("{\"" + COUNT + "\":52,\"" + SIZE + "\":42}")
            .put(QUOTA_USERS + "/" + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxMessage(QuotaRoot.forUser(BOB))).isEqualTo(52);
        assertThat(maxQuotaManager.getMaxStorage(QuotaRoot.forUser(BOB))).isEqualTo(42);
    }

    @Test
    public void putQuotaShouldBeAbleToRemoveBothQuota() throws Exception {
        given()
            .body("{\"" + COUNT + "\":-1,\"" + SIZE + "\":-1}")
            .put(QUOTA_USERS + "/" + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getMaxMessage(QuotaRoot.forUser(BOB))).isEqualTo(UNLIMITED);
        assertThat(maxQuotaManager.getMaxStorage(QuotaRoot.forUser(BOB))).isEqualTo(UNLIMITED);
    }

}
