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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
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
import io.restassured.http.ContentType;

class ForwardRoutesTest {

    private static final Domain DOMAIN = Domain.of("b.com");
    private static final Domain ALIAS_DOMAIN = Domain.of("alias");
    private static final Domain DOMAIN_MAPPING = Domain.of("mapping");
    public static final String CEDRIC = "cedric@" + DOMAIN.name();
    public static final String ALICE = "alice@" + DOMAIN.name();
    public static final String ALICE_WITH_SLASH = "alice/@" + DOMAIN.name();
    public static final String ALICE_WITH_ENCODED_SLASH = "alice%2F@" + DOMAIN.name();
    public static final String BOB = "bob@" + DOMAIN.name();
    public static final String BOB_PASSWORD = "123456";
    public static final String ALICE_PASSWORD = "789123";
    public static final String ALICE_SLASH_PASSWORD = "abcdef";
    public static final String CEDRIC_PASSWORD = "456789";

    private WebAdminServer webAdminServer;

    private void createServer(ForwardRoutes forwardRoutes) {
        webAdminServer = WebAdminUtils.createWebAdminServer(forwardRoutes)
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("address/forwards")
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
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
            domainList = new MemoryDomainList();
            domainList.configure(DomainListConfiguration.DEFAULT);
            domainList.addDomain(DOMAIN);
            domainList.addDomain(ALIAS_DOMAIN);
            domainList.addDomain(DOMAIN_MAPPING);
            memoryRecipientRewriteTable.setDomainList(domainList);
            memoryRecipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
            MappingSourceModule mappingSourceModule = new MappingSourceModule();

            usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

            usersRepository.addUser(Username.of(BOB), BOB_PASSWORD);
            usersRepository.addUser(Username.of(ALICE), ALICE_PASSWORD);
            usersRepository.addUser(Username.of(ALICE_WITH_SLASH), ALICE_SLASH_PASSWORD);
            usersRepository.addUser(Username.of(CEDRIC), CEDRIC_PASSWORD);

            createServer(new ForwardRoutes(memoryRecipientRewriteTable, usersRepository, new JsonTransformer(mappingSourceModule)));
        }

