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
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USERS_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.UserReindexingTask;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

class UserMailboxesRoutesTest {
    private static final Username USERNAME = Username.of("username");
    private static final String MAILBOX_NAME = "myMailboxName";
    private static final String MAILBOX_NAME_WITH_DOTS = "my..MailboxName";
    private static final String INVALID_MAILBOX_NAME = "myMailboxName#";
    private static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);
    private static final String ERROR_TYPE_NOTFOUND = "notFound";
    
    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;
    private ListeningMessageSearchIndex searchIndex;
    private MemoryTaskManager taskManager;

    private void createServer(MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory) throws Exception {
        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(USERNAME)).thenReturn(true);


        taskManager = new MemoryTaskManager(new Hostname("foo"));
        searchIndex = mock(ListeningMessageSearchIndex.class);
        Mockito.when(searchIndex.add(any(), any(), any())).thenReturn(Mono.empty());
        Mockito.when(searchIndex.deleteAll(any(), any())).thenReturn(Mono.empty());
        ReIndexerPerformer reIndexerPerformer = new ReIndexerPerformer(
            mailboxManager,
            searchIndex,
            mapperFactory);
        ReIndexer reIndexer = new ReIndexerImpl(
            reIndexerPerformer,
            mailboxManager,
            mapperFactory);

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new UserMailboxesRoutes(new UserMailboxesService(mailboxManager, usersRepository), new JsonTransformer(),
                    taskManager,
                    ImmutableSet.of(new UserMailboxesRoutes.UserReIndexingTaskRegistration(reIndexer))),
                new TasksRoutes(taskManager, new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(USERS_BASE + SEPARATOR + USERNAME.asString() + SEPARATOR + UserMailboxesRoutes.MAILBOXES)
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Nested
    class NormalBehaviour {

        @BeforeEach
        void setUp() throws Exception {
            InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
            createServer(mailboxManager, mailboxManager.getMapperFactory());
        }

        @Test
        void getMailboxesShouldUserErrorFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get()
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        void getShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Invalid get on user mailboxes")
                .containsEntry("details", "User does not exist");
        }

        @Test
        void putShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Invalid get on user mailboxes")
                .containsEntry("details", "User does not exist");
        }

        @Test
        void putShouldThrowWhenMailboxNameWithDots() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME_WITH_DOTS)
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox")
                .containsEntry("details", "'#private:username:my..MailboxName' has an empty part within its mailbox name considering . as a delimiter");
        }

        @Test
        void putShouldThrowWhenMailboxNameStartsWithDot() throws Exception {
            Map<String, Object> errors = when()
                .put(".startWithDot")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox")
                .containsEntry("details", "'#private:username:.startWithDot' has an empty part within its mailbox name considering . as a delimiter");
        }

        @Test
        void putShouldThrowWhenMailboxNameEndsWithDots() throws Exception {
            Map<String, Object> errors = when()
                .put("endWithDot.")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox")
                .containsEntry("details", "'#private:username:endWithDot.' has an empty part within its mailbox name considering . as a delimiter");
        }

        @Test
        void putShouldThrowWhenInvalidMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(true);

            Map<String, Object> errors = when()
                .put(INVALID_MAILBOX_NAME)
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox")
                .containsEntry("details", "#private:username:myMailboxName# contains one of the forbidden characters %*&#");
        }

        @Test
        void deleteShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Invalid get on user mailboxes")
                .containsEntry("details", "User does not exist");
        }

        @Test
        void getShouldReturnUserErrorWithInvalidWildcardMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "*")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to test existence of an invalid mailbox")
                .containsEntry("details", "#private:username:myMailboxName* contains one of the forbidden characters %*&#");
        }

        @Test
        void putShouldReturnUserErrorWithInvalidWildcardMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "*")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void deleteShouldReturnUserErrorWithInvalidWildcardMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "*")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void getShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "%")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to test existence of an invalid mailbox")
                .containsEntry("details", "#private:username:myMailboxName% contains one of the forbidden characters %*&#");
        }

        @Test
        void putShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "%")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void deleteShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "%")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void getShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "#")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to test existence of an invalid mailbox")
                .containsEntry("details", "#private:username:myMailboxName# contains one of the forbidden characters %*&#");
        }

        @Test
        void putShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "#")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void deleteShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "#")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void getShouldReturnUserErrorWithInvalidAndMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "&")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to test existence of an invalid mailbox")
                .containsEntry("details", "#private:username:myMailboxName& contains one of the forbidden characters %*&#");
        }

        @Test
        void putShouldReturnUserErrorWithInvalidAndMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "&")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void deleteShouldReturnUserErrorWithInvalidAndMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "&")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        void deleteMailboxesShouldReturnUserErrorWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .delete()
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Invalid delete on user mailboxes");
        }

        @Test
        void getMailboxesShouldReturnEmptyListByDefault() {
            List<Object> list =
                when()
                    .get()
                .then()
                    .statusCode(OK_200)
                    .contentType(JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).isEmpty();
        }

        @Test
        void putShouldReturnNotFoundWhenNoMailboxName() {
            when()
                .put()
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void putShouldReturnNotFoundWhenJustSeparator() {
            when()
                .put(SEPARATOR)
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void putShouldReturnOk() {
            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void putShouldReturnOkWhenIssuedTwoTimes() {
            with()
                .put(MAILBOX_NAME);

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void putShouldAddAMailbox() {
            with()
                .put(MAILBOX_NAME);

            when()
                .get()
            .then()
                .statusCode(OK_200)
                .body(".", hasSize(1))
                .body("[0].mailboxName", is("myMailboxName"))
                .body("[0].mailboxId", is("1"));
        }

        @Test
        void getShouldReturnNotFoundWhenMailboxDoesNotExist() {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Mailbox does not exist");
        }

        @Test
        void getShouldReturnOkWhenMailboxExists() {
            with()
                .put(MAILBOX_NAME);

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnOkWhenMailboxDoesNotExist() {
            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnOkWhenMailboxExists() {
            with()
                .put(MAILBOX_NAME);

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldRemoveMailbox() {
            with()
                .put(MAILBOX_NAME);

            with()
                .delete(MAILBOX_NAME);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", ERROR_TYPE_NOTFOUND)
                .containsEntry("message", "Mailbox does not exist");
        }

        @Test
        void deleteMailboxesShouldReturnOkWhenNoMailboxes() {
            when()
                .delete()
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteMailboxesShouldReturnOkWhenMailboxes() {
            with()
                .put(MAILBOX_NAME);

            when()
                .delete()
                .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteMailboxesShouldRemoveAllUserMailboxes() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put("otherMailbox");

            with()
                .delete();

            List<Object> list =
                when()
                    .get()
                .then()
                    .statusCode(OK_200)
                    .contentType(JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).isEmpty();
        }

        @Test
        void deleteShouldReturnOkWhenMailboxHasChildren() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put(MAILBOX_NAME + ".child");

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldDeleteAMailboxAndItsChildren() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put(MAILBOX_NAME + ".child");

            with()
                .delete(MAILBOX_NAME);

            List<Object> list =
                when()
                    .get()
                .then()
                    .statusCode(OK_200)
                    .contentType(JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).isEmpty();
        }

        @Test
        void deleteShouldNotDeleteUnrelatedMailbox() {
            String mailboxName = MAILBOX_NAME + "!child";
            with()
                .put(MAILBOX_NAME);

            with()
                .put(mailboxName);

            with()
                .delete(MAILBOX_NAME);

            List<Map<String, String>> list =
                when()
                    .get()
                .then()
                    .statusCode(OK_200)
                    .contentType(JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list)
                .hasSize(1)
                .first()
                .satisfies(map -> assertThat(map).hasSize(2)
                    .containsKeys("mailboxId")
                    .containsEntry("mailboxName", mailboxName));
        }

        @Test
        void deleteShouldReturnOkWhenDeletingChildMailboxes() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put(MAILBOX_NAME + ".child");

            when()
                .delete(MAILBOX_NAME + ".child")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldBeAbleToRemoveChildMailboxes() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put(MAILBOX_NAME + ".child");

            with()
                .delete(MAILBOX_NAME + ".child");

            List<Map<String, String>> list =
                when()
                    .get()
                .then()
                    .statusCode(OK_200)
                    .contentType(JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list)
                .hasSize(1)
                .first()
                .satisfies(map -> assertThat(map).hasSize(2)
                    .containsKeys("mailboxId")
                    .containsEntry("mailboxName", MAILBOX_NAME));
        }
    }

    @Nested
    class ExceptionHandling {
        private MailboxManager mailboxManager;

        @BeforeEach
        void setUp() throws Exception {
            mailboxManager = mock(MailboxManager.class);
            when(mailboxManager.createSystemSession(any())).thenReturn(MailboxSessionUtil.create(USERNAME));

            createServer(mailboxManager, mock(MailboxSessionMapperFactory.class));
        }

        @Test
        void putShouldGenerateInternalErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).createMailbox(any(), any());

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).createMailbox(any(), any());

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldReturnOkOnMailboxExists() throws Exception {
            doThrow(new MailboxExistsException(MAILBOX_NAME)).when(mailboxManager).createMailbox(any(), any());

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownExceptionOnDelete() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(
                                MailboxMetaData.unselectableMailbox(
                                        MailboxPath.forUser(USERNAME, MAILBOX_NAME), mailboxId, '.')));
            doThrow(new RuntimeException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownExceptionOnSearch() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenThrow(new RuntimeException());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownMailboxExceptionOnDelete() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(
                                MailboxMetaData.unselectableMailbox(MailboxPath.forUser(USERNAME, MAILBOX_NAME), mailboxId, '.')));
            doThrow(new MailboxException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownMailboxExceptionOnSearch() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenThrow(new MailboxException());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnOkOnMailboxDoesNotExists() throws Exception {
            doThrow(new MailboxNotFoundException(MAILBOX_NAME)).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownExceptionWhenListingMailboxes() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnMailboxExceptionWhenListingMailboxes() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }


        @Test
        void deleteShouldGenerateInternalErrorOnUnknownExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(
                                MailboxMetaData.unselectableMailbox(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new RuntimeException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnOkOnMailboxNotFoundExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(MailboxMetaData.unselectableMailbox(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new MailboxNotFoundException("any")).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete()
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnInternalErrorOnMailboxExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(MailboxMetaData.unselectableMailbox(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new MailboxException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldGenerateInternalErrorOnUnknownException() throws Exception {
            doReturn(Mono.error(new RuntimeException())).when(mailboxManager).mailboxExists(any(), any());

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            doReturn(Mono.error(new MailboxException())).when(mailboxManager).mailboxExists(any(), any());

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getMailboxesShouldGenerateInternalErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .get()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getMailboxesShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .get()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getMailboxesShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .get()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteMailboxesShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

    }

    @Nested
    class UserReprocessing {

        private InMemoryMailboxManager mailboxManager;

        @BeforeEach
        void setUp() throws Exception {
            mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
            createServer(mailboxManager, mailboxManager.getMapperFactory());
        }

        @Nested
        class Validation {
            @Test
            void userReprocessingShouldFailWithNoTask() {
                when()
                    .post()
                .then()
                    .statusCode(BAD_REQUEST_400)
                    .body("statusCode", Matchers.is(400))
                    .body("type", Matchers.is("InvalidArgument"))
                    .body("message", Matchers.is("Invalid arguments supplied in the user request"))
                    .body("details", Matchers.is("'task' query parameter is compulsory. Supported values are [reIndex]"));
            }

            @Test
            void userReprocessingShouldFailWithBadTask() {
                given()
                    .queryParam("task", "bad")
                .when()
                    .post()
                .then()
                    .statusCode(BAD_REQUEST_400)
                    .body("statusCode", Matchers.is(400))
                    .body("type", Matchers.is("InvalidArgument"))
                    .body("message", Matchers.is("Invalid arguments supplied in the user request"))
                    .body("details", Matchers.is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }

            @Test
            void userReprocessingShouldFailWithBadUser() {
                RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                    .setBasePath(USERS_BASE + SEPARATOR + "bad@bad@bad" + SEPARATOR + UserMailboxesRoutes.MAILBOXES)
                    .build();

                given()
                    .queryParam("user", "bad@bad@bad")
                    .queryParam("task", "reIndex")
                .when()
                    .post()
                .then()
                    .statusCode(BAD_REQUEST_400)
                    .body("statusCode", Matchers.is(400))
                    .body("type", Matchers.is("InvalidArgument"))
                    .body("message", Matchers.is("Invalid arguments supplied in the user request"))
                    .body("details", Matchers.is("The username should not contain multiple domain delimiter."));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void userReprocessingShouldNotFailWhenNoMail() {
                String taskId = given()
                    .queryParam("task", "reIndex")
                .when()
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                    .when()
                    .get(taskId + "/await")
                    .then()
                    .body("status", Matchers.is("completed"))
                    .body("taskId", Matchers.is(notNullValue()))
                    .body("type", Matchers.is(UserReindexingTask.USER_RE_INDEXING.asString()))
                    .body("additionalInformation.username", Matchers.is("username"))
                    .body("additionalInformation.successfullyReprocessedMailCount", Matchers.is(0))
                    .body("additionalInformation.failedReprocessedMailCount", Matchers.is(0))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()))
                    .body("completedDate", Matchers.is(notNullValue()));
            }

            @Test
            void userReprocessingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = given()
                    .queryParam("task", "reIndex")
                .when()
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", Matchers.is("completed"))
                    .body("taskId", Matchers.is(notNullValue()))
                    .body("type", Matchers.is(UserReindexingTask.USER_RE_INDEXING.asString()))
                    .body("additionalInformation.username", Matchers.is("username"))
                    .body("additionalInformation.successfullyReprocessedMailCount", Matchers.is(1))
                    .body("additionalInformation.failedReprocessedMailCount", Matchers.is(0))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()))
                    .body("completedDate", Matchers.is(notNullValue()));
            }

            @Test
            void userReprocessingShouldReturnTaskDetailsWhenFailing() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession).getId();

                doReturn(Mono.error(new RuntimeException()))
                    .when(searchIndex)
                    .add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String taskId = with()
                    .queryParam("task", "reIndex")
                .post()
                    .jsonPath()
                    .get("taskId");

                long uidAsLong = composedMessageId.getUid().asLong();
                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", Matchers.is("failed"))
                    .body("taskId", Matchers.is(notNullValue()))
                    .body("type", Matchers.is(UserReindexingTask.USER_RE_INDEXING.asString()))
                    .body("additionalInformation.successfullyReprocessedMailCount", Matchers.is(0))
                    .body("additionalInformation.failedReprocessedMailCount", Matchers.is(1))
                    .body("additionalInformation.failures.\"" + mailboxId.serialize() + "\"[0].uid", Matchers.is(Long.valueOf(uidAsLong).intValue()))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void userReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession).getId();

                String taskId = given()
                    .queryParam("task", "reIndex")
                .when()
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", Matchers.is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<MailboxId> mailboxIdCaptor = ArgumentCaptor.forClass(MailboxId.class);
                ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).deleteAll(any(MailboxSession.class), mailboxIdCaptor.capture());
                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxIdCaptor.getValue()).matches(capturedMailboxId -> capturedMailboxId.equals(mailboxId));
                assertThat(mailboxCaptor2.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(messageCaptor.getValue()).matches(message -> message.getMailboxId().equals(mailboxId)
                    && message.getUid().equals(createdMessage.getUid()));
            }
        }
    }
}
