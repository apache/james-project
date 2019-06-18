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
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.dto.MappingSourceModule;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;

class AliasRoutesTest {

    private static final Domain DOMAIN = Domain.of("b.com");
    private static final Domain ALIAS_DOMAIN = Domain.of("alias");
    public static final String BOB = "bob@" + DOMAIN.name();
    public static final String BOB_WITH_SLASH = "bob/@" + DOMAIN.name();
    public static final String BOB_WITH_ENCODED_SLASH = "bob%2F@" + DOMAIN.name();
    public static final String BOB_ALIAS = "bob-alias@" + DOMAIN.name();
    public static final String BOB_ALIAS_2 = "bob-alias2@" + DOMAIN.name();
    public static final String BOB_ALIAS_WITH_SLASH = "bob-alias/@" + DOMAIN.name();
    public static final String BOB_ALIAS_WITH_ENCODED_SLASH = "bob-alias%2F@" + DOMAIN.name();
    public static final String ALICE = "alice@" + DOMAIN.name();
    public static final String ALICE_ALIAS = "alice-alias@" + DOMAIN.name();
    public static final String BOB_PASSWORD = "123456";
    public static final String BOB_WITH_SLASH_PASSWORD = "abcdef";
    public static final String ALICE_PASSWORD = "789123";

    private WebAdminServer webAdminServer;

    private void createServer(AliasRoutes aliasRoutes) {
        webAdminServer = WebAdminUtils.createWebAdminServer(aliasRoutes)
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("address/aliases")
            .log(LogDetail.METHOD)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Nested
    class NormalBehaviour {

        MemoryUsersRepository usersRepository;
        MemoryDomainList domainList;
        MemoryRecipientRewriteTable memoryRecipientRewriteTable;

        @BeforeEach
        void setUp() throws Exception {
            memoryRecipientRewriteTable = new MemoryRecipientRewriteTable();
            DNSService dnsService = mock(DNSService.class);
            domainList = new MemoryDomainList(dnsService);
            domainList.configure(DomainListConfiguration.builder()
                .autoDetect(false)
                .autoDetectIp(false));
            domainList.addDomain(DOMAIN);
            domainList.addDomain(ALIAS_DOMAIN);
            MappingSourceModule module = new MappingSourceModule();
            memoryRecipientRewriteTable.setDomainList(domainList);

            usersRepository = MemoryUsersRepository.withVirtualHosting();
            usersRepository.setDomainList(domainList);
            usersRepository.configure(new DefaultConfigurationBuilder());

            usersRepository.addUser(BOB, BOB_PASSWORD);
            usersRepository.addUser(BOB_WITH_SLASH, BOB_WITH_SLASH_PASSWORD);
            usersRepository.addUser(ALICE, ALICE_PASSWORD);

            createServer(new AliasRoutes(memoryRecipientRewriteTable, usersRepository, new JsonTransformer(module)));
        }

        @Test
        void getAliasesShouldBeEmpty() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        void getAliasesShouldListExistingAliasesInAlphabeticOrder() {
            with()
                .put(ALICE + SEPARATOR + "sources" + SEPARATOR + ALICE_ALIAS);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            List<String> addresses =
                when()
                    .get()
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(ALICE, BOB);
        }

        @Test
        void getNotRegisteredAliasesShouldReturnEmptyList() {
            when()
                .get("unknown@domain.travel")
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        void getAliasesShouldReturnEmptyListWhenNoAliasMappings() {
            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        void putAliasForUserShouldReturnNoContent() {
            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putAliasShouldBeIdempotent() {
            given()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putAliasWithSlashForUserShouldReturnNoContent() {
            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_WITH_ENCODED_SLASH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserForAliasWithEncodedSlashShouldReturnNoContent() {
            when()
                .put(BOB_WITH_ENCODED_SLASH + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putExistingUserAsAliasSourceShouldNotBePossible() {
            Map<String, Object> errors = when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + ALICE)
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
                .containsEntry("message", "The alias source exists as an user already");
        }

        @Test
        void putSameSourceAndDestinationShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put(BOB_ALIAS + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
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
                .containsEntry("message", "Source and destination can't be the same!");
        }

        @Test
        void putAliasForUserShouldCreateAlias() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS));
        }

        @Test
        void putAliasWithEncodedSlashForUserShouldAddItAsADestination() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_WITH_ENCODED_SLASH);

            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS_WITH_SLASH));
        }

        @Test
        void putAliasForUserWithEncodedSlashShouldCreateAlias() {
            with()
                .put(BOB_WITH_ENCODED_SLASH + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get(BOB_WITH_ENCODED_SLASH)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS));
        }