        @Test
        void getForwardShouldBeEmpty() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        void getForwardShouldListExistingForwardsInAlphabeticOrder() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            with()
                .put(CEDRIC + SEPARATOR + "targets" + SEPARATOR + BOB);

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
            assertThat(addresses).containsExactly(ALICE, CEDRIC);
        }

        @Test
        void shouldSupportSubAddressing() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + URLEncoder.encode("bob+tag@" + DOMAIN.name(), StandardCharsets.UTF_8));

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems("bob+tag@" + DOMAIN.name()));
        }

        @Test
        void getShouldNotResolveRecurseForwards() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            with()
                .put(BOB + SEPARATOR + "targets" + SEPARATOR + CEDRIC);


            when()
                .get(ALICE)
                .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(BOB));
        }

        @Test
        void getNotRegisteredForwardShouldReturnNotFound() {
            Map<String, Object> errors = when()
                .get("unknown@domain.travel")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.NOT_FOUND_404)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The forward does not exist");
        }

        @Test
        void getForwardShouldReturnNotFoundWhenNonForwardMappings() {
            memoryRecipientRewriteTable.addMapping(
                MappingSource.fromDomain(DOMAIN),
                Mapping.domain(Domain.of("target.tld")));

            Map<String, Object> errors = when()
                .get(ALICE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.NOT_FOUND_404)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The forward does not exist");
        }

        @Test
        void getForwardShouldReturnNotFoundWhenNoForwardMappings() {
            Map<String, Object> errors = when()
                .get(ALICE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.NOT_FOUND_404)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The forward does not exist");
        }

        @Test
        void putUserInForwardShouldReturnNoContent() {
            when()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putShouldDetectLoops() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            Map<String, Object> errors = when()
                .put(BOB + SEPARATOR + "targets" + SEPARATOR + ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.CONFLICT_409)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.CONFLICT_409)
                .containsEntry("type", "WrongState")
                .containsEntry("message", "Creation of redirection of bob@b.com to forward:alice@b.com would lead to a loop, operation not performed");
        }

        @Test
        void putUserShouldBeIdempotent() {
            given()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            when()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserWithSlashInForwardShouldReturnNoContent() {
            when()
                .put(BOB + SEPARATOR + "targets" + SEPARATOR + ALICE_WITH_ENCODED_SLASH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserWithSlashInForwardShouldAddItAsADestination() {
            with()
                .put(BOB + SEPARATOR + "targets" + SEPARATOR + ALICE_WITH_ENCODED_SLASH);

            when()
                .get(BOB)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(ALICE_WITH_SLASH));
        }

        @Test
        void putUserInForwardShouldCreateForward() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(BOB));
        }

        @Test
        void putUserInForwardWithEncodedSlashShouldReturnNoContent() {
            when()
                .put(ALICE_WITH_ENCODED_SLASH + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserInForwardWithEncodedSlashShouldCreateForward() {
            with()
                .put(ALICE_WITH_ENCODED_SLASH + SEPARATOR + "targets" + SEPARATOR + BOB);

            when()
                .get(ALICE_WITH_ENCODED_SLASH)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(BOB));
        }

        @Test
        void putSameUserInForwardTwiceShouldBeIdempotent() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(BOB));
        }

        @Test
        void putUserInForwardShouldAllowSeveralDestinations() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + CEDRIC);

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(BOB, CEDRIC));
        }

        @Test
        void forwardShouldAllowIdentity() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + ALICE);

            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + CEDRIC);

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(ALICE, CEDRIC));
        }

        @Test
        void putUserInForwardShouldRequireExistingBaseUser() {
            Map<String, Object> errors = when()
                .put("notFound@" + DOMAIN.name() + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.NOT_FOUND_404)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Requested base forward address does not correspond to a user");
        }

        @Test
        void addForwardWithOneTimeUrlEncodedAddressShouldSucceed() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + URLEncoder.encode("alice+tag@james.org", StandardCharsets.UTF_8));

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems("alice+tag@james.org"));
        }

        @Test
        void getForwardShouldReturnMembersInAlphabeticOrder() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + CEDRIC);

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(BOB, CEDRIC));
        }

        @Test
        void forwardShouldAcceptExternalAddresses() {
            String externalAddress = "external@other.com";

            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + externalAddress);

            when()
                .get(ALICE)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body("mailAddress", hasItems(externalAddress));
        }

        @Test
        void deleteUserNotInForwardShouldReturnOK() {
            when()
                .delete(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteLastUserInForwardShouldDeleteForward() {
            with()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

            with()
                .delete(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB);

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
            memoryRecipientRewriteTable.addDomainMapping(MappingSource.fromDomain(DOMAIN_MAPPING), DOMAIN);
            memoryRecipientRewriteTable.addDomainAliasMapping(MappingSource.fromDomain(ALIAS_DOMAIN), DOMAIN);
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
            doReturn(true)
                .when(userRepository).contains(any());

            domainList = mock(DomainList.class);
            memoryRecipientRewriteTable.setDomainList(domainList);
            Mockito.when(domainList.containsDomain(any())).thenReturn(true);
            createServer(new ForwardRoutes(memoryRecipientRewriteTable, userRepository, new JsonTransformer()));
        }

        @Test
        void getMalformedForwardShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .get("not-an-address")
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
                .containsEntry("message", "The base forward is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putMalformedForwardShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put("not-an-address" + SEPARATOR + "targets" + SEPARATOR + BOB)
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
                .containsEntry("message", "The base forward is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putWithSourceDomainNotInDomainListShouldReturnBadRequest() throws Exception {
            doReturn(false)
                .when(domainList).containsDomain(any());

            Map<String, Object> errors = when()
                .put("bob@not-managed-domain.tld" + SEPARATOR + "targets" + SEPARATOR + BOB)
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
        void putUserInForwardWithSlashShouldReturnNotFound() {
            when()
                .put(ALICE_WITH_SLASH + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putUserWithSlashInForwardShouldReturnNotFound() {
            when()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + ALICE_WITH_SLASH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putMalformedAddressShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + "not-an-address")
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
                .containsEntry("message", "The target forward is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putRequiresTwoPathParams() {
            when()
                .put(ALICE)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is("InvalidArgument"))
                .body("message", is("A destination address needs to be specified in the path"));
        }

        @Test
        void deleteMalformedForwardShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .delete("not-an-address" + SEPARATOR + "targets" + SEPARATOR + ALICE)
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
                .containsEntry("message", "The base forward is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void deleteMalformedAddressShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .delete(ALICE + SEPARATOR + "targets" + SEPARATOR + "not-an-address")
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
                .containsEntry("message", "The target forward is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void deleteRequiresTwoPathParams() {
            when()
                .delete(ALICE)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is("InvalidArgument"))
                .body("message", is("A destination address needs to be specified in the path"));
        }

        @Test
        void putShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .addForwardMapping(any(), anyString());

            when()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .addForwardMapping(any(), anyString());

            when()
                .put(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getAllShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .getSourcesForType(any());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getAllShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .getSourcesForType(any());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .removeForwardMapping(any(), anyString());

            when()
                .delete(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .removeForwardMapping(any(), anyString());

            when()
                .delete(ALICE + SEPARATOR + "targets" + SEPARATOR + BOB)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .getStoredMappings(any());

            when()
                .get(ALICE)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

}
