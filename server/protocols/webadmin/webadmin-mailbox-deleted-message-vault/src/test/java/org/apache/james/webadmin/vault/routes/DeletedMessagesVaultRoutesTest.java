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

package org.apache.james.webadmin.vault.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETION_DATE;
import static org.apache.james.vault.DeletedMessageFixture.DELIVERY_DATE;
import static org.apache.james.vault.DeletedMessageFixture.FINAL_STAGE;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_1;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_3;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.james.vault.DeletedMessageFixture.USER_2;
import static org.apache.james.vault.DeletedMessageVaultSearchContract.MESSAGE_ID_GENERATOR;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.apache.james.webadmin.vault.routes.RestoreService.RESTORE_MAILBOX_NAME;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT3;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.apache.mailet.base.MailAddressFixture.SENDER2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.memory.MemoryDeletedMessagesVault;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.vault.routes.query.QueryTranslator;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DeletedMessagesVaultRoutesTest {

    private static final String MATCH_ALL_QUERY = "{" +
        "\"combinator\": \"and\"," +
        "\"criteria\": []" +
        "}";

    private WebAdminServer webAdminServer;
    private MemoryDeletedMessagesVault vault;
    private InMemoryMailboxManager mailboxManager;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void beforeEach() throws Exception {
        vault = spy(new MemoryDeletedMessagesVault());
        InMemoryIntegrationResources inMemoryResource = new InMemoryIntegrationResources.Factory().create();
        mailboxManager = spy(inMemoryResource.getMailboxManager());

        taskManager = new MemoryTaskManager();
        JsonTransformer jsonTransformer = new JsonTransformer();

        RestoreService vaultRestore = new RestoreService(vault, mailboxManager);
        QueryTranslator queryTranslator = new QueryTranslator(new InMemoryId.Factory());
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new TasksRoutes(taskManager, jsonTransformer),
            new DeletedMessagesVaultRoutes(vaultRestore, jsonTransformer, taskManager, queryTranslator));

        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(DeletedMessagesVaultRoutes.ROOT_PATH)
            .log(LogDetail.METHOD)
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Nested
    class QueryTest {

        @Nested
        class SubjectTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingSubjectContains() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("subject contains should match")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"contains\"," +
                    "  \"value\": \"subject contains\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenSubjectDoesntContains() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("subject")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"contains\"," +
                    "  \"value\": \"james\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingSubjectContainsIgnoreCase() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("SUBJECT contains should match")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"containsIgnoreCase\"," +
                    "  \"value\": \"subject contains\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenSubjectDoesntContainsIgnoreCase() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("subject")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"containsIgnoreCase\"," +
                    "  \"value\": \"JAMES\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingSubjectEquals() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("subject should match")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"subject should match\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenSubjectDoesntEquals() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("subject")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"SUBJECT\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingSubjectEqualsIgnoreCase() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("SUBJECT should MatCH")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"equalsIgnoreCase\"," +
                    "  \"value\": \"subject should match\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenSubjectDoesntEqualsIgnoreCase() throws Exception {
                vault.append(USER, FINAL_STAGE.get()
                    .subject("subject")
                    .build(), new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"subject\"," +
                    "  \"operator\": \"equalsIgnoreCase\"," +
                    "  \"value\": \"SUBJECT Of the mail\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class DeletionDateTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingDeletionDateBeforeOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deletionDate\"," +
                    "  \"operator\": \"beforeOrEquals\"," +
                    "  \"value\": \"" + DELETION_DATE.plusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenNotMatchingDeletionDateBeforeOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deletionDate\"," +
                    "  \"operator\": \"beforeOrEquals\"," +
                    "  \"value\": \"" + DELETION_DATE.minusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingDeletionDateAfterOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deletionDate\"," +
                    "  \"operator\": \"afterOrEquals\"," +
                    "  \"value\": \"" + DELETION_DATE.minusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenNotMatchingDeletionDateAfterOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deletionDate\"," +
                    "  \"operator\": \"afterOrEquals\"," +
                    "  \"value\": \"" + DELETION_DATE.plusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class DeliveryDateTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingDeliveryDateBeforeOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deliveryDate\"," +
                    "  \"operator\": \"beforeOrEquals\"," +
                    "  \"value\": \"" + DELIVERY_DATE.plusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenNotMatchingDeliveryDateBeforeOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deliveryDate\"," +
                    "  \"operator\": \"beforeOrEquals\"," +
                    "  \"value\": \"" + DELIVERY_DATE.minusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingDeliveryDateAfterOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deliveryDate\"," +
                    "  \"operator\": \"afterOrEquals\"," +
                    "  \"value\": \"" + DELIVERY_DATE.minusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenNotMatchingDeliveryDateAfterOrEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"deliveryDate\"," +
                    "  \"operator\": \"afterOrEquals\"," +
                    "  \"value\": \"" + DELIVERY_DATE.plusHours(1).toString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class RecipientsTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingRecipientContains() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"recipients\"," +
                    "  \"operator\": \"contains\"," +
                    "  \"value\": \"" + RECIPIENT1.asString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenMatchingRecipientsDoNotContain() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"recipients\"," +
                    "  \"operator\": \"contains\"," +
                    "  \"value\": \"" + RECIPIENT3.asString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class SenderTest {
            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingSenderEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"sender\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"" + SENDER.asString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingSenderDoesntEquals() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"sender\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"" + SENDER2.asString() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class HasAttachmentTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingNoAttachment() throws Exception {
                DeletedMessage deletedMessage = messageWithAttachmentBuilder()
                    .hasAttachment(false)
                    .build();
                storeDeletedMessage(deletedMessage);

                String query =
                    "{" +
                    "  \"fieldName\": \"hasAttachment\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"false\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldAppendMessageToMailboxWhenMatchingHasAttachment() throws Exception {
                DeletedMessage deletedMessage = messageWithAttachmentBuilder()
                    .hasAttachment()
                    .build();
                storeDeletedMessage(deletedMessage);

                String query =
                    " {" +
                    "  \"fieldName\": \"hasAttachment\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"true\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenMatchingHasNoAttachment() throws Exception {
                DeletedMessage deletedMessage = messageWithAttachmentBuilder()
                    .hasAttachment(false)
                    .build();
                storeDeletedMessage(deletedMessage);

                String query =
                    "{" +
                    "  \"fieldName\": \"hasAttachment\"," +
                    "  \"operator\": \"equals\"," +
                    "  \"value\": \"true\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class OriginMailboxIdsTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenContainsMailboxId() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"originMailboxes\"," +
                    "  \"operator\": \"contains\"," +
                    "  \"value\": \"" + MAILBOX_ID_1.serialize() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(1)
                    .hasOnlyOneElementSatisfying(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenDoNotContainsMailboxId() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

                String query =
                    "{" +
                    "  \"fieldName\": \"originMailboxes\"," +
                    "  \"operator\": \"contains\"," +
                    "  \"value\": \"" + MAILBOX_ID_3.serialize() + "\"" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }

        @Nested
        class MultipleCriteriaTest {

            @Test
            void restoreShouldAppendMessageToMailboxWhenAllcriteriaAreMatched() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
                vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

                String query = "" +
                    "{" +
                    "  \"combinator\": \"and\"," +
                    "  \"criteria\": [" +
                    "    {" +
                    "      \"fieldName\": \"deliveryDate\"," +
                    "      \"operator\": \"beforeOrEquals\"," +
                    "      \"value\": \"" + DELIVERY_DATE.toString() + "\"" +
                    "    }," +
                    "    {" +
                    "      \"fieldName\": \"recipients\"," +
                    "      \"operator\": \"contains\"," +
                    "      \"value\": \"" + RECIPIENT1.asString() + "\"" +
                    "    }," +
                    "    {" +
                    "      \"fieldName\": \"hasAttachment\"," +
                    "      \"operator\": \"equals\"," +
                    "      \"value\": \"false\"" +
                    "    }," +
                    "    {" +
                    "      \"fieldName\": \"originMailboxes\"," +
                    "      \"operator\": \"contains\"," +
                    "      \"value\": \"" + MAILBOX_ID_1.serialize() + "\"" +
                    "    }" +
                    "  ]" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(restoreMessageContents(USER))
                    .hasSize(2)
                    .allSatisfy(messageIS -> assertThat(messageIS).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
            }

            @Test
            void restoreShouldNotAppendMessageToMailboxWhenASingleCriterionDoesntMatch() throws Exception {
                vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
                vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

                String query = "" +
                    "{" +
                    "  \"combinator\": \"and\"," +
                    "  \"criteria\": [" +
                    "    {" +
                    "      \"fieldName\": \"deliveryDate\"," +
                    "      \"operator\": \"beforeOrEquals\"," +
                    "      \"value\": \"" + DELIVERY_DATE.toString() + "\"" +
                    "    }," +
                    "    {" +
                    "      \"fieldName\": \"recipients\"," +
                    "      \"operator\": \"contains\"," +
                    "      \"value\": \"allMessageDoNotHaveThisRecipient@domain.tld\"" +
                    "    }," +
                    "    {" +
                    "      \"fieldName\": \"hasAttachment\"," +
                    "      \"operator\": \"equals\"," +
                    "      \"value\": \"false\"" +
                    "    }," +
                    "    {" +
                    "      \"fieldName\": \"originMailboxes\"," +
                    "      \"operator\": \"contains\"," +
                    "      \"value\": \"" + MAILBOX_ID_1.serialize() + "\"" +
                    "    }" +
                    "  ]" +
                    "}";

                String taskId =
                    given()
                        .queryParam("action", "restore")
                        .body(query)
                    .when()
                        .post(USER.asString())
                        .jsonPath()
                        .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(hasAnyMail(USER)).isFalse();
            }
        }
    }

    @Nested
    class ValidationTest {

        @Test
        void restoreShouldReturnInvalidWhenActionIsMissing() {
            when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnInvalidWhenPassingEmptyAction() {
            given()
                .queryParam("action", "")
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnInvalidWhenActionIsInValid() {
            given()
                .queryParam("action", "invalid action")
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnInvalidWhenPassingCaseInsensitiveAction() {
            given()
                .queryParam("action", "RESTORE")
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnInvalidWhenUserIsInvalid() {
            given()
                .queryParam("action", "restore")
            .when()
                .post("not@valid@user.com")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void postShouldReturnNotFoundWhenNoUserPathParameter() {
            given()
                .queryParam("action", "restore")
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body("statusCode", is(404))
                .body("type", is(notNullValue()))
                .body("message", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnBadRequestWhenPassingUnsupportedField() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

            String query =
                "{" +
                "  \"criteria\": [" +
                "    {" +
                "      \"fieldName\": \"unsupported\"," +
                "      \"operator\": \"contains\"," +
                "      \"value\": \"" + MAILBOX_ID_1.serialize() + "\"" +
                "    }" +
                "  ]" +
                "}";

            given()
                .body(query)
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnBadRequestWhenPassingUnsupportedOperator() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

            String query =
                "{" +
                "  \"criteria\": [" +
                "    {" +
                "      \"fieldName\": \"subject\"," +
                "      \"operator\": \"isLongerThan\"," +
                "      \"value\": \"" + SUBJECT + "\"" +
                "    }" +
                "  ]" +
                "}";

            given()
                .body(query)
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnBadRequestWhenPassingUnsupportedPairOfFieldNameAndOperator() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

            String query =
                "{" +
                "  \"criteria\": [" +
                "    {" +
                "      \"fieldName\": \"sender\"," +
                "      \"operator\": \"contains\"," +
                "      \"value\": \"" + SENDER.asString() + "\"" +
                "    }" +
                "  ]" +
                "}";

            given()
                .body(query)
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnBadRequestWhenPassingInvalidMailAddress() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

            String query =
                "{" +
                "  \"criteria\": [" +
                "    {" +
                "      \"fieldName\": \"sender\"," +
                "      \"operator\": \"contains\"," +
                "      \"value\": \"invalid@mail@domain.tld\"" +
                "    }" +
                "  ]" +
                "}";

            given()
                .body(query)
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnBadRequestWhenPassingOrCombinator() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

            String query =
                "{" +
                "  \"combinator\": \"or\"," +
                "  \"criteria\": [" +
                "    {" +
                "      \"fieldName\": \"sender\"," +
                "      \"operator\": \"contains\"," +
                "      \"value\": \"" + SENDER.asString() + "\"" +
                "    }" +
                "  ]" +
                "}";

            given()
                .body(query)
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }

        @Test
        void restoreShouldReturnBadRequestWhenPassingNestedStructuredQuery() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();

            String query =
                "{" +
                "  \"combinator\": \"and\"," +
                "  \"criteria\": [" +
                "    {" +
                "      \"combinator\": \"or\"," +
                "      \"criteria\": [" +
                "        {\"fieldName\": \"subject\", \"operator\": \"containsIgnoreCase\", \"value\": \"Apache James\"}," +
                "        {\"fieldName\": \"subject\", \"operator\": \"containsIgnoreCase\", \"value\": \"Apache James\"}" +
                "      ]" +
                "    }," +
                "    {\"fieldName\": \"subject\", \"operator\": \"containsIgnoreCase\", \"value\": \"Apache James\"}" +
                "  ]" +
                "}";

            given()
                .body(query)
            .when()
                .post(USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is(notNullValue()))
                .body("details", is(notNullValue()));
        }
    }

    @Nested
    class FailingRestoreTest {

        @Test
        void restoreShouldProduceFailedTaskWhenTheVaultGetsError() {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
            vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

            doThrow(new RuntimeException("mock exception"))
                .when(vault)
                .search(any(), any());

            String taskId =
                given()
                    .queryParam("action", "restore")
                    .body(MATCH_ALL_QUERY)
                .when()
                    .post(USER.asString())
                    .jsonPath()
                    .get("taskId");

            given()
                .queryParam("action", "restore")
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("failed"))
                .body("taskId", is(taskId))
                .body("type", is(DeletedMessagesVaultRestoreTask.TYPE))
                .body("additionalInformation.successfulRestoreCount", is(0))
                .body("additionalInformation.errorRestoreCount", is(0))
                .body("additionalInformation.user", is(USER.asString()))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()));
        }

        @Test
        void restoreShouldProduceFailedTaskWithErrorRestoreCountWhenMessageAppendGetsError() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
            vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

            MessageManager mockMessageManager = Mockito.mock(MessageManager.class);
            doReturn(mockMessageManager)
                .when(mailboxManager)
                .getMailbox(any(MailboxId.class), any(MailboxSession.class));

            doThrow(new MailboxException("mock exception"))
                .when(mockMessageManager)
                .appendMessage(any(), any());

            String taskId =
                given()
                    .queryParam("action", "restore")
                    .body(MATCH_ALL_QUERY)
                .when()
                    .post(USER.asString())
                    .jsonPath()
                    .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("failed"))
                .body("taskId", is(taskId))
                .body("type", is(DeletedMessagesVaultRestoreTask.TYPE))
                .body("additionalInformation.successfulRestoreCount", is(0))
                .body("additionalInformation.errorRestoreCount", is(2))
                .body("additionalInformation.user", is(USER.asString()))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()));
        }

        @Test
        void restoreShouldProduceFailedTaskWhenMailboxMangerGetsError() throws Exception {
            vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
            vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

            doThrow(new RuntimeException("mock exception"))
                .when(mailboxManager)
                .createMailbox(any(MailboxPath.class), any(MailboxSession.class));

            String taskId =
                given()
                    .queryParam("action", "restore")
                    .body(MATCH_ALL_QUERY)
                .when()
                    .post(USER.asString())
                    .jsonPath()
                    .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("failed"))
                .body("taskId", is(taskId))
                .body("type", is(DeletedMessagesVaultRestoreTask.TYPE))
                .body("additionalInformation.successfulRestoreCount", is(0))
                .body("additionalInformation.errorRestoreCount", is(0))
                .body("additionalInformation.user", is(USER.asString()))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()));
        }
    }

    @Test
    void restoreShouldReturnATaskCreated() {
        given()
            .queryParam("action", "restore")
            .body(MATCH_ALL_QUERY)
        .when()
            .post(USER.asString())
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void restoreShouldProduceASuccessfulTaskWithAdditionalInformation() {
        vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
        vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

        String taskId =
            given()
                .queryParam("action", "restore")
                .body(MATCH_ALL_QUERY)
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is(DeletedMessagesVaultRestoreTask.TYPE))
            .body("additionalInformation.successfulRestoreCount", is(2))
            .body("additionalInformation.errorRestoreCount", is(0))
            .body("additionalInformation.user", is(USER.asString()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void restoreShouldKeepAllMessagesInTheVaultOfCorrespondingUser() {
        vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
        vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

        String taskId =
            given()
                .queryParam("action", "restore")
                .body(MATCH_ALL_QUERY)
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        assertThat(Flux.from(vault.search(USER, Query.ALL)).toStream())
            .containsOnly(DELETED_MESSAGE, DELETED_MESSAGE_2);
    }

    @Test
    void restoreShouldNotDeleteExistingMessagesInTheUserMailbox() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USER.asString());
        MailboxPath restoreMailboxPath = MailboxPath.forUser(USER.asString(), RESTORE_MAILBOX_NAME);
        mailboxManager.createMailbox(restoreMailboxPath, session);
        MessageManager messageManager = mailboxManager.getMailbox(restoreMailboxPath, session);
        messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(new ByteArrayInputStream(CONTENT)),
            session);

        vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
        vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

        String taskId =
            given()
                .queryParam("action", "restore")
                .body(MATCH_ALL_QUERY)
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        assertThat(restoreMailboxMessages(USER))
            .hasSize(3);
    }

    @Test
    void restoreShouldAppendAllMessageFromVaultToRestoreMailboxOfCorrespondingUser() throws Exception {
        vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
        vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

        String taskId =
            given()
                .queryParam("action", "restore")
                .body(MATCH_ALL_QUERY)
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        assertThat(restoreMailboxMessages(USER))
            .hasSize(2)
            .anySatisfy(messageResult -> assertThat(fullContent(messageResult)).hasSameContentAs(new ByteArrayInputStream(CONTENT)))
            .anySatisfy(messageResult -> assertThat(fullContent(messageResult)).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
    }

    @Test
    void restoreShouldNotAppendMessagesToAnOtherUserMailbox() throws Exception {
        vault.append(USER, DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)).block();
        vault.append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)).block();

        String taskId =
            given()
                .queryParam("action", "restore")
                .body(MATCH_ALL_QUERY)
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        assertThat(hasAnyMail(USER_2))
            .isFalse();
    }

    private boolean hasAnyMail(User user) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.asString());
        int limitToOneMessage = 1;

        return !mailboxManager.search(MultimailboxesSearchQuery.from(new SearchQuery()).build(), session, limitToOneMessage)
            .isEmpty();
    }

    private InputStream fullContent(MessageResult messageResult) {
        try {
            return messageResult.getFullContent().getInputStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<InputStream> restoreMessageContents(User user) throws Exception {
        return restoreMailboxMessages(user).stream()
            .map(this::fullContent);
    }

    private List<MessageResult> restoreMailboxMessages(User user) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(user.asString());
        MessageManager messageManager = mailboxManager.getMailbox(MailboxPath.forUser(user.asString(), RESTORE_MAILBOX_NAME), session);
        return ImmutableList.copyOf(messageManager.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session));
    }

    private DeletedMessage.Builder.RequireHasAttachment<DeletedMessage.Builder.FinalStage> messageWithAttachmentBuilder() {
        return DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
            .originMailboxes(MAILBOX_ID_1)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.of(SENDER))
            .recipients(RECIPIENT1, RECIPIENT2);
    }

    private DeletedMessage storeDeletedMessage(DeletedMessage deletedMessage) {
        Mono.from(vault.append(USER, deletedMessage, new ByteArrayInputStream(CONTENT)))
            .block();
        return deletedMessage;
    }
}