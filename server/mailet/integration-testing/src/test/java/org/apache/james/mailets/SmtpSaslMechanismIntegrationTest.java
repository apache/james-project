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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Base64;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmtpSaslMechanismIntegrationTest {
    private static final String USER = "fromuser@" + DEFAULT_DOMAIN;

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }

    private static String plainInitialResponse(String username, String password) {
        return base64(String.join("\0", "", username, password));
    }

    private TemporaryJamesServer jamesServer;

    private MailetContainer.Builder minimalMailetContainer() {
        return MailetContainer.builder()
            .putProcessor(CommonProcessors.simpleRoot())
            .putProcessor(CommonProcessors.error())
            .putProcessor(CommonProcessors.rrtError())
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.TO_BOUNCE))
            .putProcessor(CommonProcessors.bounces());
    }

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void explicitlyConfiguredLoginSaslMechanismShouldAuthenticate(@TempDir File temporaryFolder) throws Exception {
        startJamesServer(temporaryFolder, "LoginSaslMechanismFactory");

        SMTPClient smtpClient = new SMTPClient();
        try {
            smtpClient.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());

            smtpClient.sendCommand("EHLO", "localhost");
            assertThat(smtpClient.getReplyCode()).isEqualTo(250);

            smtpClient.sendCommand("AUTH LOGIN");
            assertThat(smtpClient.getReplyCode()).isEqualTo(334);

            smtpClient.sendCommand(base64(USER));
            assertThat(smtpClient.getReplyCode()).isEqualTo(334);

            smtpClient.sendCommand(base64(PASSWORD));
            assertThat(smtpClient.getReplyCode()).isEqualTo(235);
        } finally {
            if (smtpClient.isConnected()) {
                smtpClient.disconnect();
            }
        }
    }

    @Test
    void explicitlyConfiguredPlainSaslMechanismShouldAuthenticateOverClearTextWhenRequireSSLIsTrue(@TempDir File temporaryFolder) throws Exception {
        // SMTP keeps accepting AUTH PLAIN on clear-text connections even when auth.requireSSL controls capability announcement.
        startJamesServer(temporaryFolder, "PlainSaslMechanismFactory");

        SMTPClient smtpClient = new SMTPClient();
        try {
            smtpClient.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());

            smtpClient.sendCommand("EHLO", "localhost");
            assertThat(smtpClient.getReplyCode()).isEqualTo(250);

            smtpClient.sendCommand("AUTH", "PLAIN " + plainInitialResponse(USER, PASSWORD));
            assertThat(smtpClient.getReplyCode()).isEqualTo(235);
        } finally {
            if (smtpClient.isConnected()) {
                smtpClient.disconnect();
            }
        }
    }

    private void startJamesServer(File temporaryFolder, String saslMechanisms) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_ONLY_MODULE)
            .withMailetContainer(minimalMailetContainer())
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .doNotVerifyIdentity()
                .requireSSLForAuth(true)
                .withSaslMechanisms(saslMechanisms)
                .build())
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(USER, PASSWORD);
    }

}
