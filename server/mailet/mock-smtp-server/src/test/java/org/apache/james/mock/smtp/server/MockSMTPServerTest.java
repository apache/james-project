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

package org.apache.james.mock.smtp.server;

import static org.apache.james.mock.smtp.server.Fixture.ALICE;
import static org.apache.james.mock.smtp.server.Fixture.BOB;
import static org.apache.james.mock.smtp.server.Fixture.DOMAIN;
import static org.apache.james.mock.smtp.server.Fixture.JACK;
import static org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode.REQUESTED_MAIL_ACTION_NOT_TAKEN_450;
import static org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421;
import static org.apache.james.mock.smtp.server.model.SMTPCommand.DATA;
import static org.apache.james.mock.smtp.server.model.SMTPCommand.MAIL_FROM;
import static org.apache.james.mock.smtp.server.model.SMTPCommand.RCPT_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.commons.net.smtp.SMTPConnectionClosedException;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mock.smtp.server.model.Condition;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.Operator;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.SMTPExtension;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.Port;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

class MockSMTPServerTest {
    private MockSMTPServer mockServer;
    private FakeMail mail1;
    private MimeMessage mimeMessage1;
    private SMTPMessageSender smtpClient;
    private SMTPBehaviorRepository behaviorRepository;
    private ReceivedMailRepository mailRepository;

