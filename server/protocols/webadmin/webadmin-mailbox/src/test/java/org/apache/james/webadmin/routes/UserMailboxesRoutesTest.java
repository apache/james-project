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
import static javax.mail.Flags.Flag.SEEN;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USERS_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.mail.Flags;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.core.Username;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.MailboxOpenSearchConstants;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.dto.WebAdminUserReindexingTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ClearMailboxContentTask;
import org.apache.james.webadmin.service.ClearMailboxContentTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.UserReindexingTask;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UserMailboxesRoutesTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesRoutesTest.class);

    public static MailboxMetaData testMetadata(MailboxPath path, MailboxId mailboxId, char delimiter) {
        return new MailboxMetaData(new Mailbox(path, UidValidity.of(45), mailboxId), delimiter, MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.NONE, new MailboxACL(),
            MailboxCounters.empty(mailboxId));
    }

    private static final Username USERNAME = Username.of("username");
    private static final String MAILBOX_NAME = "myMailboxName";
    private static final String MAILBOX_NAME_WITH_DOTS = "my..MailboxName";
    private static final String INVALID_MAILBOX_NAME = "#myMailboxName";
    private static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);
    private static final String ERROR_TYPE_NOTFOUND = "notFound";
    
    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;
    private MemoryTaskManager taskManager;

    private void createServer(MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory, MailboxId.Factory mailboxIdFactory, ListeningMessageSearchIndex searchIndex) throws Exception {
        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(USERNAME)).thenReturn(true);

        taskManager = new MemoryTaskManager(new Hostname("foo"));
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
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(WebAdminUserReindexingTaskAdditionalInformationDTO.serializationModule(),
                        ClearMailboxContentTaskAdditionalInformationDTO.SERIALIZATION_MODULE)))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(USERS_BASE + SEPARATOR + USERNAME.asString() + SEPARATOR + UserMailboxesRoutes.MAILBOXES)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Nested
    class NormalBehaviour {

        private MailboxManager mailboxManager;

        @BeforeEach
        void setUp() throws Exception {
            InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
            ListeningMessageSearchIndex searchIndex = mock(ListeningMessageSearchIndex.class);
            Mockito.when(searchIndex.add(any(), any(), any())).thenReturn(Mono.empty());
            Mockito.when(searchIndex.deleteAll(any(), any())).thenReturn(Mono.empty());

            createServer(mailboxManager, mailboxManager.getMapperFactory(), new InMemoryId.Factory(), searchIndex);
            this.mailboxManager = mailboxManager;
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
        void putShouldThrowWhenMailboxNameWithDots() {
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
                .put(URLEncoder.encode(INVALID_MAILBOX_NAME))
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
                .containsEntry("details", "#private:username:#myMailboxName contains one of the forbidden characters %*\r\n or starts with #");
        }

        @Test
        void deleteShouldReturnNotFoundWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .delete(MAILBOX_NAME)
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
                .containsEntry("message", "Invalid delete on user mailboxes")
                .containsEntry("details", "User does not exist");
        }

        @Test
        void putShouldReturn204WhenForceNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            given()
                .queryParam("force")
            .when()
                .put(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturn204WhenForceNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            given()
                .queryParam("force")
            .when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void getShouldReturnUserErrorWithInvalidWildcardMailboxName() {
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
                .containsEntry("details", "#private:username:myMailboxName* contains one of the forbidden characters %*\r\n or starts with #");
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
                .get(URLEncoder.encode(MAILBOX_NAME + "%"))
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
                .containsEntry("details", "#private:username:myMailboxName% contains one of the forbidden characters %*\r\n or starts with #");
        }

        @Test
        void putShouldReturnUserErrorWithInvalidPercentMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(URLEncoder.encode(MAILBOX_NAME + "%"))
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
                .put(URLEncoder.encode(MAILBOX_NAME + "%"))
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
                .get(URLEncoder.encode("#" + MAILBOX_NAME))
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
                .containsEntry("details", "#private:username:#myMailboxName contains one of the forbidden characters %*\r\n or starts with #");
        }

        @Test
        void getShouldReturnOkWhenSharpInTheMiddleOfTheName() throws Exception {
            with()
                .put("a#b");

            when()
                .get("a#b")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void putShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(URLEncoder.encode("#" + MAILBOX_NAME))
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
        void putShouldAcceptMailboxNamesContainingSharp() throws Exception {
            when()
                .put("a#b")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnUserErrorWithInvalidSharpMailboxName() throws Exception {
            Map<String, Object> errors = when()
                .put(URLEncoder.encode("#" + MAILBOX_NAME))
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
        void deleteShouldAcceptSharpInTheMiddleOfTheName() throws Exception {
            when()
                .put("a#b")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void getShouldReturnNotFoundWithAndMailboxName() throws Exception {
            when()
                .get(MAILBOX_NAME + "&")
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void putShouldReturnSuccessWithAndMailboxName() throws Exception {
            when()
                .put(MAILBOX_NAME + "&")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnSuccessWithAndMailboxName() throws Exception {
            when()
                .put(MAILBOX_NAME + "&")
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON);
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
        void deleteMailboxesShouldReturn204UserErrorWithNonExistingUser() throws Exception {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            given()
                .queryParam("force")
            .when()
                .delete()
            .then()
                .statusCode(NO_CONTENT_204);
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

        @Test
        void getMessageCountShouldReturnZeroWhenMailBoxEmpty() {
            with()
                .put(MAILBOX_NAME);

            String response = when()
                .get(MAILBOX_NAME + "/messageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body().asString();

            assertThat(response)
                .isEqualTo("0");
        }

        @Test
        void getMessageCountShouldReturnTotalEmailsInMailBox() {
            with()
                .put(MAILBOX_NAME);

            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            IntStream.range(0, 10)
                .forEach(index -> {
                    try {
                        mailboxManager.getMailbox(mailboxPath, systemSession)
                            .appendMessage(
                                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                                systemSession);
                    } catch (MailboxException e) {
                        LOGGER.warn("Error when append message " + e);
                    }
                });

            String response = when()
                .get(MAILBOX_NAME + "/messageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body().asString();

            assertThat(response)
                .isEqualTo("10");
        }

        @Test
        void getMessageCountShouldReturnErrorWhenUserIsNotFound() throws UsersRepositoryException {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "/messageCount")
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
        void getMessageCountShouldReturnErrorWhenMailboxDoesNotExist() {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "/messageCount")
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
                .containsEntry("details", String.format("#private:%s:%s can not be found", USERNAME.asString(), MAILBOX_NAME));
        }

        @Test
        void getUnseenMessageCountShouldReturnZeroWhenMailBoxEmpty() {
            with()
                .put(MAILBOX_NAME);

            String response = when()
                .get(MAILBOX_NAME + "/unseenMessageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body().asString();

            assertThat(response)
                .isEqualTo("0");
        }

        @Test
        void getUnseenMessageCountShouldReturnZeroWhenMailBoxDoNotHaveAnyUnSeenEmail() {
            with()
                .put(MAILBOX_NAME);

            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            IntStream.range(0, 10)
                .forEach(index -> {
                    try {
                        mailboxManager.getMailbox(mailboxPath, systemSession)
                            .appendMessage(
                                MessageManager.AppendCommand.builder()
                                    .withFlags(new Flags(SEEN))
                                    .build("header: value\r\n\r\nbody"),
                                systemSession);
                    } catch (MailboxException e) {
                        LOGGER.warn("Error when append message " + e);
                    }
                });

            String response = when()
                .get(MAILBOX_NAME + "/unseenMessageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body().asString();

            assertThat(response)
                .isEqualTo("0");
        }

        @Test
        void getUnseenMessageCountShouldReturnNumberOfUnSeenEmails() {
            with()
                .put(MAILBOX_NAME);

            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            IntStream.range(0, 5)
                .forEach(index -> {
                    try {
                        mailboxManager.getMailbox(mailboxPath, systemSession)
                            .appendMessage(
                                MessageManager.AppendCommand.builder()
                                    .withFlags(new Flags(SEEN))
                                    .build("header: value\r\n\r\nbody"),
                                systemSession);
                    } catch (MailboxException e) {
                        LOGGER.warn("Error when append message " + e);
                    }
                });

            IntStream.range(0, 10)
                .forEach(index -> {
                    try {
                        mailboxManager.getMailbox(mailboxPath, systemSession)
                            .appendMessage(
                                MessageManager.AppendCommand.builder()
                                    .build("header: value\r\n\r\nbody"),
                                systemSession);
                    } catch (MailboxException e) {
                        LOGGER.warn("Error when append message " + e);
                    }
                });

            String response = when()
                .get(MAILBOX_NAME + "/unseenMessageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body().asString();

            assertThat(response)
                .isEqualTo("10");
        }

        @Test
        void getUnseenMessageCountShouldReturnErrorWhenUserIsNotFound() throws UsersRepositoryException {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "/unseenMessageCount")
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
        void getUnseenMessageCountShouldReturnErrorWhenMailboxDoesNotExist() {
            Map<String, Object> errors = when()
                .get(MAILBOX_NAME + "/unseenMessageCount")
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
                .containsEntry("details", String.format("#private:%s:%s can not be found", USERNAME.asString(), MAILBOX_NAME));
        }

        @Test
        void deleteMailboxContentShouldReturnErrorWhenUserIsNotFound() throws UsersRepositoryException {
            when(usersRepository.contains(USERNAME)).thenReturn(false);

            Map<String, Object> errors = when()
                .delete(MAILBOX_NAME + "/messages")
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
        void deleteMailboxContentShouldReturnErrorWhenMailboxDoesNotExist() {
            Map<String, Object> errors = when()
                .delete(MAILBOX_NAME + "/messages")
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
                .containsEntry("details", String.format("Mailbox does not exist. #private:%s:%s", USERNAME.asString(), MAILBOX_NAME));
        }

        @Test
        void deleteMailboxContentShouldReturnTaskId() {
            with()
                .put(MAILBOX_NAME);

            String taskId = when()
                .delete(MAILBOX_NAME + "/messages")
            .then()
                .statusCode(CREATED_201)
                .extract()
                .jsonPath()
                .get("taskId");

            assertThat(taskId)
                .isNotEmpty();
        }
    }

    @Nested
    class ExceptionHandling {
        private MailboxManager mailboxManager;

        @BeforeEach
        void setUp() throws Exception {
            mailboxManager = mock(MailboxManager.class);
            when(mailboxManager.createSystemSession(any())).thenReturn(MailboxSessionUtil.create(USERNAME));
            ListeningMessageSearchIndex searchIndex = mock(ListeningMessageSearchIndex.class);
            Mockito.when(searchIndex.add(any(), any(), any())).thenReturn(Mono.empty());
            Mockito.when(searchIndex.deleteAll(any(), any())).thenReturn(Mono.empty());

            createServer(mailboxManager, mock(MailboxSessionMapperFactory.class), new InMemoryId.Factory(), searchIndex);
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
                        Flux.just(
                                testMetadata(
                                        MailboxPath.forUser(USERNAME, MAILBOX_NAME), mailboxId, '.')));
            doThrow(new RuntimeException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownExceptionOnSearch() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenReturn(Flux.error(new RuntimeException()));

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
                        Flux.just(
                            testMetadata(MailboxPath.forUser(USERNAME, MAILBOX_NAME), mailboxId, '.')));
            doThrow(new MailboxException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownMailboxExceptionOnSearch() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenReturn(Flux.error(new MailboxException()));

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnOkOnMailboxDoesNotExists() throws Exception {
            MailboxMetaData metaData = mock(MailboxMetaData.class);
            when(metaData.getPath()).thenReturn(MailboxPath.forUser(USERNAME, MAILBOX_NAME));
            doReturn(Flux.just(metaData))
                .when(mailboxManager).search(any(MailboxQuery.class), any(), any(MailboxSession.class));
            doThrow(new MailboxNotFoundException(MAILBOX_NAME)).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete(MAILBOX_NAME)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnUnknownExceptionWhenListingMailboxes() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenReturn(Flux.error(new RuntimeException()));

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldGenerateInternalErrorOnMailboxExceptionWhenListingMailboxes() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenReturn(Flux.error(new MailboxException()));

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
                        Flux.just(
                            testMetadata(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
            doThrow(new RuntimeException()).when(mailboxManager).deleteMailbox(any(MailboxPath.class), any());

            when()
                .delete()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnOkOnMailboxNotFoundExceptionWhenRemovingMailboxes() throws Exception {
            MailboxId mailboxId = InMemoryId.of(12);
            when(mailboxManager.search(any(MailboxQuery.class), any(), any()))
                .thenReturn(
                        Flux.just(testMetadata(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
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
                        Flux.just(testMetadata(MailboxPath.forUser(USERNAME, "any"), mailboxId, '.')));
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
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenReturn(Flux.error(new RuntimeException()));

            when()
                .get()
            .then()
                .statusCode(INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getMailboxesShouldGenerateInternalErrorOnUnknownMailboxException() throws Exception {
            when(mailboxManager.search(any(MailboxQuery.class), any())).thenReturn(Flux.error(new MailboxException()));

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
    class UserReIndexing {
        static final int SEARCH_SIZE = 1;

        @RegisterExtension
        DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(
            new DockerOpenSearchExtension.DeleteAllIndexDocumentsCleanupStrategy(new WriteAliasName("mailboxWriteAlias")));

        private InMemoryMailboxManager mailboxManager;
        private ListeningMessageSearchIndex searchIndex;
        MessageIdManager messageIdManager;

        @BeforeEach
        void setUp() throws Exception {
            ReactorOpenSearchClient client = MailboxIndexCreationUtil.prepareDefaultClient(
                openSearch.getDockerOpenSearch().clientProvider().get(),
                openSearch.getDockerOpenSearch().configuration());

            InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
            MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

            InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
                .preProvisionnedFakeAuthenticator()
                .fakeAuthorizator()
                .inVmEventBus()
                .defaultAnnotationLimits()
                .defaultMessageParser()
                .listeningSearchIndex(preInstanciationStage -> new OpenSearchListeningMessageSearchIndex(
                    preInstanciationStage.getMapperFactory(),
                    ImmutableSet.of(),
                    new OpenSearchIndexer(client,
                        MailboxOpenSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
                    new OpenSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                        MailboxOpenSearchConstants.DEFAULT_MAILBOX_READ_ALIAS, routingKeyFactory),
                    new MessageToOpenSearchJson(new DefaultTextExtractor(), ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                    preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory, OpenSearchMailboxConfiguration.builder().build(), new RecordingMetricFactory()))
                .noPreDeletionHooks()
                .storeQuotaManager()
                .build();

            mailboxManager = resources.getMailboxManager();
            messageIdManager = resources.getMessageIdManager();
            searchIndex = spy((ListeningMessageSearchIndex) resources.getSearchIndex());

            createServer(mailboxManager, mailboxManager.getMapperFactory(), new InMemoryId.Factory(), searchIndex);
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
                    .body("details", Matchers.is("Domain parts ASCII chars must be a-z A-Z 0-9 - or _"));
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
                    .body("additionalInformation.runningOptions.messagesPerSecond", Matchers.is(50))
                    .body("additionalInformation.runningOptions.mode", Matchers.is("REBUILD_ALL"))
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
                    .body("additionalInformation.runningOptions.messagesPerSecond", Matchers.is(50))
                    .body("additionalInformation.runningOptions.mode", Matchers.is("REBUILD_ALL"))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()))
                    .body("completedDate", Matchers.is(notNullValue()));
            }

            @Test
            void userReprocessingWithMessagesPerSecondShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = given()
                    .queryParam("task", "reIndex")
                    .queryParam("messagesPerSecond", 1)
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
                    .body("additionalInformation.runningOptions.messagesPerSecond", Matchers.is(1))
                    .body("additionalInformation.runningOptions.mode", Matchers.is("REBUILD_ALL"))
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
                    .body("additionalInformation.runningOptions.messagesPerSecond", Matchers.is(50))
                    .body("additionalInformation.runningOptions.mode", Matchers.is("REBUILD_ALL"))
                    .body("additionalInformation.messageFailures.\"" + mailboxId.serialize() + "\"[0].uid", Matchers.is(Long.valueOf(uidAsLong).intValue()))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()));
            }

            @Test
            void userReprocessingShouldReturnTaskDetailsWhenFailingAtTheMailboxLevel() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                doReturn(Mono.error(new RuntimeException()))
                    .when(searchIndex)
                    .deleteAll(any(MailboxSession.class), any(MailboxId.class));

                String taskId = with()
                    .queryParam("task", "reIndex")
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", Matchers.is("failed"))
                    .body("taskId", Matchers.is(notNullValue()))
                    .body("additionalInformation.mailboxFailures", Matchers.containsInAnyOrder(mailboxId.serialize()));
            }

            @Test
            void userReprocessingWithCorrectModeShouldReturnTaskDetailsWhenMails() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                Mailbox mailbox = mailboxManager.getMailbox(mailboxId, systemSession).getMailboxEntity();

                ComposedMessageId result = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession)
                    .getId();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(result.getMessageId()), FetchGroup.MINIMAL, systemSession);

                Flags newFlags = new Flags(Flags.Flag.DRAFT);
                UpdatedFlags updatedFlags = UpdatedFlags.builder()
                    .uid(result.getUid())
                    .modSeq(messages.get(0).getModSeq())
                    .oldFlags(new Flags())
                    .newFlags(newFlags)
                    .build();

                // We update on the searchIndex level to try to create inconsistencies
                searchIndex.update(systemSession, mailbox.getMailboxId(), ImmutableList.of(updatedFlags)).block();

                String taskId = with()
                    .queryParam("task", "reIndex")
                    .queryParam("mode", "fixOutdated")
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
                    .body("additionalInformation.successfullyReprocessedMailCount", Matchers.is(2))
                    .body("additionalInformation.failedReprocessedMailCount", Matchers.is(0))
                    .body("additionalInformation.runningOptions.messagesPerSecond", Matchers.is(50))
                    .body("additionalInformation.runningOptions.mode", Matchers.is("FIX_OUTDATED"))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()))
                    .body("completedDate", Matchers.is(notNullValue()));
            }

            @Test
            void userReprocessingWithCorrectModeShouldFixInconsistenciesInES() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                Mailbox mailbox = mailboxManager.getMailbox(mailboxId, systemSession).getMailboxEntity();

                ComposedMessageId result = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession)
                    .getId();

                Flags initialFlags = searchIndex.retrieveIndexedFlags(mailbox, result.getUid()).block();

                List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(result.getMessageId()), FetchGroup.MINIMAL, systemSession);

                Flags newFlags = new Flags(Flags.Flag.DRAFT);
                UpdatedFlags updatedFlags = UpdatedFlags.builder()
                    .uid(result.getUid())
                    .modSeq(messages.get(0).getModSeq())
                    .oldFlags(new Flags())
                    .newFlags(newFlags)
                    .build();

                // We update on the searchIndex level to try to create inconsistencies
                searchIndex.update(systemSession, mailbox.getMailboxId(), ImmutableList.of(updatedFlags)).block();

                String taskId = with()
                    .queryParam("task", "reIndex")
                    .queryParam("mode", "fixOutdated")
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await");

                assertThat(searchIndex.retrieveIndexedFlags(mailbox, result.getUid()).block())
                    .isEqualTo(initialFlags);
            }

            @Test
            void userReprocessingWithCorrectModeShouldNotChangeDocumentsInESWhenNoInconsistencies() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                Mailbox mailbox = mailboxManager.getMailbox(mailboxId, systemSession).getMailboxEntity();

                ComposedMessageId result = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession)
                    .getId();

                Flags initialFlags = searchIndex.retrieveIndexedFlags(mailbox, result.getUid()).block();

                String taskId = with()
                    .queryParam("task", "reIndex")
                    .queryParam("mode", "fixOutdated")
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await");

                assertThat(searchIndex.retrieveIndexedFlags(mailbox, result.getUid()).block())
                    .isEqualTo(initialFlags);
            }

            @Disabled("JAMES-3202 Limitation of the current correct mode reindexation. We only check metadata and fix "
                + "inconsistencies with ES, but we don't check for inconsistencies from ES to metadata")
            @Test
            void userReprocessingWithCorrectModeShouldRemoveOrphanMessagesInES() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                Mailbox mailbox = mailboxManager.getMailbox(mailboxId, systemSession).getMailboxEntity();

                byte[] content = "Simple message content".getBytes(StandardCharsets.UTF_8);
                MessageUid uid = MessageUid.of(22L);

                SimpleMailboxMessage message = SimpleMailboxMessage.builder()
                    .messageId(InMemoryMessageId.of(42L))
                    .threadId(ThreadId.fromBaseMessageId(InMemoryMessageId.of(42L)))
                    .uid(uid)
                    .content(new ByteContent(content))
                    .size(content.length)
                    .internalDate(new Date(ZonedDateTime.parse("2018-02-15T15:54:02Z").toEpochSecond()))
                    .bodyStartOctet(0)
                    .flags(new Flags("myFlags"))
                    .properties(new PropertyBuilder())
                    .mailboxId(mailboxId)
                    .build();

                searchIndex.add(systemSession, mailbox, message).block();

                String taskId = with()
                    .queryParam("task", "reIndex")
                    .queryParam("mode", "fixOutdated")
                    .post()
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await");

                assertThat(searchIndex.retrieveIndexedFlags(mailbox, uid).blockOptional())
                    .isEmpty();
            }

            @Test
            void userReprocessingShouldReturnTaskDetailWhenDeleteMailboxContentWithNoEmail() {
                with()
                    .put(MAILBOX_NAME);

                String taskId = when()
                    .delete(MAILBOX_NAME + "/messages")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", Matchers.is("completed"))
                    .body("taskId", Matchers.is(notNullValue()))
                    .body("type", Matchers.is(ClearMailboxContentTask.TASK_TYPE.asString()))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()))
                    .body("completedDate", Matchers.is(notNullValue()))
                    .body("additionalInformation.username", Matchers.is(USERNAME.asString()))
                    .body("additionalInformation.mailboxName", Matchers.is(MAILBOX_NAME))
                    .body("additionalInformation.messagesSuccessCount", Matchers.is(0))
                    .body("additionalInformation.messagesFailCount", Matchers.is(0));
            }

            @Test
            void userReprocessingShouldReturnTaskDetailWhenDeleteMailboxContentWithHasEmails() {
                with()
                    .put(MAILBOX_NAME);

                MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

                IntStream.range(0, 10)
                    .forEach(index -> {
                        try {
                            mailboxManager.getMailbox(mailboxPath, systemSession)
                                .appendMessage(
                                    MessageManager.AppendCommand.builder()
                                        .build("header: value\r\n\r\nbody"),
                                    systemSession);
                        } catch (MailboxException e) {
                            LOGGER.warn("Error when append message " + e);
                        }
                    });

                String taskId = when()
                    .delete(MAILBOX_NAME + "/messages")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                    .then()
                    .body("status", Matchers.is("completed"))
                    .body("taskId", Matchers.is(notNullValue()))
                    .body("type", Matchers.is(ClearMailboxContentTask.TASK_TYPE.asString()))
                    .body("startedDate", Matchers.is(notNullValue()))
                    .body("submitDate", Matchers.is(notNullValue()))
                    .body("completedDate", Matchers.is(notNullValue()))
                    .body("additionalInformation.username", Matchers.is(USERNAME.asString()))
                    .body("additionalInformation.mailboxName", Matchers.is(MAILBOX_NAME))
                    .body("additionalInformation.messagesSuccessCount", Matchers.is(10))
                    .body("additionalInformation.messagesFailCount", Matchers.is(0));
            }

            @Test
            void userReprocessingShouldEmptyMailboxWhenDeleteMailboxContent() throws MailboxException {
                with()
                    .put(MAILBOX_NAME);

                MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

                mailboxManager.getMailbox(mailboxPath, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder()
                            .build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = when()
                    .delete(MAILBOX_NAME + "/messages")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await");

                assertThat(mailboxManager.getMailbox(mailboxPath, systemSession).getMailboxCounters(systemSession).getCount())
                    .isZero();
            }

            @Test
            void userReprocessingShouldNotClearUnRelatedMailboxWhenDeleteMailboxContent() throws MailboxException {
                with()
                    .put(MAILBOX_NAME);

                String unRelatedMailbox = "unRelatedMailbox";
                with()
                    .put(unRelatedMailbox);
                MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, unRelatedMailbox);
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

                mailboxManager.getMailbox(mailboxPath, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder()
                            .build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = when()
                    .delete(MAILBOX_NAME + "/messages")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await");

                assertThat(mailboxManager.getMailbox(mailboxPath, systemSession).getMailboxCounters(systemSession).getCount())
                    .isEqualTo(1);
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
