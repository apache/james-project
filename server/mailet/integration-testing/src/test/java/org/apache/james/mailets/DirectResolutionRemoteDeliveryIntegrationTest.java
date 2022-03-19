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
import static org.apache.james.mailets.configuration.MailetConfiguration.LOCAL_DELIVERY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.net.InetAddress;
import java.util.List;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.AddDeliveredToHeader;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

public class DirectResolutionRemoteDeliveryIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";
    private static final String JAMES_ANOTHER_MX_DOMAIN_1 = "mx1.james.com";
    private static final String JAMES_ANOTHER_MX_DOMAIN_2 = "mx2.james.com";
    private static final List<String> JAMES_ANOTHER_MX_DOMAINS = ImmutableList.of(JAMES_ANOTHER_MX_DOMAIN_1, JAMES_ANOTHER_MX_DOMAIN_2);

    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;

    private static final ImmutableList<InetAddress> ADDRESS_EMPTY_LIST = ImmutableList.of();
    private static final ImmutableList<String> RECORD_EMPTY_LIST = ImmutableList.of();

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public static FakeSmtp fakeSmtp = FakeSmtp.withDefaultPort();
    @RegisterExtension
    public static FakeSmtp fakeSmtpOnPort26 = FakeSmtp.withSmtpPort(26);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void directResolutionShouldBeWellPerformed(@TempDir File temporaryFolder) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, fakeSmtp.getContainer().getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .doNotVerifyIdentity()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute
            .untilAsserted(this::assertMessageReceivedByTheSmtpServer);
    }

    @Test
    void shouldRejectInvalidAddressesUponSubmission(@TempDir File temporaryFolder) throws Exception {
        /*
        a..b@domain.com triggered a parsing error within jakarta.mail, the exception was ignored and substituted with null,
        resulting in a NPE in RemoteDelivery. Instead we now reject it as part of SMTP reception emails we cannot handle.
        This could be used to make the delivery of all remote recipients fail while local recipient succeeds.
         */
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, fakeSmtp.getContainer().getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .doNotVerifyIdentity()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        assertThatThrownBy(() -> messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, "to..user@" + JAMES_ANOTHER_DOMAIN))
            .isInstanceOf(SMTPSendingException.class)
            .hasMessageContaining("Error upon step RCPT: 553 5.1.3 Syntax error in recipient address");
    }

    @Test
    void directResolutionShouldFailoverOnSecondMxWhenFirstMxFailed(@TempDir File temporaryFolder) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerRecord(JAMES_ANOTHER_DOMAIN, ADDRESS_EMPTY_LIST, JAMES_ANOTHER_MX_DOMAINS, RECORD_EMPTY_LIST)
            .registerMxRecord(JAMES_ANOTHER_MX_DOMAIN_1, fakeSmtpOnPort26.getContainer().getContainerIp())
            .registerMxRecord(JAMES_ANOTHER_MX_DOMAIN_2, fakeSmtp.getContainer().getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute
            .untilAsserted(this::assertMessageReceivedByTheSmtpServer);
    }

    @Test
    void directResolutionShouldBounceUponUnreachableMxRecords(@TempDir File temporaryFolder) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerRecord(JAMES_ANOTHER_DOMAIN, ADDRESS_EMPTY_LIST, ImmutableList.of("unknown"), RECORD_EMPTY_LIST);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(transport())
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void directResolutionShouldBounceWhenNoMxRecord(@TempDir File temporaryFolder) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerRecord(JAMES_ANOTHER_DOMAIN, ADDRESS_EMPTY_LIST, RECORD_EMPTY_LIST, RECORD_EMPTY_LIST);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(transport())
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Disabled("JAMES-2913 PerRecipientHeaders are not handled by RemoteDelivery")
    @Test
    void remoteDeliveryShouldAddPerRecipientHeaders(@TempDir File temporaryFolder) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, fakeSmtp.getContainer().getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(LOCAL_DELIVERY)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(AddDeliveredToHeader.class)
                        .build())
                    .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                        .matcher(All.class)))
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute
            .untilAsserted(this::assertMessageReceivedByTheSmtpServer);

        fakeSmtp.assertEmailReceived(response -> response
            .body("[0].headers.delivered-to", equalTo(FROM)));
    }

    private void assertMessageReceivedByTheSmtpServer() {
        fakeSmtp.assertEmailReceived(response -> response
            .body("", hasSize(1))
            .body("[0].from", equalTo(FROM))
            .body("[0].subject", equalTo("test")));
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                .matcher(All.class));
    }

    private ProcessorConfiguration.Builder transport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(LOCAL_DELIVERY)
            .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                .matcher(All.class));
    }
}
