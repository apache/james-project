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
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.core.builder.MimeMessageBuilder.BodyPartBuilder;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.mailrepository.memory.SimpleMailRepositoryLoader;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.ClearMailRepositoryTask;
import org.apache.james.webadmin.service.MailRepositoryStoreService;
import org.apache.james.webadmin.service.ReprocessingAllMailsTask;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTask;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.service.WebAdminClearMailRepositoryTaskAdditionalInformationDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailet.Attribute;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.apache.mailet.base.test.FakeMail;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;

class MailRepositoriesRoutesTest {

    private static final MailRepositoryUrl URL_MY_REPO = MailRepositoryUrl.from("memory://myRepo");
    private static final MailRepositoryUrl URL_MY_REPO_OTHER = MailRepositoryUrl.from("other://myRepo");
    private static final MailRepositoryPath PATH_MY_REPO = MailRepositoryPath.from("myRepo");
    private static final String PATH_ESCAPED_MY_REPO = "myRepo";
    private static final String MY_REPO_MAILS = "myRepo/mails";
    private static final MailQueueName CUSTOM_QUEUE = MailQueueName.of("customQueue");
    private static final String NAME_1 = "name1";
    private static final String NAME_2 = "name2";
    private static final byte[] MESSAGE_BYTES = "header: value \r\n".getBytes(UTF_8);
    private WebAdminServer webAdminServer;
    private MemoryMailRepositoryStore mailRepositoryStore;
    private ManageableMailQueue spoolQueue;
    private ManageableMailQueue customQueue;

