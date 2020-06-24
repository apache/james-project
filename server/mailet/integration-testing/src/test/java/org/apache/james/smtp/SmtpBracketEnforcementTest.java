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

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.apache.james.utils.SmtpSendingStep;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SmtpBracketEnforcementTest {
    private static final String USER = "user@" + DEFAULT_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    private void createJamesServer(SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withSmtpConfiguration(smtpConfiguration)
            .build(temporaryFolder.newFolder());
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(USER, PASSWORD);
    }

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void recipientWithBracketsShouldBeAcceptedWhenNoBracketRequired() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .doNotRequireBracketEnforcement());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(USER, PASSWORD)
            .sendMessage(USER, USER);
    }

    @Test
    public void recipientWithNoBracketsShouldBeAcceptedWhenNoBracketRequired() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .doNotRequireBracketEnforcement());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(USER, PASSWORD)
            .sendMessageNoBracket(USER, USER);
    }

    @Test
    public void recipientWithBracketsShouldBeAcceptedWhenBracketRequired() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireBracketEnforcement());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(USER, PASSWORD)
            .sendMessage(USER, USER);
    }

    @Test
    public void recipientWithNoBracketsShouldBeRejectedWhenBracketRequired() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireBracketEnforcement());

        assertThatThrownBy(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(USER, PASSWORD)
                .sendMessageNoBracket(USER, USER))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.RCPT, "501 5.5.2 Syntax error in parameters or arguments\n"));
    }
}
