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

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.CountinExecutionMailet;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

public class ExecutionCountTest {
    @RegisterExtension
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    public void test() {
        CountinExecutionMailet.reset();
    }

    @AfterEach
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void partialMatchShouldLeadToSingleExecutionOfMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                        .addMailet(MailetConfiguration.BCC_STRIPPER)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(RecipientIs.class)
                            .matcherCondition(RECIPIENT)
                            .mailet(CountinExecutionMailet.class)
                            .build())
                        .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountinExecutionMailet.executionCount()).isEqualTo(1);
    }
}
