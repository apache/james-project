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

import static com.jayway.restassured.RestAssured.given;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.util.Optional;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.dlp.Dlp;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.mailet.base.test.FakeMail;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.util.Modules;
import com.jayway.restassured.specification.RequestSpecification;

public class DlpIntegrationTest {
    public static final String REPOSITORY_PREFIX = "file://var/mail/dlp/quarantine/";
    public static final JwtConfiguration NO_JWT_CONFIGURATION = new JwtConfiguration(Optional.empty());

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
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
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Null.class)));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(Modules.override(
                    MemoryJamesServerMain.SMTP_AND_IMAP_MODULE,
                    MemoryJamesServerMain.WEBADMIN)
                .with(
                    binder -> binder.bind(JwtConfiguration.class).toInstance(NO_JWT_CONFIGURATION),
                    binder -> binder.bind(AuthenticationFilter.class).to(NoAuthenticationFilter.class),
                    binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION)))
            .withMailetContainer(mailets)
            .build(folder);

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD);
        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        webAdminGuiceProbe.await();
        specification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build();
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

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
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

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));

        MailRepositoryUrl repositoryUrl = MailRepositoryUrl.from(REPOSITORY_PREFIX + DEFAULT_DOMAIN);
        given()
            .spec(specification)
        .get("/mailRepositories/" + repositoryUrl.urlEncoded() + "/mails")
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

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
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
            .put("/mailRepositories/" + repositoryUrl.urlEncoded());

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

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(FROM)
                    .setText("match me"))
                .sender(FROM)
                .recipient(RECIPIENT));

        awaitAtMostOneMinute.until(() -> containsExactlyOneMail(repositoryUrl));
    }

    private boolean containsExactlyOneMail(MailRepositoryUrl repositoryUrl) {
        try {
            return given()
                .spec(specification)
                .get("/mailRepositories/" + repositoryUrl.urlEncoded() + "/mails")
                .prettyPeek()
                .jsonPath()
                .getList(".")
                .size() == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
