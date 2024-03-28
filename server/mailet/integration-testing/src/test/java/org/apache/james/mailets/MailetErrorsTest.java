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

import static org.apache.james.MailsShouldBeWellReceived.CALMLY_AWAIT;
import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.CommonProcessors.rrtError;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.ErrorMailet;
import org.apache.james.transport.mailets.ErrorMatcher;
import org.apache.james.transport.mailets.NoClassDefFoundErrorMailet;
import org.apache.james.transport.mailets.NoClassDefFoundErrorMatcher;
import org.apache.james.transport.mailets.NoopMailet;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.OneRuntimeErrorMailet;
import org.apache.james.transport.mailets.OneRuntimeExceptionMailet;
import org.apache.james.transport.mailets.OneRuntimeExceptionMatcher;
import org.apache.james.transport.mailets.OneThreadSuicideMailet;
import org.apache.james.transport.mailets.RuntimeErrorMailet;
import org.apache.james.transport.mailets.RuntimeExceptionMailet;
import org.apache.james.transport.mailets.RuntimeExceptionMatcher;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.HasException;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

class MailetErrorsTest {
    public static final String CUSTOM_PROCESSOR = "custom";
    public static final MailRepositoryUrl CUSTOM_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/custom/");

    @RegisterExtension
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private TemporaryJamesServer jamesServer;

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void mailetProcessorsShouldHandleMessagingException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ErrorMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    void mailetProcessingShouldHandleClassNotFoundException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(NoClassDefFoundErrorMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    void mailSpoolerShouldWellHandleNoSuchMethodErrorWhenPropagateOnMailetException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())))
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(NoSuchMethodErrorMailet.class)
                        .addProperty("onMailetException", "propagate"))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    void noSuchMethodErrorShouldTriggerErrorProcessorWhenDefaultOnMailetException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())))
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(NoSuchMethodErrorMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void propagateShouldAllowReprocessing(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.transport())
                .putProcessor(errorProcessor())
                .putProcessor(rrtError())
                .putProcessor(CommonProcessors.bounces())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(OneRuntimeExceptionMailet.class)
                        .addProperty("onMailetException", "propagate"))
                    .addMailet(MailetConfiguration.TO_TRANSPORT)))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void retryExceptionShouldSucceedUponSplittedMail(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(OneRuntimeExceptionMailet.class)
                        .addProperty("onMailetException", "propagate"))
                    .addMailetsFrom(CommonProcessors.transport()))
                .putProcessor(errorProcessor())
                .putProcessor(rrtError())
                .putProcessor(CommonProcessors.bounces())
                .putProcessor(CommonProcessors.root()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(1);

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void retryExceptionShouldSucceedUponSplittedMailForMatcher(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(OneRuntimeExceptionMatcher.class)
                        .mailet(NoopMailet.class)
                        .addProperty("onMatchException", "propagate"))
                    .addMailetsFrom(CommonProcessors.transport()))
                .putProcessor(errorProcessor())
                .putProcessor(rrtError())
                .putProcessor(CommonProcessors.bounces())
                .putProcessor(CommonProcessors.root()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(1);
    }

    @Test
    void matcherProcessingShouldHandleClassNotFoundException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(NoClassDefFoundErrorMatcher.class)
                        .mailet(Null.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Disabled("JAMES-3589 Test crashes as James propagates errors which seems like a sane behaviour")
    @Test
    void retryShouldSucceedUponSplittedMail(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition(RECIPIENT)
                        .mailet(OneRuntimeErrorMailet.class)
                        .addProperty("onMailetException", "propagate"))
                    .addMailetsFrom(CommonProcessors.transport()))
                .putProcessor(errorProcessor())
                .putProcessor(rrtError())
                .putProcessor(CommonProcessors.bounces())
                .putProcessor(CommonProcessors.root()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ImmutableList.of(FROM, RECIPIENT));

        Thread.sleep(5000);

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(1);

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mailetProcessorsShouldHandleRuntimeException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeExceptionMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Disabled("JAMES-3589 Test crashes as James propagates errors which seems like a sane behaviour")
    @Test
    void spoolerShouldEventuallyProcessUponTemporaryError(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(rrtError())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(OneRuntimeErrorMailet.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void spoolerShouldEventuallyProcessMailsAfterThreadSuicide(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(OneThreadSuicideMailet.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Disabled("JAMES-3589 Test crashes as James propagates errors which seems like a sane behaviour")
    @Test
    void spoolerShouldNotInfinitLoopUponPermanentError(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(rrtError())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeErrorMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    void mailetProcessorsShouldHandleMessagingExceptionWhenSpecificErrorHandlingSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ErrorMailet.class)
                        .addProperty("onMailetException", CUSTOM_PROCESSOR))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);

    }

    @Test
    void mailetProcessorsShouldHandleRuntimeExceptionWhenSpecificErrorHandlingSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeExceptionMailet.class)
                        .addProperty("onMailetException", CUSTOM_PROCESSOR))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onExceptionIgnoreShouldContinueProcessingWhenRuntimeException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeExceptionMailet.class)
                        .addProperty("onMailetException", "ignore"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onExceptionIgnoreShouldContinueProcessingWhenNoSuchMethodError(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(NoSuchMethodErrorMailet.class)
                        .addProperty("onMailetException", "ignore"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        CALMLY_AWAIT.until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class)
            .getRepositoryMailCount(ERROR_REPOSITORY) == 0);
        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class)
            .getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onExceptionIgnoreShouldContinueProcessingWhenMessagingException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ErrorMailet.class)
                        .addProperty("onMailetException", "ignore"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void matcherProcessorsShouldHandleMessagingException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(ErrorMatcher.class)
                        .mailet(NoopMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    void matcherProcessorsShouldHandleRuntimeException(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RuntimeExceptionMatcher.class)
                        .mailet(NoopMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    void matcherProcessorsShouldHandleMessagingExceptionWhenSpecificErrorHandlingSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(ErrorMatcher.class)
                        .mailet(NoopMailet.class)
                        .addProperty("onMatchException", CUSTOM_PROCESSOR))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void matcherProcessorsShouldHandleRuntimeExceptionWhenSpecificErrorHandlingSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RuntimeExceptionMatcher.class)
                        .mailet(NoopMailet.class)
                        .addProperty("onMatchException", CUSTOM_PROCESSOR))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onMatcherExceptionIgnoreShouldNotMatchWhenRuntimeExceptionAndNoMatchConfigured(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RuntimeExceptionMatcher.class)
                        .mailet(Null.class)
                        .addProperty("onMatchException", "nomatch"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onMatcherExceptionIgnoreShouldNotMatchWhenMessagingExceptionAndNoMatchConfigured(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(ErrorMatcher.class)
                        .mailet(Null.class)
                        .addProperty("onMatchException", "nomatch"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onMatcherExceptionIgnoreShouldMatchWhenRuntimeExceptionAndAllMatchConfigured(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RuntimeExceptionMatcher.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())
                        .addProperty("onMatchException", "matchall"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Null.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void onMatcherExceptionIgnoreShouldMatchWhenMessagingExceptionAndAllMatchConfigured(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(ErrorMatcher.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())
                        .addProperty("onMatchException", "matchall"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Null.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    void hasExceptionMatcherShouldMatchWhenMatcherThrowsExceptionSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("jakarta.mail.MessagingException")
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))
                    .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", ERROR_REPOSITORY.asString())))
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(ErrorMatcher.class)
                        .mailet(Null.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }
    
    @Test
    void hasExceptionMatcherShouldNotMatchWhenMatcherThrowsExceptionNotSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("jakarta.mail.MessagingException")
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))
                    .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", ERROR_REPOSITORY.asString())))
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RuntimeExceptionMatcher.class)
                        .mailet(Null.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }
    
    @Test
    void hasExceptionMatcherShouldMatchWhenMailetThrowsExceptionSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("jakarta.mail.MessagingException")
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))
                    .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", ERROR_REPOSITORY.asString())))
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ErrorMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }
    
    @Test
    void hasExceptionMatcherShouldNotMatchWhenMailetThrowsExceptionNotSpecified(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("jakarta.mail.MessagingException")
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))
                    .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", ERROR_REPOSITORY.asString())))
                .putProcessor(customProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeExceptionMailet.class))))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }
    
    private ProcessorConfiguration.Builder errorProcessor() {
        return ProcessorConfiguration.error()
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", ERROR_REPOSITORY.asString()));
    }

    private ProcessorConfiguration.Builder customProcessor() {
        return ProcessorConfiguration.builder()
            .state("custom")
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()));
    }
}
