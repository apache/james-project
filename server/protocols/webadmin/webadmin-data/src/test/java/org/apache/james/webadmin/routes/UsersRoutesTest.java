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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class UsersRoutesTest {

    private static final Domain DOMAIN = Domain.of("domain");
    private static final String USERNAME = "username@" + DOMAIN.name();
    private WebAdminServer webAdminServer;

    private void createServer(UsersRepository usersRepository) throws Exception {
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new UserRoutes(new UserService(usersRepository), new JsonTransformer()));
        webAdminServer.start();
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(UserRoutes.USERS)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Nested
    class NormalBehaviour {

        @BeforeEach
        void setUp() throws Exception {
            DomainList domainList = mock(DomainList.class);
            when(domainList.containsDomain(DOMAIN)).thenReturn(true);

            MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting();
            usersRepository.setDomainList(domainList);

            createServer(usersRepository);
        }

        @Test
        void getUsersShouldBeEmptyByDefault() {
            List<Map<String, String>> users =
                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(users).isEmpty();
        }

        @Test
        void putShouldReturnUserErrorWhenNoBody() {
            when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400);
        }

        @Test
        void postShouldReturnUserErrorWhenEmptyJsonBody() {
            given()
                .body("{}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400);
        }

        @Test
        void postShouldReturnUserErrorWhenWrongJsonBody() {
            given()
                .body("{\"bad\":\"any\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400);
        }

        @Test
        void postShouldReturnOkWhenValidJsonBody() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void postShouldReturnRequireNonNullPassword() {
            given()
                .body("{\"password\":null}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400);
        }

        @Test
        void postShouldAddTheUser() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME);

            List<Map<String, String>> users =
                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(users).containsExactly(ImmutableMap.of("username", USERNAME));
        }

        @Test
        void postingTwoTimesShouldBeAllowed() {
            // Given
            with()
                .body("{\"password\":\"password\"}")
            .put(USERNAME);

            // When
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            // Then
            List<Map<String, String>> users =
                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(users).containsExactly(ImmutableMap.of("username", USERNAME));
        }

        @Test
        void deleteShouldReturnOk() {
            when()
                .delete(USERNAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnBadRequestWhenEmptyUserName() {
            when()
                .delete("/")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteShouldReturnBadRequestWhenUsernameIsTooLong() {
            when()
                .delete(USERNAME + "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUsernameContainsSlash() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME + "/" + USERNAME)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putShouldReturnBadRequestWhenEmptyUserName() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put("/")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putShouldReturnBadRequestWhenUsernameIsTooLong() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME + "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400);
        }

        @Test
        void putShouldReturnNotFoundWhenUsernameContainsSlash() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME + "/" + USERNAME)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteShouldRemoveAssociatedUser() {
            // Given
            with()
                .body("{\"password\":\"password\"}")
            .put(USERNAME);

            // When
            when()
                .delete(USERNAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            // Then
            List<Map<String, String>> users =
                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(users).isEmpty();
        }

        @Test
        void deleteShouldStillBeValidWithExtraBody() {
            given()
                .body("{\"bad\":\"any\"}")
            .when()
                .delete(USERNAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }
    }

    @Nested
    class ErrorHandling {

        private UsersRepository usersRepository;
        private String username;
        private String password;

        @BeforeEach
        void setUp() throws Exception {
            usersRepository = mock(UsersRepository.class);
            createServer(usersRepository);
            username = "username@domain";
            password = "password";
        }

        @Test
        void deleteShouldStillBeOkWhenNoUser() throws Exception {
            doThrow(new UsersRepositoryException("message")).when(usersRepository).removeUser(username);

            when()
                .delete(USERNAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getShouldFailOnRepositoryException() throws Exception {
            when(usersRepository.list()).thenThrow(new UsersRepositoryException("message"));

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void postShouldFailOnRepositoryExceptionOnGetUserByName() throws Exception {
            when(usersRepository.getUserByName(username)).thenThrow(new UsersRepositoryException("message"));

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void postShouldNotFailOnRepositoryExceptionOnAddUser() throws Exception {
            when(usersRepository.getUserByName(username)).thenReturn(null);
            doThrow(new UsersRepositoryException("message")).when(usersRepository).addUser(username, password);

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.CONFLICT_409);
        }

        @Test
        void postShouldFailOnRepositoryExceptionOnUpdateUser() throws Exception {
            when(usersRepository.getUserByName(username)).thenReturn(mock(User.class));
            doThrow(new UsersRepositoryException("message")).when(usersRepository).updateUser(any());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.CONFLICT_409);
        }


        @Test
        void deleteShouldFailOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).removeUser(username);

            when()
                .delete(USERNAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldFailOnUnknownException() throws Exception {
            when(usersRepository.list()).thenThrow(new RuntimeException());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void postShouldFailOnUnknownExceptionOnGetUserByName() throws Exception {
            when(usersRepository.getUserByName(username)).thenThrow(new RuntimeException());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void postShouldFailOnUnknownExceptionOnAddUser() throws Exception {
            when(usersRepository.getUserByName(username)).thenReturn(null);
            doThrow(new RuntimeException()).when(usersRepository).addUser(username, password);

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void postShouldFailOnUnknownExceptionOnGetUpdateUser() throws Exception {
            when(usersRepository.getUserByName(username)).thenReturn(mock(User.class));
            doThrow(new RuntimeException()).when(usersRepository).updateUser(any());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

}
