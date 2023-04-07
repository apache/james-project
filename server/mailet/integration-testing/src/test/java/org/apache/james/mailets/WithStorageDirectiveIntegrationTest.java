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
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.WithStorageDirective;
import org.apache.james.transport.matchers.SenderIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class WithStorageDirectiveIntegrationTest {
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void targetFolderNameShouldWork(@TempDir File temporaryFolder) throws Exception {
        setUp(temporaryFolder, MailetConfiguration.builder()
            .matcher(SenderIsLocal.class)
            .mailet(WithStorageDirective.class)
            .addProperty("targetFolderName", "target"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .create("target");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.select("target")
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void seenShouldWork(@TempDir File temporaryFolder) throws Exception {
        setUp(temporaryFolder, MailetConfiguration.builder()
            .matcher(SenderIsLocal.class)
            .mailet(WithStorageDirective.class)
            .addProperty("seen", "true"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.select("INBOX")
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(testIMAPClient.hasAMessageWithFlags("\\Seen")).isTrue();
    }

    @Test
    void importantShouldWork(@TempDir File temporaryFolder) throws Exception {
        setUp(temporaryFolder, MailetConfiguration.builder()
            .matcher(SenderIsLocal.class)
            .mailet(WithStorageDirective.class)
            .addProperty("important", "true"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.select("INBOX")
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(testIMAPClient.hasAMessageWithFlags("\\Flagged")).isTrue();
    }

    @Test
    void keywordsShouldWork(@TempDir File temporaryFolder) throws Exception {
        setUp(temporaryFolder, MailetConfiguration.builder()
            .matcher(SenderIsLocal.class)
            .mailet(WithStorageDirective.class)
            .addProperty("keywords", "abc,def"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.select("INBOX")
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(testIMAPClient.hasAMessageWithFlags("abc")).isTrue();
        assertThat(testIMAPClient.hasAMessageWithFlags("def")).isTrue();
    }

    private void setUp(File temporaryFolder, MailetConfiguration.Builder mailet) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(mailet)
                    .addMailetsFrom(CommonProcessors.transport())))
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);
    }
}
