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

import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.MailetConfiguration.TO_TRANSPORT;
import static org.apache.james.mailets.configuration.ProcessorConfiguration.STATE_BOUNCES;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.core.Username;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FilteringManagementProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import com.google.inject.multibindings.Multibinder;

public class ForwardBounceLoopIntegrationTest {

    private static final Username SENDER = Username.of("sender@" + DEFAULT_DOMAIN);
    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);
    private TemporaryJamesServer jamesServer;
    private MailRepositoryProbeImpl mailRepositoryProbe;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    private DataProbeImpl dataProbe;

    private void setUp(File temporaryFolder, MailetContainer.Builder mailetConfiguration) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withOverrides(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(FilteringManagementProbeImpl.class))
            .withMailetContainer(mailetConfiguration)
            .build(temporaryFolder);

        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);

        dataProbe.addUser(SENDER.asString(), PASSWORD);
        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), PASSWORD);

        mailRepositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void dsnBounceWithForwardShouldNotLeadToALoop(@TempDir File temporaryFolder) throws Exception {
        setUp(temporaryFolder, MailetContainer.builder()
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition(CEDRIC.asString())
                    .mailet(ToProcessor.class)
                    .addProperty("processor", STATE_BOUNCES))
                .addMailet(TO_TRANSPORT))
            .putProcessor(CommonProcessors.error())
            .putProcessor(CommonProcessors.rrtError())
            .putProcessor(CommonProcessors.transport())
            .putProcessor(CommonProcessors.bounces()));

        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(BOB.asString()));
        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(CEDRIC.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), BOB.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());

        assertThat(mailRepositoryProbe.getRepositoryMailCount(ERROR_REPOSITORY))
            .isZero();
    }

    @Test
    void bounceWithForwardShouldNotLeadToALoop(@TempDir File temporaryFolder) throws Exception {
        setUp(temporaryFolder, MailetContainer.builder()
            .putProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition(CEDRIC.asString())
                    .mailet(ToProcessor.class)
                    .addProperty("processor", STATE_BOUNCES))
                .addMailet(TO_TRANSPORT))
            .putProcessor(CommonProcessors.error())
            .putProcessor(CommonProcessors.rrtError())
            .putProcessor(CommonProcessors.transport())
            .putProcessor(ProcessorConfiguration.bounces()
                .enableJmx(false)
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(Bounce.class)
                    .addProperty("passThrough", "false"))
                .build()));

        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(BOB.asString()));
        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(CEDRIC.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), BOB.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());

        assertThat(mailRepositoryProbe.getRepositoryMailCount(ERROR_REPOSITORY))
            .isZero();
    }
}
