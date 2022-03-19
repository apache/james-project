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

package org.apache.james.smtp;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.io.File;

import jakarta.mail.MessagingException;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.matchers.SMTPIsAuthNetwork;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SmtpContentTypeTest {
    private static final String FROM = "fromuser@" + DEFAULT_DOMAIN;
    private static final String TO = "to@any.com";
    public static final String SUBJECT = "test";

    @RegisterExtension
    public static FakeSmtp fakeSmtp = FakeSmtp.withDefaultPort();
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    private void createJamesServer(File temporaryFolder, SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.transport()
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport())
                .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                    .matcher(SMTPIsAuthNetwork.class)
                    .addProperty("gateway", fakeSmtp.getContainer().getContainerIp()))
                .addMailet(MailetConfiguration.TO_BOUNCE));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withSmtpConfiguration(smtpConfiguration)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        fakeSmtp.clean();
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void userShouldBeAbleToReceiveMessagesWithGoodContentType(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(mailWithContentType("text/plain;"));

        awaitAtMostOneMinute
            .untilAsserted(() -> fakeSmtp.assertEmailReceived(response -> response
                .body("", hasSize(1))
                .body("[0].from", equalTo(FROM))
                .body("[0].subject", equalTo(SUBJECT))
                .body("[0].headers.content-type", startsWith("text/plain;"))));
    }

    @Test
    void userShouldBeAbleToReceiveMessagesWithBadContentType(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(mailWithContentType("wrong|Content-Type;"));

        awaitAtMostOneMinute
            .untilAsserted(() -> fakeSmtp.assertEmailReceived(response -> response
                .body("", hasSize(1))
                .body("[0].from", equalTo(FROM))
                .body("[0].subject", equalTo(SUBJECT))
                .body("[0].headers.content-type", not(startsWith("wrong|Content-Type;")))));
    }

    private Mail mailWithContentType(String contentType) throws MessagingException {
        return MailImpl.builder()
            .name("mail1")
            .sender(FROM)
            .addRecipient(TO)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject(SUBJECT)
                .setText("content", contentType))
            .build();
    }
}
