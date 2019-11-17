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
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class UserRoutesTest {

    private static class UserRoutesExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

        static UserRoutesExtension withVirtualHosting() {
            SimpleDomainList domainList = new SimpleDomainList();
            return new UserRoutesExtension(MemoryUsersRepository.withVirtualHosting(domainList), domainList);
        }

        static UserRoutesExtension withoutVirtualHosting() {
            SimpleDomainList domainList = new SimpleDomainList();
            return new UserRoutesExtension(MemoryUsersRepository.withoutVirtualHosting(domainList), domainList);
        }

        final MemoryUsersRepository usersRepository;
        final SimpleDomainList domainList;

        WebAdminServer webAdminServer;

        UserRoutesExtension(MemoryUsersRepository usersRepository, SimpleDomainList domainList) {
            this.usersRepository = spy(usersRepository);
            this.domainList = domainList;
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            domainList.addDomain(DOMAIN);

            webAdminServer = startServer(usersRepository);
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) {
            webAdminServer.destroy();
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter()
                .getType()
                .isAssignableFrom(UsersRepository.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return usersRepository;
        }

        private WebAdminServer startServer(UsersRepository usersRepository) {
            WebAdminServer server = WebAdminUtils.createWebAdminServer(new UserRoutes(new UserService(usersRepository), new JsonTransformer()))
                .start();

            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server)
                .setBasePath(UserRoutes.USERS)
                .build();

            return server;
        }
    }

    interface UserRoutesContract {
        interface AllContracts extends NormalBehaviourContract, MockBehaviorErrorHandlingContract {
        }

        interface NormalBehaviourContract {

            @Test
            default void getUsersShouldBeEmptyByDefault() {
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
            default void putShouldReturnUserErrorWhenNoBody() {
                when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("JSON payload of the request is not valid"));
            }

            @Test
            default void postShouldReturnUserErrorWhenEmptyJsonBody() {
                given()
                    .body("{}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("JSON payload of the request is not valid"));
            }

            @Test
            default void putShouldReturnUserErrorWhenWrongJsonBody() {
                given()
                    .body("{\"bad\":\"any\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("JSON payload of the request is not valid"));
            }

            @Test
            default void putShouldReturnRequireNonNullPassword() {
                given()
                    .body("{\"password\":null}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("JSON payload of the request is not valid"));
            }

            @Test
            default void deleteShouldReturnOk() {
                when()
                    .delete(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.NO_CONTENT_204);
            }

            @Test
            default void deleteShouldReturnBadRequestWhenEmptyUserName() {
                when()
                    .delete("/")
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404);
            }

            @Test
            default void deleteShouldReturnBadRequestWhenUsernameIsTooLong() {
                when()
                    .delete(USERNAME_WITH_DOMAIN.asString() + "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                        "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                        "0123456789.0123456789.0123456789.")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("username length should not be longer than 255 characters"));
            }

            @Test
            default void deleteShouldReturnNotFoundWhenUsernameContainsSlash() {
                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString() + "/" + USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404);
            }

            @Test
            default void putShouldReturnBadRequestWhenEmptyUserName() {
                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put("/")
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404);
            }

            @Test
            default void putShouldReturnBadRequestWhenUsernameIsTooLong() {
                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString() + "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                        "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                        "0123456789.0123456789.0123456789.")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("username length should not be longer than 255 characters"));
            }

            @Test
            default void putShouldReturnNotFoundWhenUsernameContainsSlash() {
                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString() + "/" + USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404);
            }

            @Test
            default void deleteShouldRemoveAssociatedUser() {
                // Given
                with()
                    .body("{\"password\":\"password\"}")
                    .put(USERNAME_WITH_DOMAIN.asString());

                // When
                when()
                    .delete(USERNAME_WITH_DOMAIN.asString())
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
            default void deleteShouldStillBeValidWithExtraBody() {
                given()
                    .body("{\"bad\":\"any\"}")
                .when()
                    .delete(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.NO_CONTENT_204);
            }
        }

        interface MockBehaviorErrorHandlingContract {

            @Test
            default void deleteShouldStillBeOkWhenNoUser(UsersRepository usersRepository) throws Exception {
                doThrow(new UsersRepositoryException("message")).when(usersRepository).removeUser(USERNAME_WITH_DOMAIN);

                when()
                    .delete(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.NO_CONTENT_204);
            }

            @Test
            default void getShouldFailOnRepositoryException(UsersRepository usersRepository) throws Exception {
                when(usersRepository.list()).thenThrow(new UsersRepositoryException("message"));

                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void putShouldFailOnRepositoryExceptionOnGetUserByName(UsersRepository usersRepository) throws Exception {
                when(usersRepository.getUserByName(USERNAME_WITH_DOMAIN)).thenThrow(new UsersRepositoryException("message"));

                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void putShouldReturnInternalServerErrorWhenUserRepositoryAddingUserError(UsersRepository usersRepository) throws Exception {
                when(usersRepository.getUserByName(USERNAME_WITH_DOMAIN)).thenReturn(null);
                doThrow(new UsersRepositoryException("message")).when(usersRepository).addUser(USERNAME_WITH_DOMAIN, PASSWORD);

                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void putShouldReturnInternalServerErrorWhenUserRepositoryUpdatingUserError(UsersRepository usersRepository) throws Exception {
                when(usersRepository.getUserByName(USERNAME_WITH_DOMAIN)).thenReturn(mock(User.class));
                doThrow(new UsersRepositoryException("message")).when(usersRepository).updateUser(any());

                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }


            @Test
            default void deleteShouldFailOnUnknownException(UsersRepository usersRepository) throws Exception {
                doThrow(new RuntimeException()).when(usersRepository).removeUser(USERNAME_WITH_DOMAIN);

                when()
                    .delete(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void getShouldFailOnUnknownException(UsersRepository usersRepository) throws Exception {
                when(usersRepository.list()).thenThrow(new RuntimeException());

                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void putShouldFailOnUnknownExceptionOnGetUserByName(UsersRepository usersRepository) throws Exception {
                when(usersRepository.getUserByName(USERNAME_WITH_DOMAIN)).thenThrow(new RuntimeException());

                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void putShouldFailOnUnknownExceptionOnAddUser(UsersRepository usersRepository) throws Exception {
                when(usersRepository.getUserByName(USERNAME_WITH_DOMAIN)).thenReturn(null);
                doThrow(new RuntimeException()).when(usersRepository).addUser(USERNAME_WITH_DOMAIN, PASSWORD);

                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }

            @Test
            default void putShouldFailOnUnknownExceptionOnGetUpdateUser(UsersRepository usersRepository) throws Exception {
                when(usersRepository.getUserByName(USERNAME_WITH_DOMAIN)).thenReturn(mock(User.class));
                doThrow(new RuntimeException()).when(usersRepository).updateUser(any());

                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put(USERNAME_WITH_DOMAIN.asString())
                .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            }
        }
    }

    private static final Domain DOMAIN = Domain.of("domain");
    private static final Username USERNAME_WITHOUT_DOMAIN = Username.of("username");
    private static final Username USERNAME_WITH_DOMAIN =
        Username.fromLocalPartWithDomain(USERNAME_WITHOUT_DOMAIN.asString(), DOMAIN);
    private static final String PASSWORD = "password";

    @Nested
    class WithVirtualHosting implements UserRoutesContract.AllContracts {

        @RegisterExtension
        UserRoutesExtension extension = UserRoutesExtension.withVirtualHosting();

        @Test
        void puttingWithDomainPartInUsernameTwoTimesShouldBeAllowed() {
            // Given
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            // When
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
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

            assertThat(users).containsExactly(ImmutableMap.of("username", USERNAME_WITH_DOMAIN.asString()));
        }

        @Test
        void putWithDomainPartInUsernameShouldReturnOkWhenWithA255LongUsername() {
            String usernameTail = "@" + DOMAIN.name();
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(StringUtils.repeat('j', 255 - usernameTail.length()) + usernameTail)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putWithDomainPartInUsernameShouldAddTheUser() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

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

            assertThat(users).containsExactly(ImmutableMap.of("username", USERNAME_WITH_DOMAIN.asString()));
        }

        @Test
        void putShouldReturnBadRequestWhenUsernameDoesNotHaveDomainPart() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put("justLocalPart")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Username supplied is invalid"))
                .body("details", is("Given Username needs to contain a @domainpart"));
        }

        @Test
        void putWithDomainPartInUsernameShouldReturnOkWhenValidJsonBody() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }
    }

    @Nested
    class WithoutVirtualHosting implements UserRoutesContract.AllContracts {

        @RegisterExtension
        UserRoutesExtension extension = UserRoutesExtension.withoutVirtualHosting();

        @Test
        void puttingWithoutDomainPartInUsernameTwoTimesShouldBeAllowed() {
            // Given
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            // When
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITHOUT_DOMAIN.asString())
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

            assertThat(users).containsExactly(ImmutableMap.of("username", USERNAME_WITHOUT_DOMAIN.asString()));
        }

        @Test
        void putWithoutDomainPartInUsernameShouldReturnOkWhenWithA255LongUsername() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(StringUtils.repeat('j',
                    255 - USERNAME_WITHOUT_DOMAIN.asString().length()) + USERNAME_WITHOUT_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putWithoutDomainPartInUsernameShouldAddTheUser() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

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

            assertThat(users).containsExactly(ImmutableMap.of("username", USERNAME_WITHOUT_DOMAIN.asString()));
        }

        @Test
        void putShouldReturnBadRequestWhenUsernameHasDomainPart() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Username supplied is invalid"))
                .body("details", is("Given Username contains a @domainpart but virtualhosting support is disabled"));
        }

        @Test
        void putWithoutDomainPartInUsernameShouldReturnOkWhenValidJsonBody() {
            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITHOUT_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }
    }
}
