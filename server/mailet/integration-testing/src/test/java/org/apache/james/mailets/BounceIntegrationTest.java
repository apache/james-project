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

import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.Forward;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.NotifyPostmaster;
import org.apache.james.transport.mailets.NotifySender;
import org.apache.james.transport.mailets.Redirect;
import org.apache.james.transport.mailets.Resend;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BounceIntegrationTest {
    public static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;
    public static final String BOUNCE_RECEIVER = "bounce.receiver@" + DEFAULT_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void dsnBounceMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(DSNBounce.class)
                .addProperty("passThrough", "false")))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(BOUNCE_RECEIVER, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    @Test
    public void bounceMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Bounce.class)
                .addProperty("passThrough", "false")))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(BOUNCE_RECEIVER, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    @Test
    public void forwardMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Forward.class)
                .addProperty("forwardTo", BOUNCE_RECEIVER)
                .addProperty("passThrough", "false")))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    @Test
    public void redirectMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(Redirect.class)
                    .addProperty("recipients", BOUNCE_RECEIVER)
                    .addProperty("passThrough", "false")))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    @Test
    public void resendMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(Resend.class)
                    .addProperty("recipients", BOUNCE_RECEIVER)
                    .addProperty("passThrough", "false")))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    @Test
    public void notifySenderMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(NotifySender.class)
                    .addProperty("passThrough", "false")))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(BOUNCE_RECEIVER, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    @Test
    public void notifyPostmasterMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(NotifyPostmaster.class)
                    .addProperty("passThrough", "false")))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(POSTMASTER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(POSTMASTER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }

    private MailetContainer generateMailetContainerConfiguration(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return MailetContainer.builder()
            .postmaster(POSTMASTER)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(transport())
            .addProcessor(bounces(redirectionMailetConfiguration))
            .build();
    }

    private ProcessorConfiguration transport() {
        // This processor delivers emails to BOUNCE_RECEIVER and POSTMASTER
        // Other recipients will be bouncing
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(BOUNCE_RECEIVER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(POSTMASTER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.STATE_BOUNCES))
            .build();
    }

    public static ProcessorConfiguration bounces(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return ProcessorConfiguration.bounces()
            .addMailet(redirectionMailetConfiguration)
            .build();
    }
}
