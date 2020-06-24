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

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.activemq.ActiveMQQueueModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AddFooterTest {
    public static final String MATCH_ME = "Match me";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withOverrides(new ActiveMQQueueModule())
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(AddFooter.class)
                        .addProperty("text", MATCH_ME))
                    .addMailetsFrom(CommonProcessors.transport())))
            .build(temporaryFolder.newFolder());
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void shouldAddFooterWhenSimpleMessage() throws Exception {
        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("Any content")
                    .setSubject("test"))
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        String processedMessage = testIMAPClient.readFirstMessage();
        assertThat(processedMessage).contains(MATCH_ME);
    }

    @Test
    public void shouldAddFooterWhenMultipartMessage() throws Exception {
        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .type("text/plain")
                            .data("Text message"),
                        MimeMessageBuilder.bodyPartBuilder()
                            .data("IMAGE")
                            .type("application/data"))
                    .setSubject("test"))
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        String processedMessage = testIMAPClient.readFirstMessage();
        assertThat(processedMessage).contains(MATCH_ME);
    }
}
