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

import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.NoClassDefFoundErrorMailet;
import org.apache.james.transport.mailets.ErrorMailet;
import org.apache.james.transport.mailets.ErrorMatcher;
import org.apache.james.transport.mailets.NoClassDefFoundErrorMatcher;
import org.apache.james.transport.mailets.NoopMailet;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.OneRuntimeErrorMailet;
import org.apache.james.transport.mailets.OneThreadSuicideMailet;
import org.apache.james.transport.mailets.RuntimeErrorMailet;
import org.apache.james.transport.mailets.RuntimeExceptionMailet;
import org.apache.james.transport.mailets.RuntimeExceptionMatcher;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.HasException;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MailetErrorsTest {
    public static final String CUSTOM_PROCESSOR = "custom";
    public static final MailRepositoryUrl CUSTOM_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/custom/");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void mailetProcessorsShouldHandleMessagingException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ErrorMailet.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void mailetProcessingShouldHandleClassNotFoundException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(NoClassDefFoundErrorMailet.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void matcherProcessingShouldHandleClassNotFoundException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(NoClassDefFoundErrorMatcher.class)
                        .mailet(Null.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void mailetProcessorsShouldHandleRuntimeException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeExceptionMailet.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void spoolerShouldEventuallyProcessUponTemporaryError() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(OneRuntimeErrorMailet.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString()))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void spoolerShouldEventuallyProcessMailsAfterThreadSuicide() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void spoolerShouldNotInfinitLoopUponPermanentError() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RuntimeErrorMailet.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void mailetProcessorsShouldHandleMessagingExceptionWhenSpecificErrorHandlingSpecified() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);

    }

    @Test
    public void mailetProcessorsShouldHandleRuntimeExceptionWhenSpecificErrorHandlingSpecified() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void onExceptionIgnoreShouldContinueProcessingWhenRuntimeException() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void onExceptionIgnoreShouldContinueProcessingWhenMessagingException() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void matcherProcessorsShouldHandleMessagingException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(ErrorMatcher.class)
                        .mailet(NoopMailet.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void matcherProcessorsShouldHandleRuntimeException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(errorProcessor())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RuntimeExceptionMatcher.class)
                        .mailet(NoopMailet.class))))
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void matcherProcessorsShouldHandleMessagingExceptionWhenSpecificErrorHandlingSpecified() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void matcherProcessorsShouldHandleRuntimeExceptionWhenSpecificErrorHandlingSpecified() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void onMatcherExceptionIgnoreShouldNotMatchWhenRuntimeExceptionAndNoMatchConfigured() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void onMatcherExceptionIgnoreShouldNotMatchWhenMessagingExceptionAndNoMatchConfigured() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void onMatcherExceptionIgnoreShouldMatchWhenRuntimeExceptionAndAllMatchConfigured() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void onMatcherExceptionIgnoreShouldMatchWhenMessagingExceptionAndAllMatchConfigured() throws Exception {
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }

    @Test
    public void hasExceptionMatcherShouldMatchWhenMatcherThrowsExceptionSpecified() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("javax.mail.MessagingException")
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }
    
    @Test
    public void hasExceptionMatcherShouldNotMatchWhenMatcherThrowsExceptionNotSpecified() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("javax.mail.MessagingException")
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }
    
    @Test
    public void hasExceptionMatcherShouldMatchWhenMailetThrowsExceptionSpecified() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("javax.mail.MessagingException")
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
            .build(temporaryFolder.newFolder());
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        smtpMessageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort()).sendMessage(FROM, FROM);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
    }
    
    @Test
    public void hasExceptionMatcherShouldNotMatchWhenMailetThrowsExceptionNotSpecified() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(ProcessorConfiguration.error()
                    .addMailet(MailetConfiguration.builder()
                            .matcher(HasException.class)
                            .matcherCondition("javax.mail.MessagingException")
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
            .build(temporaryFolder.newFolder());
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
