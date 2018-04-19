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

import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.restassured.specification.RequestSpecification;

public class ToRepositoryTest {
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    public static final String CUSTOM_REPOSITORY = "file://var/mail/custom/";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private MailRepositoryProbeImpl probe;
    private RequestSpecification webAdminAPI;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToRepository.class)
                    .addProperty("repositoryPath", CUSTOM_REPOSITORY)));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);

        probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
        webAdminAPI = WebAdminUtils.buildRequestSpecification(
            jamesServer.getProbe(WebAdminGuiceProbe.class)
                .getWebAdminPort())
            .build();
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void incomingShouldBeStoredInProcessorByDefault() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(RECIPIENT, RECIPIENT)
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2);
    }

    @Test
    public void userShouldBeAbleToAccessReprocessedMails() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(RECIPIENT, RECIPIENT)
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2);

        with()
            .spec(webAdminAPI)
            .queryParam("processor", ProcessorConfiguration.STATE_TRANSPORT)
            .queryParam("action", "reprocess")
            .patch(MailRepositoriesRoutes.MAIL_REPOSITORIES
                + "/" + URLEncoder.encode(CUSTOM_REPOSITORY, StandardCharsets.UTF_8.displayName())
                + "/mails")
            .jsonPath()
            .get("taskId");

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .hasMessageCount(2);
        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 0);
    }

    @Test
    public void userShouldBeAbleToAccessReprocessedMail() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(RECIPIENT, RECIPIENT)
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2);
        String key = probe.listMailKeys(CUSTOM_REPOSITORY).get(0);

        with()
            .spec(webAdminAPI)
            .queryParam("processor", ProcessorConfiguration.STATE_TRANSPORT)
            .queryParam("action", "reprocess")
            .patch(MailRepositoriesRoutes.MAIL_REPOSITORIES
                + "/" + URLEncoder.encode(CUSTOM_REPOSITORY, StandardCharsets.UTF_8.displayName())
                + "/mails/" + key)
            .jsonPath()
            .get("taskId");

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .hasMessageCount(1);
        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }
}
