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
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.droplists.memory.MemoryDropList;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class DropListRoutesTest {
    private static final String DENIED_SENDER = "attacker@evil.com";
    private static final String OWNER_RECIPIENT = "owner@owner.com";

    private WebAdminServer webAdminServer;
    private DropList dropList;

    @BeforeEach
    public void setUp() throws Exception {
        dropList = new MemoryDropList();
        DropListRoutes dropListRoutes = new DropListRoutes(dropList, new JsonTransformer());
        this.webAdminServer = WebAdminUtils.createWebAdminServer(dropListRoutes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        getDropListTestEntries().forEach(entry -> dropList.add(entry).block());
    }

    @AfterEach
    void tearDown() throws AddressException {
        webAdminServer.destroy();
        getDropListTestEntries().forEach(entry -> dropList.remove(entry).block());
    }

    @ParameterizedTest(name = "{index} Owner: {0}")
    @ValueSource(strings = {
        "global",
        "domain/owner.com",
        "user/owner@owner.com"})
    void shouldGetFullDropList(String pathParam) {
        when()
            .get(DropListRoutes.DROP_LIST + SEPARATOR + pathParam)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", containsInAnyOrder("attacker@evil.com", "evil.com"));
    }

    @ParameterizedTest(name = "{index} Owner: {0}")
    @ValueSource(strings = {
        "unknown",
        "unknown/owner.com",
        "unknown/owner@owner.com"})
    void shouldHandleWhenGetDropListWithInvalidOwnerScope(String pathParam) {
        when()
            .get(DropListRoutes.DROP_LIST + SEPARATOR + pathParam)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid OwnerScope"))
            .body("details", is("OwnerScope 'unknown' is invalid. Supported values are [GLOBAL, DOMAIN, USER]"));
    }

    @ParameterizedTest(name = "{index} Owner: {0}, DeniedEntityType: {1}")
    @CsvSource(value = {
        "global, domain, evil.com",
        "global, address, attacker@evil.com",
        "domain/owner.com, domain, evil.com",
        "domain/owner.com, address, attacker@evil.com",
        "user/owner@owner.com, domain, evil.com",
        "user/owner@owner.com, address, attacker@evil.com"})
    void shouldGetDropListWithQueryParams(String pathParam, String queryParam, String expected) {
        given()
            .queryParam("deniedEntityType", queryParam)
        .when()
            .get(DropListRoutes.DROP_LIST + SEPARATOR + pathParam)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", containsInAnyOrder(expected));
    }

    @Test
    void shouldHandleInvalidDeniedEntityType() {
        given()
            .queryParam("deniedEntityType", "unknown")
        .when()
            .get(DropListRoutes.DROP_LIST + SEPARATOR + "global")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid DeniedEntityType"))
            .body("details", is("DeniedEntityType 'unknown' is invalid. Supported values are [ADDRESS, DOMAIN]"));
    }

    @ParameterizedTest(name = "{index} OwnerScope: {0}, Owner: {1}, DeniedEntity: {2}")
    @CsvSource(value = {
        "global, , devil.com",
        "global, , bad_guy@crime.com",
        "domain, owner.com, devil.com",
        "domain, owner.com, bad_guy@crime.com",
        "user, owner@owner.com, devil.com",
        "user, owner@owner.com, bad_guy@crime.com"})
    void shouldAddDropListEntry(String ownerScope, String owner, String newDeniedEntity) {
        when()
            .put(DropListRoutes.DROP_LIST + SEPARATOR + ownerScope + SEPARATOR + owner + SEPARATOR + newDeniedEntity)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(getResultDropList(ownerScope, owner)).contains(newDeniedEntity);
    }

    @ParameterizedTest(name = "{index} OwnerScope: {0}, Owner: {1}, DeniedEntity: {2}")
    @CsvSource(value = {
        "global, , devil..com, Invalid domain devil..com",
        "global, , bad_guy@@crime.com, Invalid mail address bad_guy@@crime.com",
        "domain, owner.com, devil..com, Invalid domain devil..com",
        "domain, owner.com, bad_guy@@crime.com, Invalid mail address bad_guy@@crime.com",
        "user, owner@owner.com, devil..com, Invalid domain devil..com",
        "user, owner@owner.com, bad_guy@@crime.com, Invalid mail address bad_guy@@crime.com"})
    void shouldFailWhenAddInvalidDeniedEntity(String ownerScope, String owner, String newDeniedEntity, String message) {
        when()
            .put(DropListRoutes.DROP_LIST + SEPARATOR + ownerScope + SEPARATOR + owner + SEPARATOR + newDeniedEntity)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is(message));
    }

    @ParameterizedTest(name = "{index} Path: {0}")
    @CsvSource(value = {
        "/global/evil.com",
        "/global/attacker@evil.com",
        "/domain/owner.com/evil.com",
        "/domain/owner.com/attacker@evil.com",
        "/user/owner@owner.com/evil.com",
        "/user/owner@owner.com/attacker@evil.com"})
    void headShouldReturnNoContentWhenDomainDeniedEntityExists(String path) {
        when()
            .head(DropListRoutes.DROP_LIST + path)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @ParameterizedTest(name = "{index} Path: {0}")
    @CsvSource(value = {
        "global/devil.com",
        "global/bad_guy@crime.com",
        "/domain/owner.com/devil.com",
        "/domain/owner.com/bad_guy@crime.com",
        "/user/owner@owner.com/devil.com",
        "/user/owner@owner.com/bad_guy@crime.com"})
    void headShouldReturnNotFoundWhenDomainDeniedEntityNotExists(String path) {
        when()
            .head(DropListRoutes.DROP_LIST + path)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @ParameterizedTest(name = "{index} Path: {3}")
    @CsvSource(value = {
        "global, , evil.com, /global/evil.com",
        "global, , attacker@evil.com, /global/attacker@evil.com",
        "domain, owner.com, evil.com, /domain/owner.com/evil.com",
        "domain, owner.com, attacker@evil.com, /domain/owner.com/attacker@evil.com",
        "user, owner@owner.com, evil.com, /user/owner@owner.com/evil.com",
        "user, owner@owner.com, attacker@evil.com, /user/owner@owner.com/attacker@evil.com"})
    void deleteShouldReturnNoContent(String ownerScope, String owner, String deniedEntity, String path) {
        given()
            .delete(DropListRoutes.DROP_LIST + path)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(getResultDropList(ownerScope, owner)).doesNotContain(deniedEntity);
    }

    @Test
    void deleteShouldReturnNotFoundWhenUsedWithEmptyEntry() {
        given()
            .delete(SEPARATOR)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    static Stream<DropListEntry> getDropListTestEntries() throws AddressException {
        return Stream.of(
            DropListEntry.builder()
                .forAll()
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .forAll()
                .denyDomain(new MailAddress(DENIED_SENDER).getDomain())
                .build(),
            DropListEntry.builder()
                .forAll()
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .domainOwner(new MailAddress(OWNER_RECIPIENT).getDomain())
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .domainOwner(new MailAddress(OWNER_RECIPIENT).getDomain())
                .denyDomain(new MailAddress(DENIED_SENDER).getDomain())
                .build(),
            DropListEntry.builder()
                .userOwner(new MailAddress(OWNER_RECIPIENT))
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .userOwner(new MailAddress(OWNER_RECIPIENT))
                .denyDomain(new MailAddress(DENIED_SENDER).getDomain())
                .build());
    }

    private List<String> getResultDropList(String ownerScope, String owner) {
        return dropList.list(OwnerScope.valueOf(ownerScope.toUpperCase()),
                Optional.ofNullable(owner).orElse(""))
            .map(DropListEntry::getDeniedEntity)
            .collectList()
            .block();
    }
}