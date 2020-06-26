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
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.matchers.dlp.Dlp;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.mailet.base.test.FakeMail;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.util.Modules;

import io.restassured.specification.RequestSpecification;

public class DlpIntegrationTest {
    public static final String REPOSITORY_PREFIX = "memory://var/mail/dlp/quarantine/";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private RequestSpecification specification;

    private void createJamesServer(MailetConfiguration.Builder dlpMailet) throws Exception {
        MailetContainer.Builder mailets = TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(
                ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(dlpMailet)
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE, MemoryJamesServerMain.WEBADMIN_TESTING))
            .withMailetContainer(mailets)
            .build(folder.newFolder());

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD);
        specification = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void dlpShouldStoreMatchingEmails() throws Exception {
        createJamesServer(MailetConfiguration.builder()
            .matcher(Dlp.class)
            .mailet(ToSenderDomainRepository.class)
            .addProperty(ToSenderDomainRepository.URL_PREFIX, REPOSITORY_PREFIX));

        given()
            .spec(specification)
            .body("{\"rules\":[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"match me\"," +
                "  \"explanation\": \"A simple DLP rule.\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": true" +
                "}]}")
            .put("/dlp/rules/" + DEFAULT_DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));

        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(MailRepositoryUrl.from(REPOSITORY_PREFIX + DEFAULT_DOMAIN)));
    }

    @Test
    public void dlpShouldNotCreateRepositoryWhenNotAllowed() throws Exception {
        createJamesServer(MailetConfiguration.builder()
            .matcher(Dlp.class)
            .mailet(ToSenderDomainRepository.class)
            .addProperty(ToSenderDomainRepository.URL_PREFIX, REPOSITORY_PREFIX)
            .addProperty(ToSenderDomainRepository.ALLOW_REPOSITORY_CREATION, "false"));

        given()
            .spec(specification)
            .body("{\"rules\":[[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"match me\"," +
                "  \"explanation\": \"A simple DLP rule.\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": true" +
                "}]}")
            .put("/dlp/rules/" + DEFAULT_DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));

        MailRepositoryUrl repositoryUrl = MailRepositoryUrl.from(REPOSITORY_PREFIX + DEFAULT_DOMAIN);
        given()
            .spec(specification)
        .get("/mailRepositories/" + repositoryUrl.getPath().urlEncoded() + "/mails")
            .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void dlpShouldCreateRepositoryWhenAllowed() throws Exception {
        createJamesServer(MailetConfiguration.builder()
            .matcher(Dlp.class)
            .mailet(ToSenderDomainRepository.class)
            .addProperty(ToSenderDomainRepository.URL_PREFIX, REPOSITORY_PREFIX)
            .addProperty(ToSenderDomainRepository.ALLOW_REPOSITORY_CREATION, "true"));
        MailRepositoryUrl repositoryUrl = MailRepositoryUrl.from(REPOSITORY_PREFIX + DEFAULT_DOMAIN);

        given()
            .spec(specification)
            .body("{\"rules\":[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"match me\"," +
                "  \"explanation\": \"A simple DLP rule.\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": true" +
                "}]}")
            .put("/dlp/rules/" + DEFAULT_DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));

        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(repositoryUrl));
    }

    @Test
    public void dlpShouldStoreMailWhenNotAllowedButRepositoryExists() throws Exception {
        createJamesServer(MailetConfiguration.builder()
            .matcher(Dlp.class)
            .mailet(ToSenderDomainRepository.class)
            .addProperty(ToSenderDomainRepository.URL_PREFIX, REPOSITORY_PREFIX)
            .addProperty(ToSenderDomainRepository.ALLOW_REPOSITORY_CREATION, "false"));

        MailRepositoryUrl repositoryUrl = MailRepositoryUrl.from(REPOSITORY_PREFIX + DEFAULT_DOMAIN);
        given()
            .spec(specification)
            .param("protocol", repositoryUrl.getProtocol().getValue())
            .put("/mailRepositories/" + repositoryUrl.getPath().urlEncoded());

        given()
            .spec(specification)
            .body("{\"rules\":[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"match me\"," +
                "  \"explanation\": \"A simple DLP rule.\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": true" +
                "}]}")
            .put("/dlp/rules/" + DEFAULT_DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));

        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(repositoryUrl));
    }

    @Test
    public void dlpShouldBeAbleToReadMailContentWithAttachments() throws Exception {
        createJamesServer(MailetConfiguration.builder()
            .matcher(Dlp.class)
            .mailet(ToSenderDomainRepository.class)
            .addProperty(ToSenderDomainRepository.URL_PREFIX, REPOSITORY_PREFIX)
            .addProperty(ToSenderDomainRepository.ALLOW_REPOSITORY_CREATION, "false"));

        MailRepositoryUrl repositoryUrl = MailRepositoryUrl.from(REPOSITORY_PREFIX + DEFAULT_DOMAIN);
        given()
            .spec(specification)
            .param("protocol", repositoryUrl.getProtocol().getValue())
            .put("/mailRepositories/" + repositoryUrl.getPath().urlEncoded());

        given()
            .spec(specification)
            .body("{\"rules\":[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"matchMe\"," +
                "  \"explanation\": \"\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": true" +
                "}]}")
            .put("/dlp/rules/" + DEFAULT_DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("eml/dlp_read_mail_with_attachment.eml"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).containsSequence("dlp subject");

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
