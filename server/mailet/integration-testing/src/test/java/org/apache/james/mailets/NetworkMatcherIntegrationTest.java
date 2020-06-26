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
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RemoteAddrInNetwork;
import org.apache.james.transport.matchers.RemoteAddrNotInNetwork;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NetworkMatcherIntegrationTest {
    private static final String FROM = "fromuser@" + DEFAULT_DOMAIN;
    private static final MailRepositoryUrl DROPPED_MAILS = MailRepositoryUrl.from("memory://var/mail/dropped-mails/");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    private TemporaryJamesServer createJamesServerWithRootProcessor(ProcessorConfiguration.Builder rootProcessor) throws Exception {
        TemporaryJamesServer temporaryJamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(rootProcessor)
                .putProcessor(CommonProcessors.deliverOnlyTransport()))
            .build(temporaryFolder.newFolder());

        DataProbe dataProbe = temporaryJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        return temporaryJamesServer;
    }

    private MailetConfiguration.Builder toRepository() {
        return MailetConfiguration.builder()
            .matcher(All.class)
            .mailet(ToRepository.class)
            .addProperty("repositoryPath", DROPPED_MAILS.asString());
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void mailsFromAuthorizedNetworksShouldBeDeliveredWithRemoteAddrInNetwork() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrInNetwork.class)
                .matcherCondition("127.0.0.0/8")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void mailsFromAuthorizedNetworksShouldBeDeliveredWithRemoteAddrNotInNetwork() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrNotInNetwork.class)
                .matcherCondition("172.0.0.0/8")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void remoteAddrInNetworkShouldSupportLargerMask() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrInNetwork.class)
                .matcherCondition("127.0.0.0/2")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void remoteAddrInNetworkShouldSupportRangesDefinedByAMiddleIp() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrInNetwork.class)
                .matcherCondition("127.0.4.108/8")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void remoteAddrInNetworkShouldSupportRangesDefinedByEndingIp() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrInNetwork.class)
                .matcherCondition("127.255.255.255/8")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void remoteAddrInNetworkShouldSupportRangesWithNonEightMultipleSubMasks() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrInNetwork.class)
                .matcherCondition("126.0.0.0/4")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void mailsFromNonAuthorizedNetworksShouldNotBeDeliveredWithRemoteAddrInNetwork() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrInNetwork.class)
                .matcherCondition("172.0.0.0/8")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        MailRepositoryProbeImpl repositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
        awaitAtMostOneMinute.until(() -> repositoryProbe.getRepositoryMailCount(DROPPED_MAILS) == 1);
        assertThat(
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(FROM, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .hasAMessage())
            .isFalse();
    }

    @Test
    public void mailsFromNonAuthorizedNetworksShouldNotBeDeliveredWithRemoteAddrNotInNetwork() throws Exception {
        jamesServer = createJamesServerWithRootProcessor(ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(RemoteAddrNotInNetwork.class)
                .matcherCondition("127.0.0.0/8")
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR))
            .addMailet(toRepository()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, FROM);

        MailRepositoryProbeImpl repositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
        awaitAtMostOneMinute.until(() -> repositoryProbe.getRepositoryMailCount(DROPPED_MAILS) == 1);
        assertThat(
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(FROM, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .hasAMessage())
            .isFalse();
    }

}
