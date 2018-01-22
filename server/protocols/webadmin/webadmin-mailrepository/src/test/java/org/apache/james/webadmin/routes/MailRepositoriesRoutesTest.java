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

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.ClearMailRepositoryTask;
import org.apache.james.webadmin.service.MailRepositoryStoreService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
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
    private WebAdminServer webAdminServer;
    private MailRepositoryStore mailRepositoryStore;
    private MemoryMailRepository mailRepository;

    @Before
    public void setUp() throws Exception {
        mailRepositoryStore = mock(MailRepositoryStore.class);
        mailRepository = new MemoryMailRepository();

        MemoryTaskManager taskManager = new MemoryTaskManager();
        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new NoopMetricFactory(),
                new MailRepositoriesRoutes(new MailRepositoryStoreService(mailRepositoryStore),
                    jsonTransformer, taskManager),
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
        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void listingKeysShouldReturnEmptyWhenNoMail() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    public void listingKeysShouldReturnContainedKeys() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

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
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name3")
            .build());

        when()
            .get(MY_REPO_MAILS + "?offset=1&limit=1")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("mailKey", contains("name2"));
    }

    @Test
    public void listingKeysShouldHandleErrorGracefully() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO))
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
        when()
            .get(MY_REPO_MAILS + "?offset=invalid")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Can not parse offset"));
    }

    @Test
    public void listingKeysShouldReturnErrorOnNegativeOffset() throws Exception {
        when()
            .get(MY_REPO_MAILS + "?offset=-1")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("offset can not be negative"));
    }

    @Test
    public void listingKeysShouldReturnEmptyWhenOffsetExceedsSize() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name3")
            .build());

        when()
            .get(MY_REPO_MAILS + "?offset=5")
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    public void listingKeysShouldReturnErrorOnInvalidLimit() throws Exception {
        when()
            .get(MY_REPO_MAILS + "?limit=invalid")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Can not parse limit"));
    }

    @Test
    public void listingKeysShouldReturnErrorOnNegativeLimit() throws Exception {
        when()
            .get(MY_REPO_MAILS + "?limit=-1")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("limit can not be negative"));
    }

    @Test
    public void listingKeysShouldIgnoreZeroedOffset() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());

        when()
            .get(MY_REPO_MAILS + "?offset=0")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(2))
            .body("mailKey", containsInAnyOrder("name1", "name2"));
    }

    @Test
    public void zeroLimitShouldNotBeValid() throws Exception {
        when()
            .get(MY_REPO_MAILS + "?limit=0")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("limit can not be equal to zero"));
    }

    @Test
    public void retrievingRepositoryShouldReturnNotFoundWhenNone() throws Exception {
        given()
            .get(URL_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void retrievingRepositoryShouldReturnBasicInformation() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

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
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        given()
            .get(URL_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("size", equalTo(0));
    }

    @Test
    public void retrievingRepositorySizeShouldReturnNumberOfContainedMails() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        mailRepository.store(FakeMail.builder()
            .name("name1")
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
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        String name = "name1";
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
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        String name = "name1";
        mailRepository.store(FakeMail.builder()
            .name(name)
            .build());

        when()
            .get(URL_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(name))
            .body("sender", isEmptyOrNullString())
            .body("state", isEmptyOrNullString())
            .body("error", isEmptyOrNullString())
            .body("recipients", hasSize(0));
    }

    @Test
    public void retrievingAMailShouldFailWhenUnknown() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

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
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        given()
            .delete(URL_ESCAPED_MY_REPO + "/mails/" + name1);

        when()
            .get(URL_ESCAPED_MY_REPO + "/mails")
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("mailKey", contains(name2));
    }

    @Test
    public void deletingAMailShouldReturnOkWhenExist() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        String name1 = "name1";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails/" + name1)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deletingAMailShouldReturnOkWhenNotExist() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        when()
            .delete(URL_ESCAPED_MY_REPO + "/mails/name")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deletingAllMailsShouldCreateATask() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        when()
            .patch(URL_ESCAPED_MY_REPO + "/mails?action=clear")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    public void patchShouldOnlySupportClear() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        when()
            .patch(URL_ESCAPED_MY_REPO + "/mails?action=invalid")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Unknown action invalid"));
    }

    @Test
    public void patchShouldRequireAnAction() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        when()
            .patch(URL_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("You need to specify an action. Currently only clear is supported."));
    }

    @Test
    public void clearTaskShouldHaveDetails() throws Exception {
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .build());

        String taskId = with()
            .patch(URL_ESCAPED_MY_REPO + "/mails?action=clear")
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
        when(mailRepositoryStore.select(URL_MY_REPO)).thenReturn(mailRepository);

        mailRepository.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository.store(FakeMail.builder()
            .name("name2")
            .build());

        String taskId = with()
            .patch(URL_ESCAPED_MY_REPO + "/mails?action=clear")
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

}
