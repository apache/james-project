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
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ToSenderDomainRepositoryTest {

    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    private static final String CUSTOM_REPOSITORY_PREFIX = "file://var/mail/custom/";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private MailRepositoryProbeImpl probe;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToSenderDomainRepository.class)
                    .addProperty("urlPrefix", CUSTOM_REPOSITORY_PREFIX)));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);

        probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void incomingMailShouldBeStoredInCorrespondingMailRepository() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY_PREFIX + DEFAULT_DOMAIN) == 1);
    }

    @Test
    public void incomingMailsShouldBeStoredInCorrespondingMailRepository() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(RECIPIENT, RECIPIENT)
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(
            () -> probe.getRepositoryMailCount(CUSTOM_REPOSITORY_PREFIX + DEFAULT_DOMAIN) == 2);
    }
}
