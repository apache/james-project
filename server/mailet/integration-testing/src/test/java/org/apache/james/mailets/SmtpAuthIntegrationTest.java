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

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SmtpAuthIntegrationTest {
    private static final String FROM = "fromuser@" + DEFAULT_DOMAIN;
    private static final MailRepositoryUrl DROPPED_MAILS = MailRepositoryUrl.from("memory://var/mail/dropped-mails/");

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private MailRepositoryProbeImpl repositoryProbe;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        ProcessorConfiguration.Builder rootProcessor = ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(SMTPAuthSuccessful.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(MailetConfiguration.TO_BOUNCE);

        MailetContainer.Builder mailetContainer = TemporaryJamesServer.defaultMailetContainerConfiguration()
            .putProcessor(rootProcessor)
            .putProcessor(CommonProcessors.deliverOnlyTransport())
            .putProcessor(bounces());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailetContainer)
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .doNotVerifyIdentity()
                .build())
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        repositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
    }

    private ProcessorConfiguration.Builder bounces() {
        return ProcessorConfiguration.bounces()
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", DROPPED_MAILS.asString()));
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void authenticatedSmtpSessionsShouldBeDelivered() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void nonAuthenticatedSmtpSessionsShouldNotBeDelivered() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> repositoryProbe.getRepositoryMailCount(DROPPED_MAILS) == 1);
        assertThat(
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(FROM, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .hasAMessage())
            .isFalse();
    }

    @Test
    void mixedCaseSenderMailShouldBeDelivered() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage("FROMUSER@" + DEFAULT_DOMAIN, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }
}
