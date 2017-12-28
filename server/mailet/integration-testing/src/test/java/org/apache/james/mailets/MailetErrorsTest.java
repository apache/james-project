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

import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.ErrorMailet;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.mailets.RuntimeErrorMailet;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.Mail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class MailetErrorsTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM = "from@" + JAMES_APACHE_ORG;
    private static final String RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;
    public static final String CUSTOM_PROCESSOR = "custom";
    public static final String CUSTOM_REPOSITORY = "file://var/mail/error/";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;
    private DataProbe dataProbe;

    @Before
    public void setup() throws Exception {
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await();
    }

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
            .build(temporaryFolder,
                MailetContainer.builder()
                    .threads(2)
                    .postmaster("postmaster@localhost")
                    .addProcessor(emptyTransport())
                    .addProcessor(errorProcessor())
                    .addProcessor(ProcessorConfiguration.builder()
                        .state(Mail.DEFAULT)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ErrorMailet.class)
                            .build())
                        .build())
                    .build());
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.TEN_SECONDS)
                .until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        }
    }

    @Test
    public void mailetProcessorsShouldHandleRuntimeException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .build(temporaryFolder,
                MailetContainer.builder()
                    .threads(2)
                    .postmaster("postmaster@localhost")
                    .addProcessor(emptyTransport())
                    .addProcessor(errorProcessor())
                    .addProcessor(ProcessorConfiguration.builder()
                        .state(Mail.DEFAULT)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(RuntimeErrorMailet.class)
                            .build())
                        .build())
                    .build());
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.TEN_SECONDS)
                .until(() -> probe.getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        }
    }

    @Test
    public void mailetProcessorsShouldHandleMessagingExceptionWhenSpecificErrorHandlingSpecified() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .build(temporaryFolder,
                MailetContainer.builder()
                    .threads(2)
                    .postmaster("postmaster@localhost")
                    .addProcessor(emptyTransport())
                    .addProcessor(errorProcessor())
                    .addProcessor(customProcessor())
                    .addProcessor(ProcessorConfiguration.builder()
                        .state(Mail.DEFAULT)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ErrorMailet.class)
                            .addProperty("onMailetException", CUSTOM_PROCESSOR)
                            .build())
                        .build())
                    .build());
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.TEN_SECONDS)
                .until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
        }
    }

    @Test
    public void mailetProcessorsShouldHandleRuntimeExceptionWhenSpecificErrorHandlingSpecified() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .build(temporaryFolder,
                MailetContainer.builder()
                    .threads(2)
                    .postmaster("postmaster@localhost")
                    .addProcessor(emptyTransport())
                    .addProcessor(errorProcessor())
                    .addProcessor(customProcessor())
                    .addProcessor(ProcessorConfiguration.builder()
                        .state(Mail.DEFAULT)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(RuntimeErrorMailet.class)
                            .addProperty("onMailetException", CUSTOM_PROCESSOR)
                            .build())
                        .build())
                    .build());
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.TEN_SECONDS)
                .until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
        }
    }

    @Test
    public void onExceptionIgnoreShouldContinueProcessingWhenRuntimeException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .build(temporaryFolder,
                MailetContainer.builder()
                    .threads(2)
                    .postmaster("postmaster@localhost")
                    .addProcessor(emptyTransport())
                    .addProcessor(errorProcessor())
                    .addProcessor(customProcessor())
                    .addProcessor(ProcessorConfiguration.builder()
                        .state(Mail.DEFAULT)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(RuntimeErrorMailet.class)
                            .addProperty("onMailetException", "ignore")
                            .build())
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", CUSTOM_REPOSITORY)
                            .build())
                        .build())
                    .build());
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.TEN_SECONDS)
                .until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
        }
    }

    @Test
    public void onExceptionIgnoreShouldContinueProcessingWhenMessagingException() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .build(temporaryFolder,
                MailetContainer.builder()
                    .threads(2)
                    .postmaster("postmaster@localhost")
                    .addProcessor(emptyTransport())
                    .addProcessor(errorProcessor())
                    .addProcessor(customProcessor())
                    .addProcessor(ProcessorConfiguration.builder()
                        .state(Mail.DEFAULT)
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ErrorMailet.class)
                            .addProperty("onMailetException", "ignore")
                            .build())
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ToRepository.class)
                            .addProperty("repositoryPath", CUSTOM_REPOSITORY)
                            .build())
                        .build())
                    .build());
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.TEN_SECONDS)
                .until(() -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1);
        }
    }

    private ProcessorConfiguration errorProcessor() {
        return ProcessorConfiguration.builder()
            .state(Mail.ERROR)
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", ERROR_REPOSITORY)
                .build())
            .build();
    }

    private ProcessorConfiguration customProcessor() {
        return ProcessorConfiguration.builder()
            .state("custom")
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", CUSTOM_REPOSITORY)
                .build())
            .build();
    }

    private ProcessorConfiguration emptyTransport() {
        return ProcessorConfiguration.builder()
            .state("transport")
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoveMimeHeader.class)
                .addProperty("name", "bcc")
                .build())
            .build();
    }
}
