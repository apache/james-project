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

package org.apache.james.mailets;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import net.javacrumbs.jsonunit.core.Option;

public class RemoteDeliveryErrorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDeliveryErrorTest.class);
    private static final String ANOTHER_DOMAIN = "other.com";

    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String MIME_MESSAGE = "FROM: " + FROM + "\r\n" +
        "subject: test\r\n" +
        "\r\n" +
        "content\r\n" +
        ".\r\n";
    private static final String RECIPIENT = "touser@" + ANOTHER_DOMAIN;
    private static final String RECIPIENT1 = "touser1@" + ANOTHER_DOMAIN;
    private static final String RECIPIENT2 = "touser2@" + ANOTHER_DOMAIN;

    private static final MockSmtpBehaviors ALWAYS_421_RCPT_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.RCPT_TO)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forAnyInput()
        .unlimitedNumberOfAnswer()
        .build();
    private static final MockSmtpBehaviors ALWAYS_421_FROM_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.MAIL_FROM)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forAnyInput()
        .unlimitedNumberOfAnswer()
        .build();
    private static final MockSmtpBehaviors ALWAYS_421_DATA_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.DATA)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forAnyInput()
        .unlimitedNumberOfAnswer()
        .build();
    private static final MockSmtpBehaviors TWICE_421_RCPT_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.RCPT_TO)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forAnyInput()
        .onlySomeAnswers(2)
        .build();
    private static final MockSmtpBehaviors TWICE_421_FROM_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.MAIL_FROM)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forAnyInput()
        .onlySomeAnswers(2)
        .build();
    private static final MockSmtpBehaviors TWICE_421_DATA_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.DATA)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forAnyInput()
        .onlySomeAnswers(2)
        .build();
    private static final MockSmtpBehaviors SINGLE_500_RCPT_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.RCPT_TO)
        .respond(Response.SMTPStatusCode.DOES_NOT_ACCEPT_MAIL_521, "mock response")
        .forAnyInput()
        .onlySomeAnswers(1)
        .build();
    private static final MockSmtpBehaviors SINGLE_500_FROM_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.MAIL_FROM)
        .respond(Response.SMTPStatusCode.DOES_NOT_ACCEPT_MAIL_521, "mock response")
        .forAnyInput()
        .onlySomeAnswers(1)
        .build();
    private static final MockSmtpBehaviors SINGLE_500_DATA_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.DATA)
        .respond(Response.SMTPStatusCode.DOES_NOT_ACCEPT_MAIL_521, "mock response")
        .forAnyInput()
        .onlySomeAnswers(1)
        .build();
    private static final MockSmtpBehaviors SINGLE_PARTIAL_RCPT_421_BEHAVIOR = MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.RCPT_TO)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forInputContaining(RECIPIENT1)
        .onlySomeAnswers(1)
        .build();
    private static final MockSmtpBehaviors ALWAYS_PARTIAL_RCPT_421_BEHAVIOR =  MockSmtpBehaviors.builder()
        .addNewBehavior()
        .onCommand(SMTPCommand.RCPT_TO)
        .respond(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, "mock response")
        .forInputContaining(RECIPIENT2)
        .unlimitedNumberOfAnswer()
        .build();
    private static final String BOUNCE_MESSAGE = "Hi. This is the James mail server at localhost.\n" +
        "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
        "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
        "I include the list of recipients and the reason why I was unable to deliver\n" +
        "your message.";
    private static final ResponseSpecification RESPONSE_SPECIFICATION = new ResponseSpecBuilder().build();
    private InMemoryDNSService inMemoryDNSService;
    private RequestSpecification requestSpecificationForMockSMTP1;
    private RequestSpecification requestSpecificationForMockSMTP2;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @ClassRule
    public static DockerContainer mockSmtp = DockerContainer.fromName("linagora/mock-smtp-server")
        .withLogConsumer(outputFrame -> LOGGER.debug("MockSMTP 1: " + outputFrame.getUtf8String()));
    @ClassRule
    public static DockerContainer mockSmtp2 = DockerContainer.fromName("linagora/mock-smtp-server")
        .withLogConsumer(outputFrame -> LOGGER.debug("MockSMTP 2: " + outputFrame.getUtf8String()));

    private TemporaryJamesServer jamesServer;

    @Before
    public void setUp() throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
            .registerMxRecord(ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.simpleRoot())
                .putProcessor(CommonProcessors.error())
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD);

        requestSpecificationForMockSMTP1 = requestSpecification(mockSmtp);
        requestSpecificationForMockSMTP2 = requestSpecification(mockSmtp2);
        RestAssured.requestSpecification = requestSpecificationForMockSMTP1;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
        with().delete("/smtpMails");
    }

    @Test
    public void remoteDeliveryShouldBounceWhenAlwaysRCPT421() throws Exception {
        with()
            .body(ALWAYS_421_RCPT_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldBounceWhenAlwaysFROM421() throws Exception {
        with()
            .body(ALWAYS_421_FROM_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldBounceWhenAlwaysDATA421() throws Exception {
        with()
            .body(ALWAYS_421_DATA_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldNotRetryWhenRCPT500() throws Exception {
        with()
            .body(SINGLE_500_RCPT_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldNotRetryWhenFROM500() throws Exception {
        with()
            .body(SINGLE_500_FROM_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldNotRetryWhenDATA500() throws Exception {
        with()
            .body(SINGLE_500_DATA_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldRetryWhenRCPT421() throws Exception {
        with()
            .body(TWICE_421_RCPT_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() -> given()
            .get("/smtpMails")
        .then()
            .body("", hasSize(1))
            .body("[0].from", is(FROM))
            .body("[0].recipients", hasSize(1))
            .body("[0].recipients[0]", is(RECIPIENT))
            .body("[0].message", containsString("subject: test")));
    }

    @Test
    public void remoteDeliveryShouldRetryWhenFROM421() throws Exception {
        with()
            .body(TWICE_421_FROM_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() -> given()
            .get("/smtpMails")
        .then()
            .body("", hasSize(1))
            .body("[0].from", is(FROM))
            .body("[0].recipients", hasSize(1))
            .body("[0].recipients[0]", is(RECIPIENT))
            .body("[0].message", containsString("subject: test")));
    }

    @Test
    public void remoteDeliveryShouldRetryWhenDATA421() throws Exception {
        with()
            .body(TWICE_421_DATA_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() -> given()
            .get("/smtpMails")
        .then()
            .body("", hasSize(1))
            .body("[0].from", is(FROM))
            .body("[0].recipients", hasSize(1))
            .body("[0].recipients[0]", is(RECIPIENT))
            .body("[0].message", containsString("subject: test")));
    }

    @Test
    public void remoteDeliveryShouldNotDuplicateContentWhenSendPartial() throws Exception {
        with()
            .body(SINGLE_PARTIAL_RCPT_421_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(MailImpl.builder()
                .name("name")
                .sender(new MailAddress(FROM))
                .addRecipient(RECIPIENT1)
                .addRecipient(RECIPIENT2)
                .mimeMessage(MimeMessageUtil.mimeMessageFromString(MIME_MESSAGE))
                .build());

        awaitAtMostOneMinute.until(() -> given()
            .get("/smtpMails")
        .then()
            .extract()
            .body()
            .as(List.class)
            .size() == 2);

        String mailsAsJson = given()
            .get("/smtpMails")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(mailsAsJson)
            .when(Option.IGNORING_ARRAY_ORDER)
            .whenIgnoringPaths("[*].message")
            .isEqualTo("[" +
                "  {" +
                "    \"from\": \"" + FROM + "\", " +
                "    \"recipients\":[\"" + RECIPIENT1 + "\"]" +
                "  }," +
                "  {" +
                "    \"from\": \"" + FROM + "\", " +
                "    \"recipients\":[\"" + RECIPIENT2 + "\"]" +
                "  }" +
                "]");
    }

    @Ignore("JAMES-2097 Using full recipients for following MX iteration when partial fails on delivering")
    @Test
    public void remoteDeliveryShouldNotDuplicateContentWhenSendPartialWhenFailover() throws Exception {
        ImmutableList<InetAddress> addresses = ImmutableList.of(InetAddress.getByName(mockSmtp.getContainerIp()));
        ImmutableList<String> mxs = ImmutableList.of(mockSmtp.getContainerIp(), mockSmtp2.getContainerIp());
        ImmutableList<String> txtRecords = ImmutableList.of();

        inMemoryDNSService.registerRecord(ANOTHER_DOMAIN, addresses, mxs, txtRecords)
            .registerMxRecord(mockSmtp.getContainerIp(), mockSmtp.getContainerIp())
            .registerMxRecord(mockSmtp2.getContainerIp(), mockSmtp2.getContainerIp());

        given(requestSpecificationForMockSMTP1)
            .body(ALWAYS_PARTIAL_RCPT_421_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(MailImpl.builder()
                .name("name")
                .sender(new MailAddress(FROM))
                .addRecipient(RECIPIENT1)
                .addRecipient(RECIPIENT2)
                .mimeMessage(MimeMessageUtil.mimeMessageFromString(MIME_MESSAGE))
                .build());

        awaitAtMostOneMinute.until(() -> given(requestSpecificationForMockSMTP1, RESPONSE_SPECIFICATION)
            .get("/smtpMails")
        .then()
            .extract()
            .body()
            .as(List.class)
            .size() == 1);
        awaitAtMostOneMinute.until(() -> given(requestSpecificationForMockSMTP2, RESPONSE_SPECIFICATION)
            .get("/smtpMails")
        .then()
            .extract()
            .body()
            .as(List.class)
            .size() == 1);

        given(requestSpecificationForMockSMTP1, RESPONSE_SPECIFICATION)
            .get("/smtpMails")
        .then()
            .body("", hasSize(1))
            .body("[0].from", is(FROM))
            .body("[0].recipients", hasSize(1))
            .body("[0].recipients[0]", is(RECIPIENT2))
            .body("[0].message", containsString("subject: test"));
        
        given(requestSpecificationForMockSMTP2, RESPONSE_SPECIFICATION)
            .get("/smtpMails")
        .then()
            .body("", hasSize(1))
            .body("[0].from", is(FROM))
            .body("[0].recipients", hasSize(1))
            .body("[0].recipients[0]", is(RECIPIENT1))
            .body("[0].message", containsString("subject: test"));
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.LOCAL_DELIVERY)
            .addMailet(MailetConfiguration.builder()
                .mailet(RemoteDelivery.class)
                .matcher(All.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "10, 10, 10")
                .addProperty("maxRetries", "3")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true"));
    }

    private RequestSpecification requestSpecification(DockerContainer container) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(8000)
            .setBaseUri("http://" + container.getContainerIp())
            .build();
    }
}
