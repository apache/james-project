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
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.MimeMessage;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
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

public class StripAttachmentTest {
    private static final String FROM = "fromUser@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(StripAttachment.class)
                    .addProperty("attribute", "my.attribute")
                    .addProperty("remove", "all")
                    .addProperty("notpattern", ".*.tmp.*"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(OnlyText.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(RecoverAttachment.class)
                    .addProperty("attribute", "my.attribute"))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder.newFolder());

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    /**
     * The main goal of this test is to check StripAttachment.
     * - StripAttachments put *.tmp attachments in an attribute and remove all other
     * - OnlyText keeps only text part
     * - RecoverAttachment recovers attachments from attribute
     */
    @Test
    public void stripAttachmentShouldPutAttachmentsInMailAttributeWhenConfiguredForIt() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data("Not matching attachment")
                    .filename("not_matching.tmp")
                    .disposition("attachment"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data("Matching attachment")
                    .filename("temp.zip")
                    .disposition("attachment"))
            .setSubject("test")
            .build();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains("Matching attachment");
    }
}
