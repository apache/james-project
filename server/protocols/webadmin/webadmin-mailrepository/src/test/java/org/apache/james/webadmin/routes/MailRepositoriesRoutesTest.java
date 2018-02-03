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
import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.ClearMailRepositoryTask;
import org.apache.james.webadmin.service.MailRepositoryStoreService;
import org.apache.james.webadmin.service.ReprocessingAllMailsTask;
import org.apache.james.webadmin.service.ReprocessingOneMailTask;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public class MailRepositoriesRoutesTest {

    public static final String URL_MY_REPO = "url://myRepo";
    public static final String URL_ESCAPED_MY_REPO = "url%3A%2F%2FmyRepo";
    public static final String MY_REPO_MAILS = "url%3A%2F%2FmyRepo/mails";
    public static final String CUSTOM_QUEUE = "customQueue";
    public static final String NAME_1 = "name1";
    public static final String NAME_2 = "name2";
    private WebAdminServer webAdminServer;
    private MailRepositoryStore mailRepositoryStore;
    private MemoryMailRepository mailRepository;
    private ManageableMailQueue spoolQueue;
    private ManageableMailQueue customQueue;

    @Before
    public void setUp() throws Exception {
        mailRepositoryStore = mock(MailRepositoryStore.class);
        mailRepository = new MemoryMailRepository();

        MemoryTaskManager taskManager = new MemoryTaskManager();
        JsonTransformer jsonTransformer = new JsonTransformer();
        MailQueueFactory<ManageableMailQueue> queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        spoolQueue = queueFactory.createQueue(MailQueueFactory.SPOOL);
        customQueue = queueFactory.createQueue(CUSTOM_QUEUE);

        MailRepositoryStoreService repositoryStoreService = new MailRepositoryStoreService(mailRepositoryStore);

        ReprocessingService reprocessingService = new ReprocessingService(queueFactory, repositoryStoreService);

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new NoopMetricFactory(),
                new MailRepositoriesRoutes(repositoryStoreService,
                    jsonTransformer, reprocessingService, taskManager),
            new TasksRoutes(taskManager, jsonTransformer));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .build();
    }

    @After
    public void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    public void getMailRepositoriesShouldReturnEmptyWhenEmpty() {
        List<Object> mailRepositories =
            when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".");

        assertThat(mailRepositories).isEmpty();
    }

    @Test
    public void getMailRepositoriesShouldReturnRepositoryWhenOne() {
        when(mailRepositoryStore.getUrls())
            .thenReturn(ImmutableList.of(URL_MY_REPO));

        when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].repository", is(URL_MY_REPO))
            .body("[0].id", is(URL_ESCAPED_MY_REPO));
    }

    @Test
    public void getMailRepositoriesShouldReturnTwoRepositoriesWhenTwo() {
        ImmutableList<String> myRepositories = ImmutableList.of(URL_MY_REPO, "url://mySecondRepo");
        when(mailRepositoryStore.getUrls())
            .thenReturn(myRepositories);

        List<String> mailRepositories =
            when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList("repository");

        assertThat(mailRepositories).containsOnlyElementsOf(myRepositories);
    }

    @Test
    public void listingKeysShouldReturnNotFoundWhenNoRepository() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.empty());

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void listingKeysShouldReturnEmptyWhenNoMail() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    public void listingKeysShouldReturnContainedKeys() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(2))
            .body("mailKey", containsInAnyOrder("name1", "name2"));
    }

    @Test
    public void listingKeysShouldApplyLimitAndOffset() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name3")
            .build());

        given()
            .param("limit", "1")
            .param("offset", "1")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("mailKey", contains("name2"));
    }

    @Test
    public void listingKeysShouldHandleErrorGracefully() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO))
            .thenThrow(new MailRepositoryStore.MailRepositoryStoreException("message"));

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
            .body("statusCode", is(500))
            .body("type", is(ErrorResponder.ErrorType.SERVER_ERROR.getType()))
            .body("message", is("Error while listing keys"))
            .body("cause", containsString("message"));
    }

    @Test
    public void listingKeysShouldReturnErrorOnInvalidOffset() throws Exception {
        given()
            .param("offset", "invalid")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Can not parse offset"));
    }

    @Test
    public void listingKeysShouldReturnErrorOnNegativeOffset() throws Exception {
        given()
            .param("offset", "-1")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("offset can not be negative"));
    }

    @Test
    public void listingKeysShouldReturnEmptyWhenOffsetExceedsSize() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name3")
            .build());

        given()
            .param("offset", "5")
        .when()
            .get(MY_REPO_MAILS)
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    public void listingKeysShouldReturnErrorOnInvalidLimit() throws Exception {
        given()
            .param("limit", "invalid")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Can not parse limit"));
    }

    @Test
    public void listingKeysShouldReturnErrorOnNegativeLimit() throws Exception {
        given()
            .param("limit", "-1")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("limit can not be negative"));
    }

    @Test
    public void listingKeysShouldIgnoreZeroedOffset() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        given()
            .param("offset", "0")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(2))
            .body("mailKey", containsInAnyOrder(NAME_1, NAME_2));
    }

    @Test
    public void zeroLimitShouldNotBeValid() throws Exception {
        given()
            .param("limit", "0")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("limit can not be equal to zero"));
    }

    @Test
    public void retrievingRepositoryShouldReturnNotFoundWhenNone() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.empty());

        given()
            .get(URL_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void retrievingRepositoryShouldReturnBasicInformation() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        given()
            .get(URL_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("repository", is(URL_MY_REPO))
            .body("id", is(URL_ESCAPED_MY_REPO));
    }

    @Test
    public void retrievingRepositorySizeShouldReturnZeroWhenEmpty() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        given()
            .get(URL_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("size", equalTo(0));
    }

    @Test
    public void retrievingRepositorySizeShouldReturnNumberOfContainedMails() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        given()
            .get(URL_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("size", equalTo(1));
    }

    @Test
    public void retrievingAMailShouldDisplayItsInformation() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        String name = NAME_1;
        String sender = "sender@domain";
        String recipient1 = "recipient1@domain";
        String recipient2 = "recipient2@domain";
        String state = "state";
        String errorMessage = "Error: why this mail is stored";
        mailRepository.store(FakeMail.builder()
            .name(name)
            .sender(sender)
            .recipients(recipient1, recipient2)
            .state(state)
            .errorMessage(errorMessage)
            .build());

        when()
            .get(URL_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(name))
            .body("sender", is(sender))
            .body("state", is(state))
            .body("error", is(errorMessage))
            .body("recipients", containsInAnyOrder(recipient1, recipient2));
    }

    @Test
    public void retrievingAMailShouldNotFailWhenOnlyNameProperty() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        when()
            .get(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(NAME_1))
            .body("sender", isEmptyOrNullString())
            .body("state", isEmptyOrNullString())
            .body("error", isEmptyOrNullString())
            .body("recipients", hasSize(0));
    }

    @Test
    public void retrievingAMailShouldFailWhenUnknown() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        String name = "name";
        when()
            .get(URL_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("Could not retrieve " + name));
    }

    @Test
    public void deletingAMailShouldRemoveIt() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        given()
            .delete(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1);

        when()
            .get(URL_ESCAPED_MY_REPO + "/mails")
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("mailKey", contains(NAME_2));
    }

    @Test
    public void deletingAMailShouldReturnOkWhenExist() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deletingAMailShouldReturnOkWhenNotExist() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails/name")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deletingAllMailsShouldCreateATask() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    public void clearTaskShouldHaveDetails() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .delete(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ClearMailRepositoryTask.TYPE))
            .body("additionalInformation.repositoryUrl", is(URL_MY_REPO))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    public void clearTaskShouldRemoveAllTheMailsFromTheMailRepository() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .delete(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    public void patchShouldReturnNotFoundWhenNoMailRepository() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.empty());

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is(URL_MY_REPO + "does not exist"));
    }

    @Test
    public void deleteShouldReturnNotFoundWhenNoMailRepository() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.empty());

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails/any")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is(URL_MY_REPO + "does not exist"));
    }

    @Test
    public void reprocessingAllTaskShouldCreateATask() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        given()
            .param("action", "reprocess")
        .when()
            .patch(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    public void reprocessingAllTaskShouldRejectInvalidActions() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        given()
            .param("action", "invalid")
        .when()
            .patch(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("action query parameter is mandatory. The only supported value is `reprocess`"));
    }

    @Test
    public void reprocessingAllTaskShouldRequireAction() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        when()
            .patch(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("action query parameter is mandatory. The only supported value is `reprocess`"));
    }

    @Test
    public void reprocessingAllTaskShouldIncludeDetailsWhenDefaultValues() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingAllMailsTask.TYPE))
            .body("additionalInformation.repositoryUrl", is(URL_MY_REPO))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(0))
            .body("additionalInformation.targetProcessor", isEmptyOrNullString())
            .body("additionalInformation.targetQueue", is(MailQueueFactory.SPOOL))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    public void reprocessingAllTaskShouldIncludeDetails() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .param("processor", transport)
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingAllMailsTask.TYPE))
            .body("additionalInformation.repositoryUrl", is(URL_MY_REPO))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(0))
            .body("additionalInformation.targetProcessor", is(transport))
            .body("additionalInformation.targetQueue", is(CUSTOM_QUEUE))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    public void reprocessingAllTaskShouldClearMailRepository() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .param("processor", transport)
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.list()).isEmpty();
    }

    @Test
    public void reprocessingAllTaskShouldEnqueueMailsOnDefaultQueue() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1, NAME_2);
    }

    @Test
    public void reprocessingAllTaskShouldPreserveStateWhenProcessorIsNotSpecified() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(state1, state2);
    }

    @Test
    public void reprocessingAllTaskShouldOverWriteStateWhenProcessorSpecified() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("processor", transport)
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(transport, transport);
    }

    @Test
    public void reprocessingAllTaskShouldEnqueueMailsOnSpecifiedQueue() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .patch(URL_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(customQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1, NAME_2);
    }

    @Test
    public void reprocessingOneTaskShouldCreateATask() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        given()
            .param("action", "reprocess")
        .when()
            .patch(URL_ESCAPED_MY_REPO + "/mails/name1")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    public void reprocessingOneTaskShouldRejectInvalidActions() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        given()
            .param("action", "invalid")
        .when()
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("action query parameter is mandatory. The only supported value is `reprocess`"));
    }

    @Test
    public void reprocessingOneTaskShouldRequireAction() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        when()
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("action query parameter is mandatory. The only supported value is `reprocess`"));
    }

    @Test
    public void reprocessingOneTaskShouldIncludeDetailsWhenDefaultValues() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingOneMailTask.TYPE))
            .body("additionalInformation.repositoryUrl", is(URL_MY_REPO))
            .body("additionalInformation.mailKey", is(NAME_1))
            .body("additionalInformation.targetProcessor", isEmptyOrNullString())
            .body("additionalInformation.targetQueue", is(MailQueueFactory.SPOOL))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    public void reprocessingOneTaskShouldIncludeDetails() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .param("processor", transport)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingOneMailTask.TYPE))
            .body("additionalInformation.repositoryUrl", is(URL_MY_REPO))
            .body("additionalInformation.mailKey", is(NAME_1))
            .body("additionalInformation.targetProcessor", is(transport))
            .body("additionalInformation.targetQueue", is(CUSTOM_QUEUE))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    public void reprocessingOneTaskShouldRemoveMailFromRepository() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .param("processor", transport)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.list())
            .containsOnly(NAME_2);
    }

    @Test
    public void reprocessingOneTaskShouldEnqueueMailsOnDefaultQueue() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1);
    }

    @Test
    public void reprocessingOneTaskShouldPreserveStateWhenProcessorIsNotSpecified() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(state1);
    }

    @Test
    public void reprocessingOneTaskShouldOverWriteStateWhenProcessorSpecified() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("processor", transport)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(transport);
    }

    @Test
    public void reprocessingOneTaskShouldEnqueueMailsOnSpecifiedQueue() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(customQueue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1);
    }

    @Test
    public void reprocessingOneTaskShouldNotEnqueueUnknownMailKey() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + "unknown")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(customQueue.browse())
            .isEmpty();
    }

    @Test
    public void reprocessingOneTaskShouldNotRemoveMailFromRepositoryWhenUnknownMailKey() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + "unknown")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.size())
            .isEqualTo(2);
    }

    @Test
    public void reprocessingOneTaskShouldFailWhenUnknownMailKey() throws Exception {
        when(mailRepositoryStore.get(URL_MY_REPO)).thenReturn(Optional.of(mailRepository));
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE)
            .patch(URL_ESCAPED_MY_REPO + "/mails/" + "unknown")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("failed"));
    }

}