        @Test
        void putSameAliasForUserTwiceShouldBeIdempotent() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS));
        }

        @Test
        void putAliasForUserShouldAllowSeveralSourcesAndReturnThemInAlphabeticalOrder() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_2);

            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS, BOB_ALIAS_2));
        }

        @Test
        void putAliasForUserShouldAllowSeveralDestinations() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .put(ALICE + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS));

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS));
        }

        @Test
        void putAliasForUserShouldNotRequireExistingBaseUser() {
            String notExistingAddress = "notFound@" + DOMAIN.name();

            with()
                .put(notExistingAddress + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get(notExistingAddress)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("source", hasItems(BOB_ALIAS));
        }

        @Test
        void deleteAliasNotInAliasesShouldReturnOK() {
            when()
                .delete(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteAliasInAliasesShouldDeleteAliasForUser() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .delete(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get(BOB_ALIAS)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        void deleteLastAliasOfUserInAliasesShouldDeleteUserFromAliasList() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .delete(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }
    }

    @Nested
    class FilteringOtherRewriteRuleTypes extends NormalBehaviour {

        @BeforeEach
        void setup() throws Exception {
            super.setUp();
            memoryRecipientRewriteTable.addErrorMapping(MappingSource.fromUser("error", DOMAIN), "disabled");
            memoryRecipientRewriteTable.addRegexMapping(MappingSource.fromUser("regex", DOMAIN), ".*@b\\.com");
            memoryRecipientRewriteTable.addAliasDomainMapping(MappingSource.fromDomain(ALIAS_DOMAIN), DOMAIN);
        }

    }

    @Nested
    class ExceptionHandling {

        private MemoryRecipientRewriteTable memoryRecipientRewriteTable;
        private DomainList domainList;

        @BeforeEach
        void setUp() throws Exception {
            memoryRecipientRewriteTable = spy(new MemoryRecipientRewriteTable());
            UsersRepository userRepository = mock(UsersRepository.class);
            domainList = mock(DomainList.class);
            memoryRecipientRewriteTable.setDomainList(domainList);
            Mockito.when(domainList.containsDomain(any())).thenReturn(true);
            createServer(new AliasRoutes(memoryRecipientRewriteTable, userRepository, new JsonTransformer()));
        }

        @Test
        void putAliasSourceContainingNotManagedDomainShouldReturnBadRequest() throws Exception {
            Mockito.when(domainList.containsDomain(any()))
                .thenReturn(false);

            Map<String, Object> errors = when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + "bob@not-managed-domain.tld")
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
                .containsEntry("message", "Source domain 'not-managed-domain.tld' is not managed by the domainList");
        }

        @Test
        void putMalformedUserDestinationShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put("not-an-address" + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
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
                .containsEntry("message", "The alias is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putMalformedAliasSourceShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + "not-an-address")
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
                .containsEntry("message", "The alias is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putUserDestinationInAliasWithSlashShouldReturnNotFound() {
            when()
                .put(BOB_WITH_SLASH + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putAliasSourceWithSlashShouldReturnNotFound() {
            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_WITH_SLASH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putRequiresTwoPathParams() {
            when()
                .put(BOB)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteMalformedUserDestinationShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .delete("not-an-address" + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
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
                .containsEntry("message", "The alias is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void deleteMalformedAliasShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .delete(BOB + SEPARATOR + "sources" + SEPARATOR + "not-an-address")
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
                .containsEntry("message", "The alias is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void deleteRequiresTwoPathParams() {
            when()
                .delete(BOB)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .addAliasMapping(any(), anyString());

            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .addAliasMapping(any(), anyString());

            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getAllShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .getMappingsForType(any());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getAllShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .getMappingsForType(any());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .removeAliasMapping(any(), anyString());

            when()
                .delete(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .removeAliasMapping(any(), anyString());

            when()
                .delete(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .listSources(any());

            when()
                .get(BOB)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .listSources(any());

            when()
                .get(BOB)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }
}
