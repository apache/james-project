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
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;

import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
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

class GroupsRoutesTest {

    private static final Domain DOMAIN = Domain.of("b.com");
    private static final Domain DOMAIN_MAPPING = Domain.of("mapping");
    private static final Domain ALIAS_DOMAIN = Domain.of("alias");
    private static final String GROUP1 = "group1" + "@" + DOMAIN.name();
    private static final String GROUP2 = "group2" + "@" + DOMAIN.name();
    private static final String GROUP_WITH_SLASH = "group10/10" + "@" + DOMAIN.name();
    private static final String GROUP_WITH_ENCODED_SLASH = "group10%2F10" + "@" + DOMAIN.name();
    private static final String USER_A = "a" + "@" + DOMAIN.name();
    private static final String USER_B = "b" + "@" + DOMAIN.name();
    private static final String USER_WITH_SLASH = "user/@" + DOMAIN.name();
    private static final String USER_WITH_ENCODED_SLASH = "user%2F@" + DOMAIN.name();

    private WebAdminServer webAdminServer;

    private void createServer(GroupsRoutes groupsRoutes, AddressMappingRoutes addressMappingRoutes) {
        webAdminServer = WebAdminUtils.createWebAdminServer(groupsRoutes, addressMappingRoutes)
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("address/groups")
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
            DNSService dnsService = mock(DNSService.class);
            domainList = new MemoryDomainList(dnsService);
            domainList.configure(DomainListConfiguration.DEFAULT);
            domainList.addDomain(DOMAIN);
            domainList.addDomain(ALIAS_DOMAIN);
            domainList.addDomain(DOMAIN_MAPPING);
            memoryRecipientRewriteTable.setDomainList(domainList);
            memoryRecipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
            usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
            MappingSourceModule mappingSourceModule = new MappingSourceModule();
            UserEntityValidator validator = UserEntityValidator.aggregate(
                new DefaultUserEntityValidator(usersRepository),
                new RecipientRewriteTableUserEntityValidator(memoryRecipientRewriteTable));
            memoryRecipientRewriteTable.setUserEntityValidator(validator);
            memoryRecipientRewriteTable.setUsersRepository(usersRepository);
            createServer(new GroupsRoutes(memoryRecipientRewriteTable, new JsonTransformer(mappingSourceModule)),
                new AddressMappingRoutes(memoryRecipientRewriteTable));
        }

