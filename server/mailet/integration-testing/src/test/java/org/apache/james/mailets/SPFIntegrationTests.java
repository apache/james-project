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

import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SieveProbeImpl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.SPF;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.HasMailAttribute;
import org.apache.james.transport.matchers.HasMailAttributeWithValue;
import org.apache.james.transport.matchers.SizeGreaterThan;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SPFIntegrationTests {
    public static final String FROM = "user@gmail.com";
    public static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
                .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
                .withMailetContainer(
                        generateMailetContainerConfiguration())
                .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void shouldRejectSpoofedEmail() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    private MailetContainer.Builder generateMailetContainerConfiguration() {
        return TemporaryJamesServer.defaultMailetContainerConfiguration()
                .postmaster(POSTMASTER)
                .putProcessor(ProcessorConfiguration.transport()
                        .addMailet(MailetConfiguration.builder()
                                .matcher(All.class)
                                .mailet(SPF.class)
                                .addProperty("checkLocalIps", "true"))
                        .addMailet(MailetConfiguration.builder()
                                .matcher(HasMailAttributeWithValue.class)
                                .matcherCondition("org.apache.james.transport.mailets.spf.result, softfail")
                                .mailet(ToRepository.class)
                                .addProperty("repositoryPath", ERROR_REPOSITORY.asString())
                                .addProperty("passThrough", "false"))
                        .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));
    }
}
