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

package org.apache.james.transport.mailets;

import static io.restassured.RestAssured.given;
import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.mailets.configuration.ProcessorConfiguration.TRANSPORT_PROCESSOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.util.Modules;

import io.restassured.specification.RequestSpecification;

class MailReprocessingIntegrationTest {
    private static final MailRepositoryUrl REPOSITORY_A = MailRepositoryUrl.from("memory://var/mail/a");
    private static final MailRepositoryUrl REPOSITORY_B = MailRepositoryUrl.from("memory://var/mail/b");

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private RequestSpecification specification;

    @BeforeEach
    void createJamesServer(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailets = TemporaryJamesServer.defaultMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", REPOSITORY_A.asString())))
            .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", REPOSITORY_B.asString())));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE, MemoryJamesServerMain.WEBADMIN_TESTING))
            .withMailetContainer(mailets)
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD);

        specification = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void reprocessingShouldAllowToTargetASpecificProcessor() throws Exception {
        // Given an incoming email
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));
        // Being stored in MailRepository B
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(REPOSITORY_B));

        // When I reprocess it
        given()
            .spec(specification)
            .param("action", "reprocess")
            .param("queue", MailQueueFactory.SPOOL.asString())
            .param("processor", TRANSPORT_PROCESSOR)
        .patch("/mailRepositories/" + REPOSITORY_B.getPath().urlEncoded() + "/mails");

        // Then I can move it to repository A
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(REPOSITORY_A));
        assertThat(containsExactlyOneMail(REPOSITORY_A)).isTrue();
    }

    @Test
    void reprocessingShouldPreserveStateWhenProcessorIsNotSpecified() throws Exception {
        // Given an incoming email
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));
        // Being stored in MailRepository B
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(REPOSITORY_B));

        // I reprocess it
        given()
            .spec(specification)
            .param("action", "reprocess")
            .param("queue", MailQueueFactory.SPOOL.asString())
            .param("processor", TRANSPORT_PROCESSOR)
        .patch("/mailRepositories/" + REPOSITORY_B.getPath().urlEncoded() + "/mails");

        // I can move it to repository A
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(REPOSITORY_A));

        // When I reprocess it without target processor
        String taskId = given()
            .spec(specification)
            .param("action", "reprocess")
            .param("queue", MailQueueFactory.SPOOL.asString())
        .patch("/mailRepositories/" + REPOSITORY_A.getPath().urlEncoded() + "/mails")
            .jsonPath()
            .get("taskId");

        given()
            .spec(specification)
            .get("/tasks/" + taskId + "/await");

        // It then is processed by the transport processor again
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(REPOSITORY_A));
        assertThat(containsExactlyOneMail(REPOSITORY_A)).isTrue();
    }

    @Test
    void reprocessingShouldProcessAsErrorWhenUnknownMailProcessor() throws Exception {
        // Given an incoming email
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));
        // Being stored in MailRepository B
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(REPOSITORY_B));

        // When I reprocess it
        given()
            .spec(specification)
            .param("action", "reprocess")
            .param("queue", MailQueueFactory.SPOOL.asString())
            .param("processor", "unknown")
            .patch("/mailRepositories/" + REPOSITORY_B.getPath().urlEncoded() + "/mails");

        // Then I can move it to repository A
        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(ERROR_REPOSITORY));
        assertThat(containsExactlyOneMail(ERROR_REPOSITORY)).isTrue();
    }

    private boolean containsExactlyOneMail(MailRepositoryUrl repositoryUrl) {
        try {
            return given()
                .spec(specification)
            .get("/mailRepositories/" + repositoryUrl.getPath().urlEncoded() + "/mails")
                .jsonPath()
                .getList(".")
                .size() == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
