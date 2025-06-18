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

import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.AddDeliveredToHeader;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.mailets.VacationMailet;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class AddDeliveredToHeaderTest {
    private static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;
    public static final String RECIPIENT2_UTF8 = "rené@" + DEFAULT_DOMAIN;
    public static final String RECIPIENT2 = "rene@" + DEFAULT_DOMAIN;
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .postmaster(POSTMASTER)
                .putProcessor(transport()))
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(RECIPIENT2, PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void receivedMessagesShouldContainDeliveredToHeaders() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessageHeaders())
            .contains(AddDeliveredToHeader.DELIVERED_TO + ": " + RECIPIENT);
    }

    @Test
    void receivedMessagesShouldContainDeliveredToHeadersI8N() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addUserAliasMapping("rené", "james.org", RECIPIENT2);
        String message = "FROM: " + RECIPIENT2_UTF8 + "\r\n" +
            "subject: testé\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Encoding: 8bit\r\n" +
            "\r\n" +
            "contenté\r\n";
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT2_UTF8, message);

        Thread.sleep(1000);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT2, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(testIMAPClient.readFirstMessage())
            .contains(RECIPIENT2_UTF8)
            .contains("testé")
            .contains("contenté");
    }

    private ProcessorConfiguration.Builder transport() {
        return ProcessorConfiguration.transport()
            .enableJmx(false)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RecipientRewriteTable.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(VacationMailet.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(JMAPFiltering.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(SMTPAuthSuccessful.class)
                .mailet(RemoteDelivery.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "5000, 100000, 500000")
                .addProperty("maxRetries", "3")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "10")
                .addProperty("sendpartial", "true")
                .addProperty("bounceProcessor", "bounces"))
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", "error"));
    }
}