        @Test
        void getGroupsShouldBeEmpty() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        void getShouldNotResolveRecurseGroups() throws Exception {
            when().put(GROUP1 + SEPARATOR + USER_A);

            memoryRecipientRewriteTable.addForwardMapping(MappingSource.fromUser(Username.of(USER_A)),
                "b@" + DOMAIN.name());

            List<String> addresses =
                when()
                    .get(GROUP1)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_A);
        }

        @Test
        void getGroupsShouldListExistingGroupsInAlphabeticOrder() {
            given()
                .put(GROUP2 + SEPARATOR + USER_A);

            given()
                .put(GROUP1 + SEPARATOR + USER_A);

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
            assertThat(addresses).containsExactly(GROUP1, GROUP2);
        }

        @Test
        void putShouldBeIdempotent() {
            with()
                .put(GROUP1 + SEPARATOR + USER_A);

            given()
                .put(GROUP1 + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getNotRegisteredGroupShouldReturnNotFound() {
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
                .containsEntry("message", "The group does not exist");
        }

        @Test
        void getGroupShouldReturnNotFoundWhenNonGroupMappings() {
            memoryRecipientRewriteTable.addMapping(
                MappingSource.fromDomain(DOMAIN),
                Mapping.domain(Domain.of("target.tld")));

            Map<String, Object> errors = when()
                .get(GROUP1)
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
                .containsEntry("message", "The group does not exist");
        }

        @Test
        void getGroupShouldReturnNotFoundWhenNoGroupMappings() {
            Map<String, Object> errors = when()
                .get(GROUP1)
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
                .containsEntry("message", "The group does not exist");
        }

        @Test
        void putUserInGroupShouldReturnNoContent() {
            when()
                .put(GROUP1 + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserWithSlashInGroupShouldReturnNoContent() {
            when()
                .put(GROUP1 + SEPARATOR + USER_WITH_ENCODED_SLASH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserWithSlashInGroupShouldCreateUser() {
            when()
                .put(GROUP1 + SEPARATOR + USER_WITH_ENCODED_SLASH);

            List<String> addresses =
                when()
                    .get(GROUP1)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_WITH_SLASH);
        }

        @Test
        void putUserInGroupShouldCreateGroup() {
            when()
                .put(GROUP1 + SEPARATOR + USER_A);

            List<String> addresses =
                when()
                    .get(GROUP1)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_A);
        }

        @Test
        void putUserInGroupWithEncodedSlashShouldReturnNoContent() {
            when()
                .put(GROUP_WITH_ENCODED_SLASH + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putShouldDetectLoops() {
            with().basePath(AddressMappingRoutes.BASE_PATH)
                .post(USER_A + "/targets/" + GROUP1);

            Map<String, Object> errors = when()
                .put(GROUP1 + SEPARATOR + USER_A)
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
                .containsEntry("message", "Creation of redirection of group1@b.com to group:a@b.com would lead to a loop, operation not performed");
        }

        @Test
        void putShouldNotConflictWithAlias() throws Exception {
            memoryRecipientRewriteTable.addAliasMapping(MappingSource.fromUser(Username.of(GROUP1)), USER_A);

            Map<String, Object> errors = when()
                .put(GROUP1 + SEPARATOR + USER_A)
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
                .containsEntry("message", "'group1@b.com' already have associated mappings: alias:a@b.com");
        }

        @Test
        void putUserInGroupWithEncodedSlashShouldCreateGroup() {
            when()
                .put(GROUP_WITH_ENCODED_SLASH + SEPARATOR + USER_A);

            List<String> addresses =
                when()
                    .get(GROUP_WITH_ENCODED_SLASH)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_A);
        }

        @Test
        void putSameUserInGroupTwiceShouldBeIdempotent() {
            given()
                .put(GROUP1 + SEPARATOR + USER_A);

            when()
                .put(GROUP1 + SEPARATOR + USER_A);

            List<String> addresses =
                when()
                    .get(GROUP1)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_A);
        }

        @Test
        void putUserInGroupShouldAllowSeveralUsers() {
            given()
                .put(GROUP1 + SEPARATOR + USER_A);

            given()
                .put(GROUP1 + SEPARATOR + USER_B);

            List<String> addresses =
                when()
                    .get(GROUP1)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_A, USER_B);
        }

        @Test
        void putUserInGroupShouldNotAllowUserShadowing() throws UsersRepositoryException {
            usersRepository.addUser(Username.of(USER_A), "whatever");

            Map<String, Object> errors = when()
                .put(USER_A + SEPARATOR + USER_B)
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.CONFLICT_409)
                .containsEntry("type", "WrongState")
                .containsEntry("message", "'a@b.com' user already exist");
        }

        @Test
        void getGroupShouldReturnMembersInAlphabeticOrder() {
            given()
                .put(GROUP1 + SEPARATOR + USER_B);

            given()
                .put(GROUP1 + SEPARATOR + USER_A);

            List<String> addresses =
                when()
                    .get(GROUP1)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");
            assertThat(addresses).containsExactly(USER_A, USER_B);
        }


        @Test
        void deleteUserNotInGroupShouldReturnOK() {
            when()
                .delete(GROUP1 + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteLastUserInGroupShouldDeleteGroup() {
            given()
                .put(GROUP1 + SEPARATOR + USER_A);

            given()
                .delete(GROUP1 + SEPARATOR + USER_A);

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
            domainList = mock(DomainList.class);
            memoryRecipientRewriteTable.setDomainList(domainList);
            Mockito.when(domainList.containsDomain(any())).thenReturn(true);
            memoryRecipientRewriteTable.setUserEntityValidator(UserEntityValidator.NOOP);
            memoryRecipientRewriteTable.setUsersRepository(userRepository);
            createServer(new GroupsRoutes(memoryRecipientRewriteTable, new JsonTransformer()),
                new AddressMappingRoutes(memoryRecipientRewriteTable));
        }

        @Test
        void getMalformedGroupShouldReturnBadRequest() {
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
                .containsEntry("message", "The group is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putMalformedGroupShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put("not-an-address" + SEPARATOR + USER_A)
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
                .containsEntry("message", "The group is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putASourceContainingANotManagedDomainShouldReturnBadRequest() throws Exception {
            doReturn(false)
                .when(domainList).containsDomain(any());

            Map<String, Object> errors = when()
                .put("userA@not-managed-domain.tld" + SEPARATOR + USER_A)
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
        void putUserInGroupWithSlashShouldReturnNotFound() {
            when()
                .put(GROUP_WITH_SLASH + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putUserWithSlashInGroupShouldReturnNotFound() {
            when()
                .put(GROUP1 + SEPARATOR + USER_WITH_SLASH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putMalformedAddressShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put(GROUP1 + SEPARATOR + "not-an-address")
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
                .containsEntry("message", "The group member is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putRequiresTwoPathParams() {
            when()
                .put(GROUP1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body(is(""));
        }

        @Test
        void deleteMalformedGroupShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .delete("not-an-address" + SEPARATOR + USER_A)
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
                .containsEntry("message", "The group is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void deleteMalformedAddressShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .delete(GROUP1 + SEPARATOR + "not-an-address")
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
                .containsEntry("message", "The group member is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void deleteRequiresTwoPathParams() {
            when()
                .delete(GROUP1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body(is(""));
        }

        @Test
        void putShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .addGroupMapping(any(), anyString());

            when()
                .put(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .addGroupMapping(any(), anyString());

            when()
                .put(GROUP1 + SEPARATOR + GROUP2)
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
                .removeGroupMapping(any(), anyString());

            when()
                .delete(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .removeGroupMapping(any(), anyString());

            when()
                .delete(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldReturnErrorWhenRuntimeExceptionIsThrown() {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .getStoredMappings(any());

            when()
                .get(GROUP1)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

}
