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
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.util.Arrays;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.Forward;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.NotifyPostmaster;
import org.apache.james.transport.mailets.NotifySender;
import org.apache.james.transport.mailets.Redirect;
import org.apache.james.transport.mailets.Resend;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.Mailet;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BounceIntegrationTest {
    public static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;
    public static final String POSTMASTER_PASSWORD = "postmasterSecret";
    public static final String SENDER = "bounce.receiver@" + DEFAULT_DOMAIN;
    public static final String SENDER_PASSWORD = "senderSecret";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void dsnBounceMailetShouldDeliverBounce() throws Exception {
        setup(DSNBounce.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(SENDER, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private void setup(Class<? extends Mailet> mailet, Pair<String, String>... additionalProperties) throws Exception {
        MailetConfiguration.Builder mailetConfiguration = MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(mailet)
                .addProperty("passThrough", "false");

        Arrays.stream(additionalProperties)
                    .forEach(property -> mailetConfiguration.addProperty(property.getKey(), property.getValue()));

        jamesServer = TemporaryJamesServer.builder()
                .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
                .withMailetContainer(
                        generateMailetContainerConfiguration(mailetConfiguration))
                .build(temporaryFolder.newFolder());

        jamesServer.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DEFAULT_DOMAIN)
                .addUser(RECIPIENT, PASSWORD)
                .addUser(SENDER, SENDER_PASSWORD)
                .addUser(POSTMASTER, POSTMASTER_PASSWORD);
    }

    @Test
    public void bounceMailetShouldDeliverBounce() throws Exception {
        setup(Bounce.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(SENDER, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void forwardMailetShouldDeliverBounce() throws Exception {
        setup(Forward.class, Pair.of("forwardTo", SENDER));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void redirectMailetShouldDeliverBounce() throws Exception {
        setup(Redirect.class, Pair.of("recipients", SENDER));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void resendMailetShouldDeliverBounce() throws Exception {
        setup(Resend.class, Pair.of("recipients", SENDER));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void notifySenderMailetShouldDeliverBounce() throws Exception {
        setup(NotifySender.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(SENDER, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void notifyPostmasterMailetShouldDeliverBounce() throws Exception {
        setup(NotifyPostmaster.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(POSTMASTER, POSTMASTER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private MailetContainer.Builder generateMailetContainerConfiguration(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
            .postmaster(POSTMASTER)
            .putProcessor(transport())
            .putProcessor(bounces(redirectionMailetConfiguration));
    }

    private ProcessorConfiguration.Builder transport() {
        // This processor delivers emails to SENDER and POSTMASTER
        // Other recipients will be bouncing
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(SENDER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(POSTMASTER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.TO_BOUNCE);
    }

    public static ProcessorConfiguration.Builder bounces(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return ProcessorConfiguration.bounces()
            .addMailet(redirectionMailetConfiguration);
    }
}
