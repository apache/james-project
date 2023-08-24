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
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.memory.MemoryDelegationStore;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UserRoutesTest {

    private static final String GET_DELEGATED_USERS_PATH = "/%s/authorizedUsers";
    private static final String CLEAR_DELEGATED_USERS_PATH = "/%s/authorizedUsers";
    private static final String ADD_DELEGATED_USER_PATH = "/%s/authorizedUsers/%s";
    private static final String REMOVE_DELEGATED_USER_PATH = "/%s/authorizedUsers/%s";

    private static class UserRoutesExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

        static UserRoutesExtension withVirtualHosting() throws DomainListException {
            SimpleDomainList domainList = setupDomainList();
            return new UserRoutesExtension(MemoryUsersRepository.withVirtualHosting(domainList), domainList);
        }

        static UserRoutesExtension withoutVirtualHosting() throws DomainListException {
            SimpleDomainList domainList = setupDomainList();
            return new UserRoutesExtension(MemoryUsersRepository.withoutVirtualHosting(domainList), domainList);
        }

        private static SimpleDomainList setupDomainList() throws DomainListException {
            SimpleDomainList domainList = new SimpleDomainList();
            domainList.addDomain(DOMAIN);
            return domainList;
        }

        final MemoryUsersRepository usersRepository;
        final SimpleDomainList domainList;
        final MemoryRecipientRewriteTable recipientRewriteTable;
        final AliasReverseResolver aliasReverseResolver;
        final CanSendFrom canSendFrom;
        final MemoryDelegationStore delegationStore;

        WebAdminServer webAdminServer;

        UserRoutesExtension(MemoryUsersRepository usersRepository, SimpleDomainList domainList) {
            this.usersRepository = spy(usersRepository);
            this.domainList = domainList;
            this.recipientRewriteTable = new MemoryRecipientRewriteTable();
            this.recipientRewriteTable.setDomainList(domainList);
            this.recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
            this.aliasReverseResolver = new AliasReverseResolverImpl(recipientRewriteTable);
            this.canSendFrom = new CanSendFromImpl(recipientRewriteTable, aliasReverseResolver);
            UserEntityValidator validator = UserEntityValidator.aggregate(
                new DefaultUserEntityValidator(this.usersRepository),
                new RecipientRewriteTableUserEntityValidator(recipientRewriteTable));
            this.usersRepository.setValidator(validator);
            recipientRewriteTable.setUsersRepository(usersRepository);
            recipientRewriteTable.setUserEntityValidator(validator);
            this.delegationStore = new MemoryDelegationStore();
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            webAdminServer = startServer(usersRepository);
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) {
            webAdminServer.destroy();
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> parameterType = parameterContext.getParameter().getType();
            return parameterType.isAssignableFrom(UsersRepository.class)
                || parameterType.isAssignableFrom(RecipientRewriteTable.class)
                || parameterType.isAssignableFrom(DelegationStore.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> parameterType = parameterContext.getParameter().getType();
            if (parameterType.isAssignableFrom(UsersRepository.class)) {
                return usersRepository;
            }
            if (parameterType.isAssignableFrom(RecipientRewriteTable.class)) {
                return recipientRewriteTable;
            }
            if (parameterType.isAssignableFrom(DelegationStore.class)) {
                return delegationStore;
            }
            throw new RuntimeException("Unknown parameter type: " + parameterType);
        }

        private WebAdminServer startServer(UsersRepository usersRepository) {
            WebAdminServer server = WebAdminUtils.createWebAdminServer(new UserRoutes(new UserService(usersRepository), canSendFrom, new JsonTransformer(),
                    delegationStore))
                .start();

            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server)
                .setBasePath(UserRoutes.USERS)
                .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
                .build();

            return server;
        }
    }

    interface UserRoutesContract {
        interface AllContracts extends NormalBehaviourContract, MockBehaviorErrorHandlingContract {
        }

        interface NormalBehaviourContract {

            @Test
            default void getDelegatedUsersShouldReturnEmptyByDefault(UsersRepository usersRepository) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);

                List<Map<String, String>> delegatedUserList = when()
                        .get(String.format(GET_DELEGATED_USERS_PATH, ALICE.asString()))
                    .then()
                        .statusCode(HttpStatus.OK_200)
                        .contentType(ContentType.JSON)
                        .extract()
                        .body()
                        .jsonPath()
                        .getList(".");

                assertThat(delegatedUserList).isEmpty();
            }

            @Test
            default void getDelegatedUsersShouldReturnAddedUsers(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);

                Mono.from(delegationStore.addAuthorizedUser(ALICE, BOB)).block();
                Mono.from(delegationStore.addAuthorizedUser(ALICE, ANDRE)).block();

                List<String> delegatedUserList = when()
                    .get(String.format(GET_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

                assertThat(delegatedUserList).containsOnly(ANDRE.asString(), BOB.asString());
            }

            @Test
            default void getDelegatedUsersShouldReturn404NotFoundWhenBaseUserIsNotExisted() {
                when()
                    .get(String.format(GET_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                    .body("message", is(String.format("User '%s' does not exist", ALICE.asString())));
            }

            @Test
            default void addDelegatedUserShouldSucceed(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);
                when(usersRepository.contains(BOB)).thenReturn(true);

                when()
                    .put(String.format(ADD_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).containsOnly(BOB);
            }

            @Test
            default void addDelegatedUserShouldReturn404NotFoundWhenBaseUserIsNotExisted() {
                when()
                    .put(String.format(ADD_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                    .body("message", is(String.format("User '%s' does not exist", ALICE.asString())));
            }

            @Test
            default void addDelegatedUserShouldReturnOkWhenDelegatedUserIsNotExisted(UsersRepository usersRepository) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);

                when()
                    .put(String.format(ADD_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200);
            }

            @Test
            default void removeDelegatedUserShouldRemoveThatDelegatedUser(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);
                when(usersRepository.contains(BOB)).thenReturn(true);
                when(usersRepository.contains(ANDRE)).thenReturn(true);

                Mono.from(delegationStore.addAuthorizedUser(ALICE, BOB)).block();
                Mono.from(delegationStore.addAuthorizedUser(ALICE, ANDRE)).block();

                when()
                    .delete(String.format(REMOVE_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).containsOnly(ANDRE);
            }

            @Test
            default void removeDelegatedUserShouldSucceedWhenEmptyAddedUsers(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);
                when(usersRepository.contains(BOB)).thenReturn(true);

                when()
                    .delete(String.format(REMOVE_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).isEmpty();
            }

            @Test
            default void removeDelegatedUserShouldReturn404NotFoundWhenBaseUserIsNotExisted() {
                when()
                    .delete(String.format(REMOVE_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                    .body("message", is(String.format("User '%s' does not exist", ALICE.asString())));
            }

            @Test
            default void removeDelegatedUserShouldReturnOkWhenDelegatedUserIsNotExisted(UsersRepository usersRepository) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);

                when()
                    .delete(String.format(REMOVE_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200);
            }


            @Test
            default void removeDelegatedUserShouldBeIdempotent(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);
                when(usersRepository.contains(BOB)).thenReturn(true);

                Mono.from(delegationStore.addAuthorizedUser(ALICE, BOB)).block();

                when()
                    .delete(String.format(REMOVE_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                when()
                    .delete(String.format(REMOVE_DELEGATED_USER_PATH, ALICE.asString(), BOB.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).isEmpty();
            }

            @Test
            default void clearAllDelegatedUsersShouldSucceedWhenEmptyAddedUsers(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);

                when()
                    .delete(String.format(CLEAR_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).isEmpty();
            }

            @Test
            default void clearAllDelegatedUsersShouldBeIdempotent(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);
                when(usersRepository.contains(BOB)).thenReturn(true);
                when(usersRepository.contains(ANDRE)).thenReturn(true);

                Mono.from(delegationStore.addAuthorizedUser(ALICE, BOB)).block();
                Mono.from(delegationStore.addAuthorizedUser(ALICE, ANDRE)).block();

                when()
                    .delete(String.format(CLEAR_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                when()
                    .delete(String.format(CLEAR_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).isEmpty();
            }

            @Test
            default void clearAllDelegatedUsersShouldReturn404NotFoundWhenBaseUserIsNotExisted() {
                when()
                    .delete(String.format(CLEAR_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                    .body("message", is(String.format("User '%s' does not exist", ALICE.asString())));
            }

            @Test
            default void clearAllDelegatedUsersShouldClearAllAddedUsers(UsersRepository usersRepository, DelegationStore delegationStore) throws UsersRepositoryException {
                when(usersRepository.contains(ALICE)).thenReturn(true);
                when(usersRepository.contains(BOB)).thenReturn(true);
                when(usersRepository.contains(ANDRE)).thenReturn(true);

                Mono.from(delegationStore.addAuthorizedUser(ALICE, BOB)).block();
                Mono.from(delegationStore.addAuthorizedUser(ALICE, ANDRE)).block();

                when()
                    .delete(String.format(CLEAR_DELEGATED_USERS_PATH, ALICE.asString()))
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON);

                assertThat(Flux.from(delegationStore.authorizedUsers(ALICE)).collectList().block()).isEmpty();
            }

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
            default void headShouldReturnBadRequestWhenEmptyUserName() {
                when()
                    .head("/")
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

        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        interface IllegalCharactersErrorHandlingContract {

            default Stream<Arguments> illegalCharacters() {
                return Stream.of(
                    Arguments.of(URLEncoder.encode("\"")),
                    Arguments.of(URLEncoder.encode("(")),
                    Arguments.of(URLEncoder.encode(")")),
                    Arguments.of(URLEncoder.encode(",")),
                    Arguments.of(URLEncoder.encode(":")),
                    Arguments.of(URLEncoder.encode(";")),
                    Arguments.of(URLEncoder.encode("<")),
                    Arguments.of(URLEncoder.encode(">")),
                    Arguments.of(URLEncoder.encode("@")),
                    Arguments.of(URLEncoder.encode("[")),
                    Arguments.of(URLEncoder.encode("\\")),
                    Arguments.of(URLEncoder.encode("]")),
                    Arguments.of("%20"));
            }

            @ParameterizedTest
            @MethodSource("illegalCharacters")
            default void putShouldReturnBadRequestWhenUsernameContainsSpecialCharacter(String illegalCharacter) {
                given()
                    .body("{\"password\":\"password\"}")
                .when()
                    .put("user" + illegalCharacter + "name@" + DOMAIN.name())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
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
                doThrow(new UsersRepositoryException("message"))
                    .when(usersRepository)
                    .contains(any(Username.class));

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
                    .queryParam("force")
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
                doThrow(new RuntimeException())
                    .when(usersRepository)
                    .contains(any(Username.class));

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
                    .queryParam("force")
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
    private static final Username OTHER_USERNAME_WITH_DOMAIN =
        Username.fromLocalPartWithDomain("other", DOMAIN);
    private static final Username ALICE  = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final Username BOB  = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username ANDRE  = Username.fromLocalPartWithDomain("andre", DOMAIN);
    private static final String PASSWORD = "password";

    @Nested
    class WithVirtualHosting implements UserRoutesContract.AllContracts {

        @RegisterExtension
        UserRoutesExtension extension = UserRoutesExtension.withVirtualHosting();

        WithVirtualHosting() throws DomainListException {
        }

        @Test
        void headShouldReturnOKWhenUserExists() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            when()
                .head(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.OK_200);
        }

        @Test
        void headShouldReturnNotFoundWhenUserDoesNotExist() {
            when()
                .head(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void puttingWithDomainPartInUsernameTwoTimesShouldNotBeAllowed() {
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
                .statusCode(HttpStatus.CONFLICT_409);

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
        void putShouldFailWhenConflictWithAlias(RecipientRewriteTable recipientRewriteTable) throws Exception {
            recipientRewriteTable.addAliasMapping(MappingSource.fromUser(USERNAME_WITH_DOMAIN),
                OTHER_USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .body("statusCode", is(HttpStatus.CONFLICT_409))
                .body("type", is(ErrorResponder.ErrorType.WRONG_STATE.getType()))
                .body("message", is("'username@domain' already have associated mappings: alias:other@domain"));
        }

        @Test
        void putShouldFailWhenConflictWithGroup(RecipientRewriteTable recipientRewriteTable) throws Exception {
            recipientRewriteTable.addGroupMapping(MappingSource.fromUser(USERNAME_WITH_DOMAIN),
                OTHER_USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .body("statusCode", is(HttpStatus.CONFLICT_409))
                .body("type", is(ErrorResponder.ErrorType.WRONG_STATE.getType()))
                .body("message", is("'username@domain' already have associated mappings: group:other@domain"));
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
        void putWithDomainPartInUsernameWithExistingUsernameAndNonForceParamShouldNotBeAllowed() {
            given()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .body("statusCode", is(HttpStatus.CONFLICT_409))
                .body("type", is(ErrorResponder.ErrorType.WRONG_STATE.getType()))
                .body("message", is("'username@domain' user already exist"));
        }

        @Test
        void putWithDomainPartInUsernameWithExistingUsernameAndForceParamShouldBeAllowed() {
            given()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
                .queryParam("force")
            .when()
                .put(USERNAME_WITH_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
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

        @Test
        void allowedFromHeadersShouldHaveUsersMailAddress() {
            // Given
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            // Then
            List<String> allowedFroms =
                when()
                    .get(USERNAME_WITH_DOMAIN.asString() + SEPARATOR + "allowedFromHeaders")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(allowedFroms).containsExactly(USERNAME_WITH_DOMAIN.asString());
        }

        @Test
        void allowedFromHeadersShouldHaveAllMailAddressesWhenAliasAdded(RecipientRewriteTable recipientRewriteTable) throws RecipientRewriteTableException {
            // Given
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            String aliasAddress = "alias@" + DOMAIN.asString();
            recipientRewriteTable.addAliasMapping(MappingSource.fromUser(Username.of(aliasAddress)), USERNAME_WITH_DOMAIN.asString());

            // Then
            List<String> allowedFroms =
                when()
                    .get(USERNAME_WITH_DOMAIN.asString() + SEPARATOR + "allowedFromHeaders")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(allowedFroms).containsExactly(USERNAME_WITH_DOMAIN.asString(), aliasAddress);
        }

        @Test
        void allowedFromHeadersShouldReturn404WhenUserDoesNotExist() {
            when()
                .get(USERNAME_WITH_DOMAIN.asString() + SEPARATOR + "allowedFromHeaders")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("user 'username@domain' does not exist"));
        }

        @Test
        void allowedFromHeadersShouldReturn404WhenUserIsInvalid() {
            when()
                .get("@@" + SEPARATOR + "allowedFromHeaders")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"));
        }

        @Test
        void verifyShouldReturnOkWhenUserPasswordMatches() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .post(USERNAME_WITH_DOMAIN.asString() + "/verify")
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void verifyShouldReturnUnauthorizedWhenUserPasswordDoesntMatch() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"false\"}")
            .when()
                .post(USERNAME_WITH_DOMAIN.asString() + "/verify")
            .then()
                .statusCode(HttpStatus.UNAUTHORIZED_401);
        }

        @Test
        void verifyShouldReturnUnauthorizedWhenUserDoesNotExist() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITH_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .post("/NOTusername@domain/verify")
            .then()
                .statusCode(HttpStatus.UNAUTHORIZED_401);
        }

        @Nested
        class IllegalCharacterErrorHandlingTest implements UserRoutesContract.IllegalCharactersErrorHandlingContract {

        }
    }

    @Nested
    class WithoutVirtualHosting implements UserRoutesContract.AllContracts {

        @RegisterExtension
        UserRoutesExtension extension = UserRoutesExtension.withoutVirtualHosting();

        WithoutVirtualHosting() throws DomainListException {
        }

        @Test
        void headShouldReturnOKWhenUserExists() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            when()
                .head(USERNAME_WITHOUT_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.OK_200);
        }

        @Test
        void headShouldReturnNotFoundWhenUserDoesNotExist() {
            when()
                .head(USERNAME_WITHOUT_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void puttingWithoutDomainPartInUsernameTwoTimesShouldNotBeAllowed() {
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
                .statusCode(HttpStatus.CONFLICT_409);

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
        void putWithoutDomainPartInUsernameWithExistingUsernameAndNonForceParamShouldNotBeAllowed() {
            given()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .put(USERNAME_WITHOUT_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .body("statusCode", is(HttpStatus.CONFLICT_409))
                .body("type", is(ErrorResponder.ErrorType.WRONG_STATE.getType()))
                .body("message", is("'username' user already exist"));
        }

        @Test
        void putWithoutDomainPartInUsernameWithExistingUsernameAndForceParamShouldBeAllowed() {
            given()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
                .queryParam("force")
            .when()
                .put(USERNAME_WITHOUT_DOMAIN.asString())
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
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

        @Test
        void verifyShouldReturnOkWhenUserPasswordMatches() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .post(USERNAME_WITHOUT_DOMAIN.asString() + "/verify")
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void verifyShouldReturnUnauthorizedWhenUserPasswordDoesntMatch() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            given()
                .body("{\"password\":\"false\"}")
            .when()
                .post(USERNAME_WITHOUT_DOMAIN.asString() + "/verify")
            .then()
                .statusCode(HttpStatus.UNAUTHORIZED_401);
        }

        @Test
        void verifyShouldReturnUnauthorizedWhenUserDoesNotExist() {
            with()
                .body("{\"password\":\"password\"}")
                .put(USERNAME_WITHOUT_DOMAIN.asString());

            given()
                .body("{\"password\":\"password\"}")
            .when()
                .post("/NOTusername/verify")
            .then()
                .statusCode(HttpStatus.UNAUTHORIZED_401);
        }

        @Nested
        class IllegalCharacterErrorHandlingTest implements UserRoutesContract.IllegalCharactersErrorHandlingContract {

        }
    }
}
