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

package org.apache.james.mailets.flow;

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.core.MailAddress;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.NoopMailet;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.PostmasterAlias;
import org.apache.james.transport.mailets.SetMailAttribute;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.transport.matchers.RelayLimit;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

public class ExecutionFlowTest {
    @RegisterExtension
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    public void test() {
        CountingExecutionMailet.reset();
        CountingExecutionMailetBis.reset();
        CountingExecutionTerminatingMailet.reset();
        CollectingExecutionMailet.reset();
        CollectingExecutionMailetBis.reset();
        CollectMailAttributeMailet.reset();
        FirstRecipientCountingExecutions.reset();
    }

    @AfterEach
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void partialMatchShouldLeadToSingleExecutionOfMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.bounces())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
    }

    @Test
    public void partialMatchShouldLeadToSingleExecutionOfMatcher(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(FirstRecipientCountingExecutions.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));


        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(FirstRecipientCountingExecutions.executionCount()).isEqualTo(1);
    }

    @Test
    public void partialMatchShouldLeadToSingleExecutionOfUpstreamMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
    }

    @Test
    public void partialMatchShouldLeadToSingleExecutionOfUpstreamRootMailets(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(ProcessorConfiguration.root()
                    .enableJmx(false)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(PostmasterAlias.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RelayLimit.class)
                        .matcherCondition("30")
                        .mailet(Null.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.TO_TRANSPORT)))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
    }

    @Test
    public void mutationsOfDownstreamMailetsShouldNotAffectUpStreamMailets(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectMailAttributeMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SetMailAttribute.class)
                        .addProperty(CollectMailAttributeMailet.MY_ATTRIBUTE, "value1")
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CollectMailAttributeMailet.encounteredAttributes()).isEmpty();
    }

    @Test
    public void mutationsOfDownstreamMailetsShouldNotAffectUpStreamMailetsUponSplit(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectMailAttributeMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SetMailAttribute.class)
                        .addProperty(CollectMailAttributeMailet.MY_ATTRIBUTE, "value1")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CollectMailAttributeMailet.encounteredAttributes()).isEmpty();
    }

    @Test
    public void totalMatchShouldNotSplitMail(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(2)
            .containsOnly(new MailAddress(FROM), new MailAddress(RECIPIENT));
    }

    @Test
    public void noMatchShouldNotExecuteMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(None.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(0);
    }

    @Test
    public void noMatchWithNullShouldNotExecuteMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(NoneWithNull.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(0);
    }

    @Test
    public void noMatchShouldNotSplitMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(None.class)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(2)
            .containsOnly(new MailAddress(FROM), new MailAddress(RECIPIENT));
    }

    @Test
    public void noMatchWithNullShouldNotSplitMailet(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(NoneWithNull.class)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(2)
            .containsOnly(new MailAddress(FROM), new MailAddress(RECIPIENT));
    }

    @Test
    public void nullMailetShouldAbortProcessing(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Null.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(0);
    }

    @Test
    public void nullMailetShouldAbortProcessingOnlOfMatchedEmails(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(Null.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(1)
            .containsOnly(new MailAddress(FROM));
    }

    @Test
    public void clearRecipientsMailetShouldAbortProcessing(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ClearRecipientsMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(0);
    }

    @Test
    public void clearRecipientsMailetShouldAbortProcessingOnlOfMatchedEmails(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(ClearRecipientsMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(1);
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(1)
            .containsOnly(new MailAddress(FROM));
    }

    @Test
    public void mailetCanEditRecipients(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(AddRecipient.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(3)
            .containsOnly(new MailAddress(FROM), new MailAddress(RECIPIENT), new MailAddress(RECIPIENT2));
    }

    @Test
    public void toProcessorShouldSwitchExecutingProcessor(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "custom")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(ProcessorConfiguration.builder()
                    .state("custom")
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailetBis.class)
                        .build()))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionMailet.executionCount()).isEqualTo(0);
        assertThat(CountingExecutionMailetBis.executionCount()).isEqualTo(1);
    }

    @Test
    public void toProcessorShouldSupportPartialMatches(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "custom")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(ProcessorConfiguration.builder()
                    .state("custom")
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailetBis.class)
                        .build()))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(1)
            .containsOnly(new MailAddress(FROM));
        assertThat(CollectingExecutionMailetBis.executionFor())
            .hasSize(1)
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    public void toProcessorSplitShouldNotDisposeContent(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "custom")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(EnsureNotDisposed.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(ProcessorConfiguration.builder()
                    .state("custom")
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(EnsureNotDisposed.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailetBis.class)
                        .build()))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(1)
            .containsOnly(new MailAddress(FROM));
        assertThat(CollectingExecutionMailetBis.executionFor())
            .hasSize(1)
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    public void splitShouldNotDisposeContent(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(EnsureNotDisposed.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionMailet.executionCount())
            .isEqualTo(2);
    }

    @Test
    public void partialMatchShouldLeadToExecutionOfDownStreamMailetsForEachSplitedMails(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CountingExecutionTerminatingMailet.class)
                        .build()))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CountingExecutionTerminatingMailet.executionCount()).isEqualTo(2);
    }

    @Test
    public void emailModificationsShouldBePreservedOnPartialMatch(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(SetMailAttribute.class)
                        .addProperty(CollectMailAttributeMailet.MY_ATTRIBUTE, "value1")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(SetMailAttribute.class)
                        .addProperty(CollectMailAttributeMailet.MY_ATTRIBUTE, "value2")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectMailAttributeMailet.class)
                        .build()))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(100); // queue delays might cause the processing not to start straight at the end of the SMTP session
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(
            jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .isTrue());
        assertThat(CollectMailAttributeMailet.encounteredAttributes())
            .hasSize(2)
            .containsOnly("value1", "value2");
    }

    @Test
    public void matcherSplitShouldNotDuplicateRecipients(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(NoopMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CollectingExecutionMailet.class)
                        .build())
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.root()))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(CollectingExecutionMailet.executionFor())
            .hasSize(2)
            .containsOnly(new MailAddress(FROM), new MailAddress(RECIPIENT));
    }
}
