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

package org.apache.james.transport.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ToSenderDomainRepositoryTest {

    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    private static final String CUSTOM_REPOSITORY_PREFIX = "memory://var/mail/custom/";
    public static final MailRepositoryUrl DOMAIN_URL = MailRepositoryUrl.from(CUSTOM_REPOSITORY_PREFIX + DEFAULT_DOMAIN);
    public static final MailRepositoryUrl AWAIT_REPOSITORY_PATH = MailRepositoryUrl.from("memory://var/mail/await/");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void incomingMailShouldBeStoredInCorrespondingMailRepository() throws Exception {
        startJamesServerWithMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX))));
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(DOMAIN_URL) == 1);

        assertThat(probe.getRepositoryMailCount(DOMAIN_URL))
            .isEqualTo(1);
    }

    @Test
    public void incomingMailShouldBeStoredWhenRepositoryDoesNotExistAndAllowedToCreateRepository() throws Exception {
        startJamesServerWithMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX)
                    .addProperty("allowRepositoryCreation", "true"))));
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(DOMAIN_URL) == 1);

        assertThat(probe.getRepositoryMailCount(DOMAIN_URL))
            .isEqualTo(1);
    }

    @Test
    public void incomingMailShouldBeStoredWhenRepositoryExistsAndAllowedToCreateRepository() throws Exception {
        startJamesServerWithMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX)
                    .addProperty("allowRepositoryCreation", "true"))));
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        probe.createRepository(DOMAIN_URL);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(DOMAIN_URL) == 1);

        assertThat(probe.getRepositoryMailCount(DOMAIN_URL))
            .isEqualTo(1);
    }

    @Test
    public void incomingMailShouldBeIgnoredWhenRepositoryDoesNotExistAndNotAllowedToCreateRepository() throws Exception {
        startJamesServerWithMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX)
                    .addProperty("allowRepositoryCreation", "false")
                    .addProperty("passThrough", "true"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToRepository.class)
                    .addProperty("repositoryPath", AWAIT_REPOSITORY_PATH.asString()))));
        MailRepositoryProbeImpl mailRepositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> mailRepositoryProbe.getRepositoryMailCount(AWAIT_REPOSITORY_PATH) == 1);

        assertThat(mailRepositoryProbe.listRepositoryUrls())
            .doesNotContain(DOMAIN_URL);
    }

    @Test
    public void incomingMailShouldBeStoredWhenRepositoryExistsAndNotAllowedToCreateRepository() throws Exception {
        startJamesServerWithMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX)
                    .addProperty("allowRepositoryCreation", "false"))));
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        probe.createRepository(DOMAIN_URL);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(DOMAIN_URL) == 1);

        assertThat(probe.getRepositoryMailCount(DOMAIN_URL))
            .isEqualTo(1);
    }

    @Test
    public void incomingMailsShouldBeStoredInCorrespondingMailRepository() throws Exception {
        startJamesServerWithMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX))));
        MailRepositoryProbeImpl probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT)
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(DOMAIN_URL) == 2);

        assertThat(probe.getRepositoryMailCount(DOMAIN_URL))
            .isEqualTo(2);
    }

    private void startJamesServerWithMailetContainer(MailetContainer.Builder mailetContainer) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder.newFolder());

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(RECIPIENT, PASSWORD);
    }
}
