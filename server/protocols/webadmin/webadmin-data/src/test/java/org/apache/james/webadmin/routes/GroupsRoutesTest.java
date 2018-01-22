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
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.LogDetail;
import com.jayway.restassured.http.ContentType;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

@RunWith(HierarchicalContextRunner.class)
public class GroupsRoutesTest {

    private static final String DOMAIN = "b.com";
    private static final String GROUP1 = "group1" + "@" + DOMAIN;
    private static final String GROUP2 = "group2" + "@" + DOMAIN;
    private static final String GROUP_WITH_SLASH = "group10/10" + "@" + DOMAIN;
    private static final String GROUP_WITH_ENCODED_SLASH = "group10%2F10" + "@" + DOMAIN;
    private static final String USER_A = "a" + "@" + DOMAIN;
    private static final String USER_B = "b" + "@" + DOMAIN;
    private static final String USER_WITH_SLASH = "user/@" + DOMAIN;
    private static final String USER_WITH_ENCODED_SLASH = "user%2F@" + DOMAIN;

    private WebAdminServer webAdminServer;

    private void createServer(GroupsRoutes groupsRoutes) throws Exception {
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            groupsRoutes);
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.defineRequestSpecification(webAdminServer)
            .setBasePath(GroupsRoutes.ROOT_PATH)
            .log(LogDetail.ALL)
            .build();
    }

    @After
    public void stop() {
        webAdminServer.destroy();
    }

    public class NormalBehaviour {

        MemoryUsersRepository usersRepository;
        MemoryDomainList domainList;
        MemoryRecipientRewriteTable memoryRecipientRewriteTable;

        @Before
        public void setUp() throws Exception {
            memoryRecipientRewriteTable = new MemoryRecipientRewriteTable();
            DNSService dnsService = mock(DNSService.class);
            domainList = new MemoryDomainList(dnsService);
            domainList.addDomain(DOMAIN);
            usersRepository = MemoryUsersRepository.withVirtualHosting();
            usersRepository.setDomainList(domainList);
            createServer(new GroupsRoutes(memoryRecipientRewriteTable, usersRepository, domainList, new JsonTransformer()));
        }

        @Test
        public void getGroupsShouldBeEmpty() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("[]"));
        }

        @Test
        public void getGroupsShouldListExistingGroupsInOrder() {
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
        public void getUnregisteredGroupShouldReturnNotFound() {
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
        public void putUserInGroupShouldReturnCreated() {
            when()
                .put(GROUP1 + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.CREATED_201);
        }

        @Test
        public void putUserWithSlashInGroupShouldReturnCreated() {
            when()
                .put(GROUP1 + SEPARATOR + USER_WITH_ENCODED_SLASH)
            .then()
                .statusCode(HttpStatus.CREATED_201);
        }

        @Test
        public void putUserWithSlashInGroupShouldCreateUser() {
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
        public void putUserInGroupShouldCreateGroup() {
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
        public void putUserInGroupWithEncodedSlashShouldReturnCreated() {
            when()
                .put(GROUP_WITH_ENCODED_SLASH + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.CREATED_201);
        }

        @Test
        public void putUserInGroupWithEncodedSlashShouldCreateGroup() {
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
        public void putSameUserInGroupTwiceShouldBeIdempotent() {
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
        public void putUserInGroupShouldAllowSeveralUsers() {
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
        public void putUserInGroupShouldNotAllowGroupOnUnregisteredDomain() throws UsersRepositoryException, DomainListException {
            Map<String, Object> errors = when()
                .put("group@unregisteredDomain" + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.FORBIDDEN_403)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.FORBIDDEN_403)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Server doesn't own the domain: unregisteredDomain");
        }


        @Test
        public void putUserInGroupShouldNotAllowUserShadowing() throws UsersRepositoryException, DomainListException {
            usersRepository.addUser(USER_A, "whatever");

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
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Requested group address is already used for another purpose");
        }

        @Test
        public void getGroupShouldReturnMembersInOrder() {
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
        public void deleteUserNotInGroupShouldReturnOK() {
            when()
                .delete(GROUP1 + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.OK_200);
        }

        @Test
        public void deleteLastUserInGroupShouldDeleteGroup() {
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

    public class FilteringOtherRewriteRuleTypes extends NormalBehaviour {

        @Before
        public void setup() throws Exception {
            super.setUp();
            memoryRecipientRewriteTable.addErrorMapping("error", DOMAIN, "disabled");
            memoryRecipientRewriteTable.addRegexMapping("regex", DOMAIN, ".*@b\\.com");
            memoryRecipientRewriteTable.addAliasDomainMapping("alias", DOMAIN);

        }

    }

    public class ExceptionHandling {

        private RecipientRewriteTable memoryRecipientRewriteTable;

        @Before
        public void setUp() throws Exception {
            memoryRecipientRewriteTable = mock(RecipientRewriteTable.class);
            UsersRepository userRepository = mock(UsersRepository.class);
            DomainList domainList = mock(DomainList.class);
            Mockito.when(domainList.containsDomain(anyString())).thenReturn(true);
            createServer(new GroupsRoutes(memoryRecipientRewriteTable, userRepository, domainList, new JsonTransformer()));
        }

        @Test
        public void getMalformedGroupShouldReturnBadRequest() {
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
                .containsEntry("cause", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        public void putMalformedGroupShouldReturnBadRequest() {
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
                .containsEntry("cause", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        public void putUserInGroupWithSlashShouldReturnNotFound() {
            when()
                .put(GROUP_WITH_SLASH + SEPARATOR + USER_A)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body(containsString("404 Not found"));
        }

        @Test
        public void putUserWithSlashInGroupShouldReturnNotFound() {
            when()
                .put(GROUP1 + SEPARATOR + USER_WITH_SLASH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body(containsString("404 Not found"));
        }

        @Test
        public void putMalformedAddressShouldReturnBadRequest() {
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
                .containsEntry("message", "The group is not an email address")
                .containsEntry("cause", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        public void putRequiresTwoPathParams() {
            when()
                .put(GROUP1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body(is(""));
        }

        @Test
        public void deleteMalformedGroupShouldReturnBadRequest() {
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
                .containsEntry("cause", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        public void deleteMalformedAddressShouldReturnBadRequest() {
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
                .containsEntry("message", "The group is not an email address")
                .containsEntry("cause", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        public void deleteRequiresTwoPathParams() {
            when()
                .delete(GROUP1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body(is(""));
        }

        @Test
        public void putShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .addAddressMapping(anyString(), anyString(), anyString());

            when()
                .put(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void putShouldReturnErrorWhenErrorMappingExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTable.ErrorMappingException.class)
                .when(memoryRecipientRewriteTable)
                .addAddressMapping(anyString(), anyString(), anyString());

            when()
                .put(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void putShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .addAddressMapping(anyString(), anyString(), anyString());

            when()
                .put(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getAllShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .getAllMappings();

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getAllShouldReturnErrorWhenErrorMappingExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTable.ErrorMappingException.class)
                .when(memoryRecipientRewriteTable)
                .getAllMappings();

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getAllShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .getAllMappings();

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .removeAddressMapping(anyString(), anyString(), anyString());

            when()
                .delete(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldReturnErrorWhenErrorMappingExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTable.ErrorMappingException.class)
                .when(memoryRecipientRewriteTable)
                .removeAddressMapping(anyString(), anyString(), anyString());

            when()
                .delete(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .removeAddressMapping(anyString(), anyString(), anyString());

            when()
                .delete(GROUP1 + SEPARATOR + GROUP2)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .getMappings(anyString(), anyString());

            when()
                .get(GROUP1)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getShouldReturnErrorWhenErrorMappingExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTable.ErrorMappingException.class)
                .when(memoryRecipientRewriteTable)
                .getMappings(anyString(), anyString());

            when()
                .get(GROUP1)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .getMappings(anyString(), anyString());

            when()
                .get(GROUP1)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }
    }

}
