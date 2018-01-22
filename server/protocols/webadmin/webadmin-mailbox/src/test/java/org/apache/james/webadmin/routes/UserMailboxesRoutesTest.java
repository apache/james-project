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

import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USERS_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.SimpleMailboxMetaData;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

@RunWith(HierarchicalContextRunner.class)
public class UserMailboxesRoutesTest {

    public static final String USERNAME = "username";
    public static final String MAILBOX_NAME = "myMailboxName";
    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;

    private void createServer(MailboxManager mailboxManager) throws Exception {
        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(USERNAME)).thenReturn(true);

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new UserMailboxesRoutes(new UserMailboxesService(mailboxManager, usersRepository), new JsonTransformer()));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.defineRequestSpecification(webAdminServer)
            .setBasePath(USERS_BASE + SEPARATOR + USERNAME + SEPARATOR + UserMailboxesRoutes.MAILBOXES)
            .build();
    }

    @After
    public void tearDown() {
        webAdminServer.destroy();
    }

    public class NormalBehaviour {

        @Before
        public void setUp() throws Exception {
            InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();

            createServer(inMemoryIntegrationResources.createMailboxManager(new SimpleGroupMembershipResolver()));
        }

        @Test
        public void getMailboxesShouldUserErrorFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get()
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
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        public void getShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME)
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
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        public void putShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME)
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
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        public void deleteShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME)
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
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        public void getShouldReturnUserErrorWithInvalidWildcardMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "*")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void putShouldReturnUserErrorWithInvalidWildcardMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "*")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void deleteShouldReturnUserErrorWithInvalidWildcardMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "*")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void getShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "%")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void putShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "%")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void deleteShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "%")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void getShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "#")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void putShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "#")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void deleteShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "#")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void getShouldReturnUserErrorWithInvalidAndMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "&")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void putShouldReturnUserErrorWithInvalidAndMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "&")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void deleteShouldReturnUserErrorWithInvalidAndMailboxName() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .put(MAILBOX_NAME + "&")
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
                .containsEntry("message", "Attempt to create an invalid mailbox");
        }

        @Test
        public void deleteMailboxesShouldReturnUserErrorWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .delete()
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
                .containsEntry("message", "Invalid delete on user mailboxes");
        }

        @Test
        public void getMailboxesShouldReturnEmptyListByDefault() {
            List<Object> list =
                when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).isEmpty();
        }

        @Test
        public void putShouldReturnNotFoundWhenNoMailboxName() {
            when()
                .put()
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body(containsString(HttpStatus.NOT_FOUND_404 + " Not found"));
        }

        @Test
        public void putShouldReturnNotFoundWhenJustSeparator() {
            when()
                .put(SEPARATOR)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body(containsString(HttpStatus.NOT_FOUND_404 + " Not found"));
        }

        @Test
        public void putShouldReturnOk() {
            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void putShouldReturnOkWhenIssuedTwoTimes() {
            with()
                .put(MAILBOX_NAME);

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void putShouldAddAMailbox() {
            with()
                .put(MAILBOX_NAME);

            when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .body(is("[{\"mailboxName\":\"myMailboxName\"}]"));
        }

        @Test
        public void getShouldReturnNotFoundWhenMailboxDoesNotExist() {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME)
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
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        public void getShouldReturnOkWhenMailboxExists() {
            with()
                .put(MAILBOX_NAME);

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldReturnOkWhenMailboxDoesNotExist() {
            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldReturnOkWhenMailboxExists() {
            with()
                .put(MAILBOX_NAME);

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldRemoveMailbox() {
            with()
                .put(MAILBOX_NAME);

            with()
                .delete(MAILBOX_NAME);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME)
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
                .containsEntry("message", "Invalid get on user mailboxes");
        }

        @Test
        public void deleteMailboxesShouldReturnOkWhenNoMailboxes() {
            when()
                .delete()
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteMailboxesShouldReturnOkWhenMailboxes() {
            with()
                .put(MAILBOX_NAME);

            when()
                .delete()
                .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteMailboxesShouldRemoveAllUserMailboxes() {
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
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).isEmpty();
        }

        @Test
        public void deleteShouldReturnOkWhenMailboxHasChildren() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put(MAILBOX_NAME + ".child");

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldDeleteAMailboxAndItsChildren() {
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
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).isEmpty();
        }

        @Test
        public void deleteShouldNotDeleteUnrelatedMailbox() {
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
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).containsExactly(ImmutableMap.of("mailboxName", mailboxName));
        }

        @Test
        public void deleteShouldReturnOkWhenDeletingChildMailboxes() {
            with()
                .put(MAILBOX_NAME);

            with()
                .put(MAILBOX_NAME + ".child");

            when()
                .delete(MAILBOX_NAME + ".child")
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldBeAbleToRemoveChildMailboxes() {
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
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(list).containsExactly(ImmutableMap.of("mailboxName", MAILBOX_NAME));
        }
    }

    public class ExceptionHandling {

        private MailboxManager mailboxManager;

        @Before
        public void setUp() throws Exception {
            mailboxManager = mock(MailboxManager.class);
            when(mailboxManager.createSystemSession(any())).thenReturn(new MockMailboxSession(USERNAME));

            createServer(mailboxManager);
        }

        @Test
        public void putShouldGenerateInternalErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).createMailbox(any(), any());

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void putShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).createMailbox(any(), any());

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void putShouldReturnOkOnMailboxExists() throws Exception {
            doThrow(new MailboxExistsException(MAILBOX_NAME)).when(mailboxManager).createMailbox(any(), any());

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnUnknownExceptionOnDelete() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(
                                new SimpleMailboxMetaData(
                                        MailboxPath.forUser(USERNAME, MAILBOX_NAME), mailboxId, '.')));
            doThrow(new RuntimeException()).when(mailboxManager).deleteMailbox(any(), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnUnknownExceptionOnSearch() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenThrow(new RuntimeException());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnUnknownMailboxExceptionOnDelete() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(
                                new SimpleMailboxMetaData(MailboxPath.forUser(USERNAME, MAILBOX_NAME), mailboxId, '.')));
            doThrow(new MailboxException()).when(mailboxManager).deleteMailbox(any(), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnUnknownMailboxExceptionOnSearch() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenThrow(new MailboxException());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldReturnOkOnMailboxDoesNotExists() throws Exception {
            doThrow(new MailboxNotFoundException(MAILBOX_NAME)).when(mailboxManager).deleteMailbox(any(), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnUnknownExceptionWhenListingMailboxes() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .delete()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnMailboxExceptionWhenListingMailboxes() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .delete()
                .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }


        @Test
        public void deleteShouldGenerateInternalErrorOnUnknownExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(
                                new SimpleMailboxMetaData(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new RuntimeException()).when(mailboxManager).deleteMailbox(any(), any());

            when()
                .delete()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldReturnOkOnMailboxNotFoundExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(new SimpleMailboxMetaData(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new MailboxNotFoundException("any")).when(mailboxManager).deleteMailbox(any(), any());

            when()
                .delete()
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        public void deleteShouldReturnInternalErrorOnMailboxExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any()))
                .thenReturn(
                        ImmutableList.of(new SimpleMailboxMetaData(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new MailboxException()).when(mailboxManager).deleteMailbox(any(), any());

            when()
                .delete()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getShouldGenerateInternalErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).mailboxExists(any(), any());

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).mailboxExists(any(), any());

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getMailboxesShouldGenerateInternalErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getMailboxesShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            doThrow(new MailboxException()).when(mailboxManager).search(any(MailboxQuery.class), any());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getMailboxesShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void getShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .get(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void putShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

        @Test
        public void deleteMailboxesShouldGenerateInternalErrorOnRepositoryException() throws Exception {
            doThrow(new RuntimeException()).when(usersRepository).contains(USERNAME);

            when()
                .delete()
                .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .body(containsString("500 Internal Server Error"));
        }

    }

}