    @BeforeEach
    void setUp() throws Exception {
        behaviorRepository = new SMTPBehaviorRepository();
        mailRepository = new ReceivedMailRepository();
        mockServer = MockSMTPServer.onRandomPort(behaviorRepository, mailRepository);

        mimeMessage1 = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("any text")
            .build();
        mail1 = FakeMail.builder()
            .name("name")
            .sender(BOB)
            .recipients(ALICE, JACK)
            .mimeMessage(mimeMessage1)
            .build();

        mockServer.start();
        smtpClient = new SMTPMessageSender(DOMAIN)
            .connect("localhost", mockServer.getPort());
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Nested
    class NormalBehaviorTests {

        @Test
        void serverShouldReceiveMessageFromClient() throws Exception {
            SMTPMessageSender sender = new SMTPMessageSender(DOMAIN)
                .connect("localhost", mockServer.getPort());

            MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("test")
                .setText("any text")
                .build();

            FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(BOB)
                .recipients(ALICE, JACK)
                .mimeMessage(message)
                .build();

            sender.sendMessage(mail);

            Awaitility.await().atMost(TEN_SECONDS)
                .untilAsserted(() -> {
                    List<Mail> mails = mailRepository.list();
                    Mail.Envelope expectedEnvelope = Mail.Envelope.ofAddresses(
                        new MailAddress(BOB),
                        new MailAddress(ALICE), new MailAddress(JACK));
                    assertThat(mails)
                        .hasSize(1)
                        .allSatisfy(Throwing.consumer(assertedMail -> {
                            assertThat(assertedMail.getEnvelope()).isEqualTo(expectedEnvelope);
                            assertThat(assertedMail.getMessage()).contains(MimeMessageUtil.asString(message));
                        }));
                });
        }
    }

    @Nested
    class ExtensionTests {
        @Test
        void extraExtensionsShouldBeExposed() throws Exception {
            behaviorRepository.setSmtpExtensions(ImmutableList.of(SMTPExtension.of("DSN")));

            AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

            try {
                smtpClient.connect("localhost", mockServer.getPort().getValue());
                smtpClient.ehlo("localhost");

                assertThat(smtpClient.getReplyString())
                    .contains("250-DSN");
            } finally {
                smtpClient.disconnect();
            }
        }
    }

    @Nested
    class ESMTPParametersTest {
        @Test
        void mailFromParametersShouldBeRecognised() throws Exception {
            AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

            try {
                smtpClient.connect("localhost", mockServer.getPort().getValue());
                smtpClient.ehlo("localhost");
                smtpClient.mail("<bob@james.org> RET=HDRS ENVID=gabouzomeuh");
                smtpClient.rcpt("<alice@james.org>");
                smtpClient.sendShortMessageData("A short message...");
            } finally {
                smtpClient.disconnect();
            }

            Mail.Envelope expectedEnvelope = Mail.Envelope.builder()
                .addMailParameter(Mail.Parameter.builder()
                    .name("RET")
                    .value("HDRS")
                    .build())
                .addMailParameter(Mail.Parameter.builder()
                    .name("ENVID")
                    .value("gabouzomeuh")
                    .build())
                .from(new MailAddress(BOB))
                .addRecipientMailAddress(new MailAddress(ALICE))
                .build();

            Awaitility.await().atMost(TEN_SECONDS)
                .untilAsserted(() -> {
                    List<Mail> mails = mailRepository.list();
                    assertThat(mails)
                        .hasSize(1)
                        .allSatisfy(Throwing.consumer(assertedMail ->
                            assertThat(assertedMail.getEnvelope()).isEqualTo(expectedEnvelope)));
                });
        }

        @Test
        void rcptToParametersShouldBeRecognised() throws Exception {
            AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

            try {
                smtpClient.connect("localhost", mockServer.getPort().getValue());
                smtpClient.ehlo("localhost");
                smtpClient.mail("<bob@james.org>");
                smtpClient.rcpt("<alice@james.org> ORCPT=rfc822;alice@james.org NOTIFY=FAILURE,DELAY");
                smtpClient.rcpt("<jack@james.org> ORCPT=rfc822;jack@james.org NOTIFY=NEVER");
                smtpClient.sendShortMessageData("A short message...");
            } finally {
                smtpClient.disconnect();
            }

            Mail.Envelope expectedEnvelope = Mail.Envelope.builder()
                .from(new MailAddress(BOB))
                .addRecipient(Mail.Recipient.builder()
                    .addParameter(Mail.Parameter.builder()
                        .name("ORCPT")
                        .value("rfc822;alice@james.org")
                        .build())
                    .addParameter(Mail.Parameter.builder()
                        .name("NOTIFY")
                        .value("FAILURE,DELAY")
                        .build())
                    .address(new MailAddress(ALICE))
                    .build())
                .addRecipient(Mail.Recipient.builder()
                    .addParameter(Mail.Parameter.builder()
                        .name("ORCPT")
                        .value("rfc822;jack@james.org")
                        .build())
                    .addParameter(Mail.Parameter.builder()
                        .name("NOTIFY")
                        .value("NEVER")
                        .build())
                    .address(new MailAddress(JACK))
                    .build())
                .build();

            Awaitility.await().atMost(TEN_SECONDS)
                .untilAsserted(() -> {
                    List<Mail> mails = mailRepository.list();
                    assertThat(mails)
                        .hasSize(1)
                        .allSatisfy(Throwing.consumer(assertedMail ->
                            assertThat(assertedMail.getEnvelope()).isEqualTo(expectedEnvelope)));
                });
        }
    }

    @Nested
    class MailMockBehaviorTest {
        @Test
        void serverShouldReceiveMessageFromClient() {
            behaviorRepository.setBehaviors(new MockSMTPBehavior(
                MAIL_FROM,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "mock response"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPConnectionClosedException.class)
                .hasMessageContaining("421");
        }

        @Test
        void serverShouldReceiveMessageRecipientClient() {
            behaviorRepository.setBehaviors(new MockSMTPBehavior(
                RCPT_TO,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "mock response"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPConnectionClosedException.class)
                .hasMessageContaining("421");
        }

        @Test
        void serverShouldReceiveMessageDataClient() {
            behaviorRepository.setBehaviors(new MockSMTPBehavior(
                DATA,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "mock response"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPConnectionClosedException.class)
                .hasMessageContaining("421");
        }
    }

    @Nested
    class NumberOfAnswersTest {
        @Test
        void serverShouldKeepReceivingErrorResponseWhenAnytime() {
            behaviorRepository.setBehaviors(new MockSMTPBehavior(
                MAIL_FROM,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "mock response"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));

            sendMessageIgnoreError(mail1);

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPConnectionClosedException.class)
                .hasMessageContaining("421");
        }

        @Test
        void serverShouldDecreaseNumberOfAnswerAfterMatched() {
            int numberOfAnswer = 5;
            MockSMTPBehavior behavior = new MockSMTPBehavior(
                MAIL_FROM,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "mock response"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(numberOfAnswer));

            behaviorRepository.setBehaviors(behavior);

            sendMessageIgnoreError(mail1);

            assertThat(remainedAnswersOf(behavior))
                .isEqualTo(4);
        }

        @Test
        void serverShouldActLikeDefaultAfterGettingEnoughMatches() {
            int numberOfAnswer = 4;
            MockSMTPBehavior behavior = new MockSMTPBehavior(
                MAIL_FROM,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "mock response"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(numberOfAnswer));

            behaviorRepository.setBehaviors(behavior);

            sendMessageIgnoreError(mail1);
            sendMessageIgnoreError(mail1);
            sendMessageIgnoreError(mail1);
            sendMessageIgnoreError(mail1);

            sendMessageIgnoreError(mail1);
            Awaitility.await().atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(mailRepository.list()).hasSize(1));
        }

        @Test
        void serverShouldNotDecreaseNonMatchedBehavior() {
            int matchedBehaviorAnswers = 2;
            MockSMTPBehavior matched = new MockSMTPBehavior(
                MAIL_FROM,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "matched"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(matchedBehaviorAnswers));

            int nonMatchedBehaviorAnswers = 3;
            MockSMTPBehavior nonMatched = new MockSMTPBehavior(
                RCPT_TO,
                new Condition.OperatorCondition(Operator.CONTAINS, "nonMatched"),
                new Response(SERVICE_NOT_AVAILABLE_421, "non matched"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(nonMatchedBehaviorAnswers));

            behaviorRepository.setBehaviors(matched, nonMatched);

            sendMessageIgnoreError(mail1);

            assertThat(remainedAnswersOf(nonMatched))
                .isEqualTo(nonMatchedBehaviorAnswers);
        }

        @Test
        void serverShouldDecreaseRemainingAnswersOnlyOncePerMessage() {
            int firstBehaviorAnswers = 2;
            MockSMTPBehavior matchesAnyFrom = new MockSMTPBehavior(
                MAIL_FROM,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "any from will be matched"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(firstBehaviorAnswers));

            int secondBehaviorAnswers = 3;
            MockSMTPBehavior matchesAnyRecipient = new MockSMTPBehavior(
                RCPT_TO,
                Condition.MATCH_ALL,
                new Response(SERVICE_NOT_AVAILABLE_421, "any recipient will be matched"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(secondBehaviorAnswers));

            behaviorRepository.setBehaviors(matchesAnyFrom, matchesAnyRecipient);

            sendMessageIgnoreError(mail1);

            assertThat(remainedAnswersOf(matchesAnyFrom) + remainedAnswersOf(matchesAnyRecipient))
                .isEqualTo(4);
        }
    }

    @Nested
    class ConditionFilteringTest {

        @Test
        void serverShouldBehaveOnMatchedFromBehavior() {
            MockSMTPBehavior matched = new MockSMTPBehavior(
                MAIL_FROM,
                new Condition.OperatorCondition(Operator.CONTAINS, BOB),
                new Response(SERVICE_NOT_AVAILABLE_421, "sender bob should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

            MockSMTPBehavior nonMatched = new MockSMTPBehavior(
                MAIL_FROM,
                new Condition.OperatorCondition(Operator.CONTAINS, ALICE),
                new Response(REQUESTED_MAIL_ACTION_NOT_TAKEN_450, "sender alice should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

            behaviorRepository.setBehaviors(matched, nonMatched);

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPConnectionClosedException.class)
                .hasMessageContaining(String.valueOf(SERVICE_NOT_AVAILABLE_421.getRawCode()));
        }

        @Test
        void serverShouldBehaveOnMatchedRecipientBehavior() {
            MockSMTPBehavior nonMatched = new MockSMTPBehavior(
                RCPT_TO,
                new Condition.OperatorCondition(Operator.CONTAINS, BOB),
                new Response(SERVICE_NOT_AVAILABLE_421, "recipient bob should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

            MockSMTPBehavior matched = new MockSMTPBehavior(
                RCPT_TO,
                new Condition.OperatorCondition(Operator.CONTAINS, ALICE),
                new Response(REQUESTED_MAIL_ACTION_NOT_TAKEN_450, "recipient alice should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

            behaviorRepository.setBehaviors(matched, nonMatched);

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPSendingException.class)
                .hasMessageContaining(String.valueOf(REQUESTED_MAIL_ACTION_NOT_TAKEN_450.getRawCode()));
        }

        @Test
        void serverShouldBehaveOnMatchedDataBehavior() {
            MockSMTPBehavior nonMatched = new MockSMTPBehavior(
                DATA,
                new Condition.OperatorCondition(Operator.CONTAINS, "nonRelatedString"),
                new Response(SERVICE_NOT_AVAILABLE_421, "contains 'nonRelatedString' should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

            MockSMTPBehavior matched = new MockSMTPBehavior(
                DATA,
                new Condition.OperatorCondition(Operator.CONTAINS, "text"),
                new Response(REQUESTED_MAIL_ACTION_NOT_TAKEN_450, "contains 'text' should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

            behaviorRepository.setBehaviors(matched, nonMatched);

            assertThatThrownBy(() -> smtpClient.sendMessage(mail1))
                .isInstanceOf(SMTPSendingException.class)
                .hasMessageContaining(String.valueOf(REQUESTED_MAIL_ACTION_NOT_TAKEN_450.getRawCode()));
        }

        @Test
        void serverShouldDecreaseAnswerCountOnMatchedBehavior() {
            int matchedAnswerOriginalCount = 10;
            MockSMTPBehavior matched = new MockSMTPBehavior(
                MAIL_FROM,
                new Condition.OperatorCondition(Operator.CONTAINS, BOB),
                new Response(SERVICE_NOT_AVAILABLE_421, "sender bob should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(matchedAnswerOriginalCount));

            int nonMatchedAnswerOriginalCount = 5;
            MockSMTPBehavior nonMatched = new MockSMTPBehavior(
                MAIL_FROM,
                new Condition.OperatorCondition(Operator.CONTAINS, ALICE),
                new Response(REQUESTED_MAIL_ACTION_NOT_TAKEN_450, "sender alice should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(nonMatchedAnswerOriginalCount));

            behaviorRepository.setBehaviors(matched, nonMatched);

            sendMessageIgnoreError(mail1);

            assertThat(behaviorRepository.getBehaviorInformation(matched)
                    .remainingAnswersCounter())
                .contains(matchedAnswerOriginalCount - 1);
        }

        @Test
        void serverShouldNotDecreaseAnswerCountOnMonMatchedBehavior() {
            int matchedAnswerOriginalCount = 10;
            MockSMTPBehavior matched = new MockSMTPBehavior(
                MAIL_FROM,
                new Condition.OperatorCondition(Operator.CONTAINS, BOB),
                new Response(SERVICE_NOT_AVAILABLE_421, "sender bob should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(matchedAnswerOriginalCount));

            int nonMatchedAnswerOriginalCount = 5;
            MockSMTPBehavior nonMatched = new MockSMTPBehavior(
                MAIL_FROM,
                new Condition.OperatorCondition(Operator.CONTAINS, ALICE),
                new Response(REQUESTED_MAIL_ACTION_NOT_TAKEN_450, "sender alice should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(nonMatchedAnswerOriginalCount));

            behaviorRepository.setBehaviors(matched, nonMatched);

            sendMessageIgnoreError(mail1);

            assertThat(remainedAnswersOf(nonMatched))
                .isEqualTo(nonMatchedAnswerOriginalCount);
        }

        @Test
        void multipleQualifiedBehaviorsShouldNotOnlyBeingDecreasedOnlyOncePerMessage() {
            int matchedOriginalCount = 10;
            MockSMTPBehavior matched = new MockSMTPBehavior(
                RCPT_TO,
                new Condition.OperatorCondition(Operator.CONTAINS, ALICE),
                new Response(SERVICE_NOT_AVAILABLE_421, "recipient alice should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(matchedOriginalCount));

            int qualifiedButNotMatchedOriginalCount = 5;
            MockSMTPBehavior qualifiedButNotMatched = new MockSMTPBehavior(
                RCPT_TO,
                new Condition.OperatorCondition(Operator.CONTAINS, JACK),
                new Response(REQUESTED_MAIL_ACTION_NOT_TAKEN_450, "recipient jack should match"),
                MockSMTPBehavior.NumberOfAnswersPolicy.times(qualifiedButNotMatchedOriginalCount));

            behaviorRepository.setBehaviors(matched, qualifiedButNotMatched);

            sendMessageIgnoreError(mail1);

            assertThat(remainedAnswersOf(matched) + remainedAnswersOf(qualifiedButNotMatched))
                .isEqualTo(matchedOriginalCount + qualifiedButNotMatchedOriginalCount - 1);
        }
    }

    @Test
    void serverStartShouldOpenASmtpPort() {
        assertThatCode(() -> new SMTPMessageSender(DOMAIN)
                .connect("localhost", mockServer.getPort()))
            .doesNotThrowAnyException();
    }

    @Test
    void serverShouldBeAbleToStop() {
        Port port = mockServer.getPort();

        mockServer.stop();
        assertThatThrownBy(() -> new SMTPMessageSender(DOMAIN)
                .connect("localhost", port))
            .isInstanceOf(ConnectException.class)
            .hasMessageContaining("Connection refused");
    }

    @Test
    void serverStartShouldBeIdempotent() {
        assertThatCode(mockServer::start)
            .doesNotThrowAnyException();
    }

    private void sendMessageIgnoreError(FakeMail mail) {
        try {
            smtpClient.sendMessage(mail);
        } catch (MessagingException | IOException e) {
            // ignore error
        }
    }

    private Integer remainedAnswersOf(MockSMTPBehavior nonMatched) {
        return behaviorRepository
            .getBehaviorInformation(nonMatched)
            .remainingAnswersCounter()
            .get();
    }
}