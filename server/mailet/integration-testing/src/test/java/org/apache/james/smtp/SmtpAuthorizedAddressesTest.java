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
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitOneMinute;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.SMTPIsAuthNetwork;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SmtpAuthorizedAddressesTest {
    private static final String FROM = "fromuser@" + DEFAULT_DOMAIN;
    private static final String TO = "to@any.com";

    @Rule
    public FakeSmtp fakeSmtp = new FakeSmtp();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        fakeSmtp.awaitStarted(awaitOneMinute);
    }

    private void createJamesServer(SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .addProcessor(ProcessorConfiguration.transport()
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

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
    }

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void userShouldBeAbleToRelayMessagesWhenInAcceptedNetwork() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("127.0.0.0/8"));

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, TO)
            .awaitSent(awaitOneMinute);

        awaitOneMinute.until(() -> fakeSmtp.isReceived(response -> response
            .body("", hasSize(1))
            .body("[0].from", equalTo(FROM))
            .body("[0].subject", equalTo("test"))));
    }

    @Test
    public void userShouldNotBeAbleToRelayMessagesWhenOutOfAcceptedNetwork() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, TO)
            .awaitSentFail(awaitOneMinute);
    }

    @Test
    public void userShouldBeAbleToRelayMessagesWhenOutOfAcceptedNetworkButAuthenticated() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, TO)
            .awaitSent(awaitOneMinute);

        awaitOneMinute.until(() -> fakeSmtp.isReceived(response -> response
            .body("", hasSize(1))
            .body("[0].from", equalTo(FROM))
            .body("[0].subject", equalTo("test"))));
    }

    @Test
    public void localDeliveryShouldBePossibleFromNonAuthenticatedNonAuthorizedSender() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(TO, FROM)
            .awaitSent(awaitOneMinute);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitOneMinute);
    }

}
