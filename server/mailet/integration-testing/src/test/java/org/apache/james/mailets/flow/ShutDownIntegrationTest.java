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

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ShutDownIntegrationTest {
    private static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .postmaster(POSTMASTER)
                .putProcessor(transport()))
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
    }

    @Test
    void shouldAllowADelayToProcessPendingMails() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(RECIPIENT, PASSWORD)
            .sendMessage(RECIPIENT, RECIPIENT);

        AtomicBoolean isOff = new AtomicBoolean(false);
        Mono.fromRunnable(() -> {
            jamesServer.shutdown();
            isOff.set(true);
        })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

        awaitAtMostOneMinute.until(() -> PauseThenCountingExecutionMailet.executionCount() == 1);
        awaitAtMostOneMinute.until(isOff::get);
    }

    private ProcessorConfiguration.Builder transport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(PauseThenCountingExecutionMailet.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Null.class))
            .addMailetsFrom(CommonProcessors.transport());
    }
}
