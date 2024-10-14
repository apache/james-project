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

package org.apache.james.smtp;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.smtpserver.fastfail.MaxRcptHandler;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.apache.james.utils.SmtpSendingStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

class SmtpMaxRcptHandlerTest {
    private static final String USER = "user@" + DEFAULT_DOMAIN;
    private static final Integer DEFAULT_MAX_RCPT = 50;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    public void createJamesServer(@TempDir File temporaryFolder) throws Exception {
        SmtpConfiguration.Builder smtpConfiguration = SmtpConfiguration.builder()
                .doNotVerifyIdentity()
                .addHook(MaxRcptHandler.class.getName(),
                        Map.of("maxRcpt", DEFAULT_MAX_RCPT.toString()));

        jamesServer = TemporaryJamesServer.builder()
            .withSmtpConfiguration(smtpConfiguration)
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(USER, PASSWORD);
        IntStream.range(0, DEFAULT_MAX_RCPT + 1).forEach(Throwing.intConsumer((
                i -> dataProbe.addUser("recipient" + i + "@" + DEFAULT_DOMAIN, PASSWORD))));
    }

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void messageShouldNotBeAcceptedWhenMaxRcptHandlerExceeded() throws Exception {
        assertThatThrownBy(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(USER, PASSWORD)
                .sendMessageWithHeaders(USER, getRecipients(DEFAULT_MAX_RCPT + 1), "message"))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.RCPT, "452 4.5.3 Requested action not taken: max recipients reached\n"));
    }

    @Test
    void messageShouldBeAcceptedWhenMaxRcptHandlerWithinLimit() throws Exception {
        assertDoesNotThrow(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(USER, PASSWORD)
                .sendMessageWithHeaders(USER, getRecipients(DEFAULT_MAX_RCPT), "message"));
    }

    private List<String> getRecipients(Integer n) {
        return IntStream.range(0, n)
                .mapToObj(i -> "recipient" + i + "@" + DEFAULT_DOMAIN)
                .collect(ImmutableList.toImmutableList());
    }
}