    @BeforeEach
    void setUp() throws Exception {
        createMailRepositoryStore();

        MemoryTaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();
        MailQueueFactory<? extends ManageableMailQueue> queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        spoolQueue = queueFactory.createQueue(MailQueueFactory.SPOOL);
        customQueue = queueFactory.createQueue(CUSTOM_QUEUE);

        MailRepositoryStoreService repositoryStoreService = new MailRepositoryStoreService(mailRepositoryStore);

        ReprocessingService reprocessingService = new ReprocessingService(queueFactory, repositoryStoreService);

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new MailRepositoriesRoutes(repositoryStoreService,
                    jsonTransformer, reprocessingService, taskManager),
            new TasksRoutes(taskManager, jsonTransformer,
                DTOConverter.of(ReprocessingOneMailTaskAdditionalInformationDTO.module(),
                    ReprocessingAllMailsTaskAdditionalInformationDTO.module(),
                    WebAdminClearMailRepositoryTaskAdditionalInformationDTO.module())))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void putMailRepositoryShouldReturnOkWhenRepositoryIsCreated() {
        given()
            .params("protocol", "memory")
        .when()
            .put(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(mailRepositoryStore.get(URL_MY_REPO))
            .isNotEmpty()
            .containsInstanceOf(MemoryMailRepository.class);
    }

    @Test
    void putMailRepositoryShouldReturnOkWhenRepositoryAlreadyExists() {
        given()
            .params("protocol", "memory")
        .when()
            .put(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        given()
            .params("protocol", "memory")
        .when()
            .put(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(mailRepositoryStore.get(URL_MY_REPO))
            .isNotEmpty()
            .containsInstanceOf(MemoryMailRepository.class);

        assertThat(mailRepositoryStore.getByPath(PATH_MY_REPO))
            .hasSize(1);
    }

    @Test
    void putMailRepositoryShouldReturnInvalidArgumentWhenProtocolIsUnsupported() {
        given()
            .params("protocol", "unsupported")
        .when()
            .put(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("'unsupported' is an unsupported protocol"));

        assertThat(mailRepositoryStore.get(URL_MY_REPO))
            .isEmpty();
    }

    @Test
    void getMailRepositoriesShouldReturnEmptyWhenEmpty() {
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
    void getMailRepositoriesShouldReturnRepositoryWhenOne() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].repository", is(PATH_MY_REPO.asString()))
            .body("[0].path", is(PATH_ESCAPED_MY_REPO));
    }

    @Test
    void getMailRepositoriesShouldDeduplicateAccordingToPath() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);
        mailRepositoryStore.create(URL_MY_REPO_OTHER);

        when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].repository", is(PATH_MY_REPO.asString()))
            .body("[0].path", is(PATH_ESCAPED_MY_REPO));
    }

    @Test
    void getMailRepositoriesShouldReturnTwoRepositoriesWhenTwo() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);
        mailRepositoryStore.create(MailRepositoryUrl.from("memory://mySecondRepo"));

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

        assertThat(mailRepositories)
            .containsOnly(PATH_ESCAPED_MY_REPO, "mySecondRepo");
    }

    @Test
    void listingKeysShouldReturnNotFoundWhenNoRepository() {
        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("message", is("myRepo does not exist"));
    }

    @Test
    void listingKeysShouldReturnEmptyWhenNoMail() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    void listingKeysShouldReturnContainedKeys() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

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
            .body("", containsInAnyOrder("name1", "name2"));
    }

    @Test
    void listingKeysShouldMergeRepositoryContentWhenSamePath() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);

        mailRepository1.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository2.store(FakeMail.builder()
            .name("name2")
            .build());

        when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(2))
            .body("", containsInAnyOrder("name1", "name2"));
    }

    @Test
    void listingKeysShouldApplyLimitAndOffset() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

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
            .body("", contains("name2"));
    }

    @Test
    void listingKeysShouldApplyLimitWhenSeveralRepositories() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);

        mailRepository1.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository1.store(FakeMail.builder()
            .name("name2")
            .build());
        mailRepository2.store(FakeMail.builder()
            .name("name3")
            .build());

        given()
            .param("limit", "1")
        .when()
            .get(MY_REPO_MAILS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1));
    }

    @Test
    void listingKeysShouldReturnErrorOnInvalidOffset() {
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
    void listingKeysShouldReturnErrorOnNegativeOffset() {
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
    void listingKeysShouldReturnEmptyWhenOffsetExceedsSize() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

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
    void offsetShouldBeAplliedOnTheMergedViewOfMailRepositories() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);

        mailRepository1.store(FakeMail.builder()
            .name("name1")
            .build());
        mailRepository2.store(FakeMail.builder()
            .name("name2")
            .build());
        mailRepository1.store(FakeMail.builder()
            .name("name3")
            .build());
        mailRepository2.store(FakeMail.builder()
            .name("name4")
            .build());

        given()
            .param("offset", "2")
        .when()
            .get(MY_REPO_MAILS)
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(2));
    }

    @Test
    void listingKeysShouldReturnErrorOnInvalidLimit() {
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
    void listingKeysShouldReturnErrorOnNegativeLimit() {
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
    void listingKeysShouldIgnoreZeroedOffset() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

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
            .body("", containsInAnyOrder(NAME_1, NAME_2));
    }

    @Test
    void zeroLimitShouldNotBeValid() {
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
    void retrievingRepositoryShouldReturnNotFoundWhenNone() {
        given()
            .get(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void retrievingRepositoryShouldReturnBasicInformation() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        given()
            .get(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("repository", is(PATH_MY_REPO.asString()))
            .body("path", is(PATH_ESCAPED_MY_REPO));
    }

    @Test
    void retrievingRepositorySizeShouldReturnZeroWhenEmpty() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        given()
            .get(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("size", equalTo(0));
    }

    @Test
    void retrievingRepositorySizeShouldReturnNumberOfContainedMails() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        given()
            .get(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("size", equalTo(1));
    }

    @Test
    void retrievingRepositorySizeShouldReturnNumberOfContainedMailsWhenSeveralRepositoryWithSamePath() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);

        mailRepository1.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        mailRepository2.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        given()
            .get(PATH_ESCAPED_MY_REPO)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("size", equalTo(2));
    }

    @Test
    void retrievingAMailShouldDisplayItsInformation() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        String name = NAME_1;
        String sender = "sender@domain";
        String recipient1 = "recipient1@domain";
        String recipient2 = "recipient2@domain";
        String state = "state";
        String errorMessage = "Error: why this mail is stored";
        String remoteHost = "smtp.domain";
        String remoteAddr = "66.66.66.66";
        Date lastUpdated = new Date(07060504030201L);
        mailRepository.store(FakeMail.builder()
            .name(name)
            .sender(sender)
            .recipients(recipient1, recipient2)
            .state(state)
            .errorMessage(errorMessage)
            .remoteHost(remoteHost)
            .remoteAddr(remoteAddr)
            .lastUpdated(lastUpdated)
            .build());

        when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(name))
            .body("sender", is(sender))
            .body("recipients", containsInAnyOrder(recipient1, recipient2))
            .body("state", is(state))
            .body("error", is(errorMessage))
            .body("remoteHost", is(remoteHost))
            .body("remoteAddr", is(remoteAddr))
            .body("lastUpdated", is(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    .format(ZonedDateTime.ofInstant(lastUpdated.toInstant(), ZoneId.of("UTC")))));
    }

    @Test
    void retrievingAMailShouldDisplayAllAdditionalFieldsWhenRequested() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name = NAME_1;

        BodyPartBuilder textMessage = MimeMessageBuilder.bodyPartBuilder()
                .addHeader("Content-type", "text/plain")
                .data("My awesome body!!");
        BodyPartBuilder htmlMessage = MimeMessageBuilder.bodyPartBuilder()
                .addHeader("Content-type", "text/html")
                .data("My awesome <em>body</em>!!");
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("headerName3", "value5")
                .addHeader("headerName3", "value8")
                .addHeader("headerName4", "value6")
                .addHeader("headerName4", "value7")
                .setContent(MimeMessageBuilder.multipartBuilder()
                    .subType("alternative")
                    .addBody(textMessage)
                    .addBody(htmlMessage))
                .build();

        MailAddress recipientHeaderAddress = new MailAddress("third@party");
        FakeMail mail = FakeMail.builder()
            .name(name)
            .attribute(Attribute.convertToAttribute("name1", "value1"))
            .attribute(Attribute.convertToAttribute("name2", "value2"))
            .mimeMessage(mimeMessage)
            .size(42424242)
            .addHeaderForRecipient(Header.builder()
                    .name("headerName1")
                    .value("value1")
                    .build(), recipientHeaderAddress)
            .addHeaderForRecipient(Header.builder()
                    .name("headerName1")
                    .value("value2")
                    .build(), recipientHeaderAddress)
            .addHeaderForRecipient(Header.builder()
                    .name("headerName2")
                    .value("value3")
                    .build(), recipientHeaderAddress)
            .addHeaderForRecipient(Header.builder()
                    .name("headerName2")
                    .value("value4")
                    .build(), recipientHeaderAddress)
            .build();

        mailRepository.store(mail);

        String jsonAsString =
            given()
                .params("additionalFields", "attributes,headers,textBody,htmlBody,messageSize,perRecipientsHeaders")
            .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .then()
            .extract()
                .body()
                .asString();

        assertThatJson(jsonAsString)
            .when(IGNORING_ARRAY_ORDER)
            .when(IGNORING_EXTRA_FIELDS)
                .isEqualTo("{" +
                        "  \"name\": \"name1\"," +
                        "  \"sender\": null," +
                        "  \"recipients\": []," +
                        "  \"error\": null," +
                        "  \"state\": null," +
                        "  \"remoteHost\": \"111.222.333.444\"," +
                        "  \"remoteAddr\": \"127.0.0.1\"," +
                        "  \"lastUpdated\": null," +
                        "  \"attributes\": {" +
                        "    \"name2\": \"value2\"," +
                        "    \"name1\": \"value1\"" +
                        "  }," +
                        "  \"perRecipientsHeaders\": {" +
                        "    \"third@party\": {" +
                        "      \"headerName1\": [" +
                        "        \"value1\"," +
                        "        \"value2\"" +
                        "      ]," +
                        "      \"headerName2\": [" +
                        "        \"value3\"," +
                        "        \"value4\"" +
                        "      ]" +
                        "    }" +
                        "  }," +
                        "  \"headers\": {" +
                        "    \"headerName4\": [" +
                        "      \"value6\"," +
                        "      \"value7\"" +
                        "    ]," +
                        "    \"headerName3\": [" +
                        "      \"value5\"," +
                        "      \"value8\"" +
                        "    ]" +
                        "  }," +
                        "  \"textBody\": \"My awesome body!!\"," +
                        "  \"htmlBody\": \"My awesome <em>body</em>!!\"," +
                        "  \"messageSize\": 42424242" +
                        "}");
    }

    @Test
    void retrievingAMailShouldDisplayAllValidAdditionalFieldsWhenRequested() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        String name = NAME_1;
        String sender = "sender@domain";
        String recipient1 = "recipient1@domain";
        int messageSize = 42424242;
        mailRepository.store(FakeMail.builder()
            .name(name)
            .sender(sender)
            .recipients(recipient1)
            .size(messageSize)
            .build());

        given()
            .params("additionalFields", ",,,messageSize")
        .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(name))
            .body("sender", is(sender))
            .body("headers", nullValue())
            .body("textBody", nullValue())
            .body("htmlBody", nullValue())
            .body("messageSize", is(messageSize))
            .body("attributes", nullValue())
            .body("perRecipientsHeaders", nullValue());
    }

    @Test
    void retrievingAMailShouldDisplayCorrectlyEncodedHeadersInValidAdditionalFieldsWhenRequested() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name = NAME_1;
        String sender = "sender@domain";
        String recipient1 = "recipient1@domain";
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("friend", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>")
                .build();

        mailRepository.store(FakeMail.builder()
            .name(name)
            .sender(sender)
            .recipients(recipient1)
            .mimeMessage(mimeMessage)
            .build());

        given()
            .params("additionalFields", "headers")
        .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(name))
            .body("sender", is(sender))
            .body("headers.friend", is(Arrays.asList("Frédéric MARTIN <fred.martin@linagora.com>")));
    }

    @Test
    void retrievingAMailShouldDisplayAllValidAdditionalFieldsEvenTheDuplicatedOnesWhenRequested() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name = NAME_1;
        String sender = "sender@domain";
        String recipient1 = "recipient1@domain";
        int messageSize = 42424242;
        mailRepository.store(FakeMail.builder()
            .name(name)
            .sender(sender)
            .recipients(recipient1)
            .size(messageSize)
            .build());

        given()
            .params("additionalFields", "messageSize,messageSize")
        .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(name))
            .body("sender", is(sender))
            .body("headers", nullValue())
            .body("textBody", nullValue())
            .body("htmlBody", nullValue())
            .body("messageSize", is(messageSize))
            .body("attributes", nullValue())
            .body("perRecipientsHeaders", nullValue());
    }

    @Test
    void retrievingAMailShouldFailWhenAnUnknownFieldIsRequested() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name = NAME_1;
        String sender = "sender@domain";
        String recipient1 = "recipient1@domain";
        int messageSize = 42424242;
        mailRepository.store(FakeMail.builder()
            .name(name)
            .sender(sender)
            .recipients(recipient1)
            .size(messageSize)
            .build());

        given()
            .params("additionalFields", "nonExistingField")
        .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("The field 'nonExistingField' can't be requested in additionalFields parameter"));
    }

    @Test
    void retrievingAMailShouldNotFailWhenOnlyNameProperty() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", is(NAME_1))
            .body("sender", is(emptyOrNullString()))
            .body("state", is(emptyOrNullString()))
            .body("error", is(emptyOrNullString()))
            .body("recipients", hasSize(0));
    }

    @Test
    void retrievingAMailShouldFailWhenUnknown() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        String name = "name";
        when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("Could not retrieve " + name));
    }

    @Test
    void downloadingAMailShouldReturnTheEml() throws Exception {
        RestAssured.requestSpecification = new RequestSpecBuilder().setPort(webAdminServer.getPort().getValue())
                .setBasePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
                .build();
        RestAssured.registerParser(Constants.RFC822_CONTENT_TYPE, Parser.TEXT);

        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        String name = NAME_1;
        FakeMail mail = FakeMail.builder()
            .name(name)
            .fileName("mail.eml")
            .build();
        mailRepository.store(mail);

        String expectedContent = ClassLoaderUtils.getSystemResourceAsString("mail.eml", StandardCharsets.UTF_8);

        String actualContent = given()
            .accept(Constants.RFC822_CONTENT_TYPE)
        .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.OK_200)
            .header("Content-Length", "471")
            .contentType(Constants.RFC822_CONTENT_TYPE)
            .extract()
            .body()
            .asString();

        assertThat(actualContent).isEqualToNormalizingNewlines(expectedContent);
    }

    @Test
    void downloadingAMailShouldFailWhenUnknown() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        RestAssured.requestSpecification = new RequestSpecBuilder().setPort(webAdminServer.getPort().getValue())
            .setBasePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .build();
        RestAssured.registerParser(Constants.RFC822_CONTENT_TYPE, Parser.TEXT);

        String name = "name";
        given()
            .accept(Constants.RFC822_CONTENT_TYPE)
        .when()
            .get(PATH_ESCAPED_MY_REPO + "/mails/" + name)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("Could not retrieve " + name));
    }

    @Test
    void deletingAMailShouldRemoveIt() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        given()
            .delete(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1);

        when()
            .get(PATH_ESCAPED_MY_REPO + "/mails")
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("", contains(NAME_2));
    }

    @Test
    void deletingAMailShouldReturnOkWhenExist() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        when()
            .delete(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void deletingAMailShouldReturnOkWhenNotExist() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        when()
            .delete(PATH_ESCAPED_MY_REPO + "/mails/name")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void deletingAllMailsShouldCreateATask() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        when()
            .delete(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    void clearTaskShouldHaveDetails() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .delete(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ClearMailRepositoryTask.TYPE.asString()))
            .body("additionalInformation.repositoryPath", is(PATH_MY_REPO.asString()))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void clearTaskShouldRemoveAllTheMailsFromTheMailRepository() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);

        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .delete(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    void patchShouldReturnNotFoundWhenNoMailRepository() {
        when()
            .delete(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is(PATH_MY_REPO.asString() + " does not exist"));
    }

    @Test
    void deleteShouldReturnNotFoundWhenNoMailRepository() {
        when()
            .delete(PATH_ESCAPED_MY_REPO + "/mails/any")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is(PATH_MY_REPO.asString() + " does not exist"));
    }

    @Test
    void reprocessingAllTaskShouldCreateATask() throws Exception {
        mailRepositoryStore.create(URL_MY_REPO);

        given()
            .param("action", "reprocess")
        .when()
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    void reprocessingAllTaskShouldRejectInvalidActions() {
        given()
            .param("action", "invalid")
        .when()
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [reprocess]"));
    }

    @Test
    void reprocessingAllTaskShouldRequireAction() {
        when()
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter is compulsory. Supported values are [reprocess]"));
    }

    @Test
    void reprocessingAllTaskShouldIncludeDetailsWhenDefaultValues() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingAllMailsTask.TYPE.asString()))
            .body("additionalInformation.repositoryPath", is(PATH_MY_REPO.asString()))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(0))
            .body("additionalInformation.targetProcessor", is(emptyOrNullString()))
            .body("additionalInformation.targetQueue", is(MailQueueFactory.SPOOL.asString()))
            .body("additionalInformation.consume", is(true))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void reprocessingAllTaskShouldIncludeDetails() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .param("consume", false)
            .param("limit", 100)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingAllMailsTask.TYPE.asString()))
            .body("additionalInformation.repositoryPath", is(PATH_MY_REPO.asString()))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(0))
            .body("additionalInformation.targetProcessor", is(transport))
            .body("additionalInformation.targetQueue", is(CUSTOM_QUEUE.asString()))
            .body("additionalInformation.consume", is(false))
            .body("additionalInformation.limit", is(100))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void reprocessingAllTaskShouldNotFailWhenSeveralRepositoriesWithSamePath() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepositoryStore.create(URL_MY_REPO_OTHER);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"));
    }

    @Test
    void reprocessingAllTaskShouldClearMailRepository() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.list())
            .toIterable()
            .isEmpty();
    }

    @Test
    void reprocessingAllTaskShouldClearMailInLimitedWhenProvideLimitParameter() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("limit", 1)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
        .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingAllMailsTask.TYPE.asString()))
            .body("additionalInformation.initialCount", is(2))
            .body("additionalInformation.remainingCount", is(1))
            .body("additionalInformation.limit", is(1));

        assertThat(mailRepository.list())
            .toIterable()
            .hasSize(1);
    }

    @Test
    void reprocessingAllTaskShouldFailWhenInvalidLimitParameter() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        with()
            .param("action", "reprocess")
            .param("limit", "invalid")
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Can not parse limit"));
    }

    @Test
    void reprocessingAllTaskShouldNotClearMailRepositoryWhenNotConsume() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .param("consume", false)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.list())
            .toIterable()
            .hasSize(2);
    }

    @Test
    void reprocessingAllTaskShouldClearBothMailRepositoriesWhenSamePath() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository1.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository2.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository1.list()).toIterable()
            .isEmpty();
        assertThat(mailRepository2.list()).toIterable()
            .isEmpty();
    }

    @Test
    void reprocessingAllTaskShouldEnqueueMailsOnDefaultQueue() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1, NAME_2);
    }

    @Test
    void reprocessingAllTaskShouldEnqueueMailsOfBothRepositoriesOnDefaultQueueWhenSamePath() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);
        mailRepository1.store(FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository2.store(FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1, NAME_2);
    }

    @Test
    void reprocessingAllTaskShouldResetLoopDetection() throws Exception {
        MailRepository mailRepository1 = mailRepositoryStore.create(URL_MY_REPO);
        MailRepository mailRepository2 = mailRepositoryStore.create(URL_MY_REPO_OTHER);
        FakeMail mail = FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build();
        LoopPrevention.RecordedRecipients.fromMail(mail).merge(new MailAddress("bob@domain.tld")).recordOn(mail);
        mailRepository1.store(mail);

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(LoopPrevention.RecordedRecipients::fromMail)
            .extracting(LoopPrevention.RecordedRecipients::getRecipients)
            .allSatisfy(recordedRecipients -> assertThat(recordedRecipients).isEmpty());
    }

    @Test
    void reprocessingAllTaskShouldPreserveStateWhenProcessorIsNotSpecified() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(state1, state2);
    }

    @Test
    void reprocessingAllTaskShouldOverWriteStateWhenProcessorSpecified() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(transport, transport);
    }

    @Test
    void reprocessingAllTaskShouldEnqueueMailsOnSpecifiedQueue() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .patch(PATH_ESCAPED_MY_REPO + "/mails")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(customQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1, NAME_2);
    }

    @Test
    void reprocessingOneTaskShouldCreateATask() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        given()
            .param("action", "reprocess")
        .when()
            .patch(PATH_ESCAPED_MY_REPO + "/mails/name1")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    void reprocessingOneTaskShouldRejectInvalidActions() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        given()
            .param("action", "invalid")
        .when()
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [reprocess]"));
    }

    @Test
    void reprocessingOneTaskShouldRequireAction() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());

        when()
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter is compulsory. Supported values are [reprocess]"));
    }

    @Test
    void reprocessingOneTaskShouldIncludeDetailsWhenDefaultValues() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingOneMailTask.TYPE.asString()))
            .body("additionalInformation.repositoryPath", is(PATH_MY_REPO.asString()))
            .body("additionalInformation.mailKey", is(NAME_1))
            .body("additionalInformation.targetProcessor", is(emptyOrNullString()))
            .body("additionalInformation.targetQueue", is(MailQueueFactory.SPOOL.asString()))
            .body("additionalInformation.consume", is(true))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void reprocessingOneTaskShouldIncludeDetails() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .param("consume", false)
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingOneMailTask.TYPE.asString()))
            .body("additionalInformation.repositoryPath", is(PATH_MY_REPO.asString()))
            .body("additionalInformation.mailKey", is(NAME_1))
            .body("additionalInformation.targetProcessor", is(transport))
            .body("additionalInformation.targetQueue", is(CUSTOM_QUEUE.asString()))
            .body("additionalInformation.consume", is(false))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void reprocessingOneTaskShouldNotFailWhenSeveralRepositoryWithSamePath() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepositoryStore.create(URL_MY_REPO_OTHER);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));
    }

    @Test
    void reprocessingOneTaskShouldRemoveMailFromRepository() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String name1 = "name1";
        String name2 = "name2";
        mailRepository.store(FakeMail.builder()
            .name(name1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(name2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.list())
            .toIterable()
            .containsOnly(new MailKey(NAME_2));
    }

    @Test
    void reprocessingOneTaskShouldEnqueueMailsOnDefaultQueue() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1);
    }

    @Test
    void reprocessingOneTaskShouldPreserveStateWhenProcessorIsNotSpecified() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(state1);
    }

    @Test
    void reprocessingOneTaskShouldOverWriteStateWhenProcessorSpecified() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        String state1 = "state1";
        String state2 = "state2";
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .state(state1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .state(state2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String transport = "transport";
        String taskId = with()
            .param("action", "reprocess")
            .param("processor", transport)
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(spoolQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getState)
            .containsOnly(transport);
    }

    @Test
    void reprocessingOneTaskShouldEnqueueMailsOnSpecifiedQueue() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(customQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsOnly(NAME_1);
    }

    @Test
    void reprocessingOneTaskShouldNotEnqueueUnknownMailKey() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + "unknown")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(customQueue.browse())
            .toIterable()
            .isEmpty();
    }

    @Test
    void reprocessingOneTaskShouldNotRemoveEmailWhenNotConsume() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("consume", false)
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.size())
            .isEqualTo(2);
    }

    @Test
    void reprocessingOneTaskShouldRemoveEmailWhenConsume() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .param("consume", true)
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + NAME_1)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.size())
            .isEqualTo(1);
    }

    @Test
    void reprocessingOneTaskShouldNotRemoveMailFromRepositoryWhenUnknownMailKey() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + "unknown")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(mailRepository.size())
            .isEqualTo(2);
    }

    @Test
    void reprocessingOneTaskShouldFailWhenUnknownMailKey() throws Exception {
        MailRepository mailRepository = mailRepositoryStore.create(URL_MY_REPO);
        mailRepository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        mailRepository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        String taskId = with()
            .param("action", "reprocess")
            .param("queue", CUSTOM_QUEUE.asString())
            .patch(PATH_ESCAPED_MY_REPO + "/mails/" + "unknown")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("failed"));
    }

    private void createMailRepositoryStore() throws Exception {
        MemoryMailRepositoryUrlStore urlStore = new MemoryMailRepositoryUrlStore();
        MailRepositoryStoreConfiguration configuration = MailRepositoryStoreConfiguration.forItems(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("memory")),
                MemoryMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()),
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("other")),
                MemoryMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()));
        mailRepositoryStore = new MemoryMailRepositoryStore(urlStore, new SimpleMailRepositoryLoader(), configuration);

        mailRepositoryStore.init();
    }
}
