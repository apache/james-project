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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class SmtpAuthIntegrationTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    private static final int IMAP_PORT = 1143;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    private static final String FROM = "fromuser@" + JAMES_APACHE_ORG;
    private static final String DROPPED_MAILS = "file://var/mail/dropped-mails/";


    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;

    @Before
    public void setup() throws Exception {
        ProcessorConfiguration rootProcessor = ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.builder()
                .matcher(SMTPAuthSuccessful.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.STATE_TRANSPORT))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.STATE_BOUNCES))
            .build();

        MailetContainer mailetContainer = MailetContainer.builder()
            .addProcessor(rootProcessor)
            .addProcessor(CommonProcessors.error())
            .addProcessor(deliverOnlyTransport())
            .addProcessor(bounces())
            .addProcessor(CommonProcessors.sieveManagerCheck())
            .build();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .build(temporaryFolder, mailetContainer);
        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await();
    }

    private ProcessorConfiguration deliverOnlyTransport() {
        return ProcessorConfiguration.transport()
            .enableJmx(true)
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(LocalDelivery.class))
            .build();
    }

    private ProcessorConfiguration bounces() {
        return ProcessorConfiguration.bounces()
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", DROPPED_MAILS))
            .build();
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void authenticatedSmtpSessionsShouldBeDelivered() throws Exception {
        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.authentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG, FROM, PASSWORD);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {

            messageSender.sendMessage(FROM, FROM);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);

            calmlyAwait.atMost(Duration.ONE_MINUTE)
                .until(() -> imapMessageReader.userReceivedMessage(FROM, PASSWORD));
        }
    }

    @Test
    public void nonAuthenticatedSmtpSessionsShouldNotBeDelivered() throws Exception {
        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {

            messageSender.sendMessage(FROM, FROM);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);

            MailRepositoryProbeImpl repositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> repositoryProbe.getRepositoryMailCount(DROPPED_MAILS) == 1);
            assertThat(imapMessageReader.userReceivedMessage(FROM, PASSWORD)).isFalse();
        }
    }

}
