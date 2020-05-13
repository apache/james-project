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
import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GatewayRemoteDeliveryIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;

    @ClassRule
    public static FakeSmtp fakeSmtp = FakeSmtp.withDefaultPort();
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;
    private InMemoryDNSService inMemoryDNSService;

    @Before
    public void setup() throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, fakeSmtp.getContainer().getContainerIp());
    }

    @After
    public void tearDown() {
        fakeSmtp.clean();
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenNoPort() throws Exception {
        String gatewayProperty = fakeSmtp.getContainer().getContainerIp();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute
            .untilAsserted(this::assertMessageReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenPort() throws Exception {
        String gatewayProperty = fakeSmtp.getContainer().getContainerIp() + ":25";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(this::assertMessageReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenSeveralIps() throws Exception {
        String gatewayProperty = fakeSmtp.getContainer().getContainerIp() + ",invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute
            .untilAsserted(this::assertMessageReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldFallbackToSecondGatewayWhenFirstInvalid() throws Exception {
        String gatewayProperty = "invalid.domain," + fakeSmtp.getContainer().getContainerIp();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute
            .untilAsserted(this::assertMessageReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldNotBeSentDirectlyToTheHostWhenGatewayFails() throws Exception {
        String gatewayProperty = "invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        // Wait for bounce being sent before checking no email is sent
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        fakeSmtp.assertEmailReceived(response -> response.body("", hasSize(0)));
    }

    @Test
    public void remoteDeliveryShouldBounceUponFailure() throws Exception {
        String gatewayProperty = "invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void remoteDeliveryShouldBounceUponFailureWhenNoBounceProcessor() throws Exception {
        String gatewayProperty = "invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY)
                    .addMailet(MailetConfiguration.remoteDeliveryBuilderNoBounces()
                        .matcher(All.class)
                        .addProperty("gateway", gatewayProperty))))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private void assertMessageReceivedByTheSmtpServer() {
        fakeSmtp.assertEmailReceived(response -> response
            .body("", hasSize(1))
            .body("[0].from", equalTo(FROM))
            .body("[0].subject", equalTo("test")));
    }

    private MailetContainer.Builder generateMailetContainerConfiguration(String gatewayProperty) {
        return TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(relayAndLocalDeliveryTransport(gatewayProperty));
    }

    private ProcessorConfiguration.Builder relayAndLocalDeliveryTransport(String gatewayProperty) {
        return ProcessorConfiguration.transport()
            .addMailetsFrom(CommonProcessors.deliverOnlyTransport())
            .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                .addProperty("gateway", gatewayProperty)
                .matcher(All.class));
    }
}
