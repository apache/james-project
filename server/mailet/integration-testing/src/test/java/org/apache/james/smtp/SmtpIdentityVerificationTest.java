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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.apache.james.utils.SmtpSendingStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SmtpIdentityVerificationTest {
    private static final String ATTACKER_PASSWORD = "secret";

    private static final String ATTACKER = "attacker@" + DEFAULT_DOMAIN;
    private static final String USER = "user@" + DEFAULT_DOMAIN;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    private void createJamesServer(File temporaryFolder, SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withSmtpConfiguration(smtpConfiguration)
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(USER, PASSWORD);
        dataProbe.addUser(ATTACKER, ATTACKER_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void remoteUserCanSendEmailsToLocalUsers(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .verifyIdentity());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("other@domain.tld", USER);
    }

    @Test
    void remoteUserCanSendEmailsToLocalUsersWhenAuthNotRequired(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .verifyIdentity());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("other@domain.tld", USER);
    }

    @Test
    void spoofingForbiddenWhenNoAuthRequired(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .verifyIdentity());

        assertThatThrownBy(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .sendMessage(USER, USER))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.Sender, "530 5.7.1 Authentication Required\n"));
    }

    @Test
    void smtpShouldAcceptMessageWhenIdentityIsMatching(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .verifyIdentity());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(USER, PASSWORD).sendMessage(USER, USER);
    }

    @Test
    void verifyIdentityShouldRejectNullSenderWHenAuthenticated(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .verifyIdentity());

        assertThatThrownBy(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(USER, PASSWORD)
                .sendMessageNoSender(USER))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.Sender, "503 5.7.1 Incorrect Authentication for Specified Email Address\n"));
    }

    @Test
    void verifyIdentityShouldAcceptNullSenderWhenAuthenticationRequired(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .verifyIdentity());

        assertThatCode(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .sendMessageNoSender(USER))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectUnauthenticatedSendersUsingLocalDomains(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .verifyIdentity());

        assertThatThrownBy(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .sendMessage(USER, USER))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.Sender, "530 5.7.1 Authentication Required\n"));
    }

    @Test
    void smtpShouldAcceptMessageWhenIdentityIsNotMatchingButNotChecked(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .doNotVerifyIdentity());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ATTACKER, ATTACKER_PASSWORD)
            .sendMessage(USER, USER);
    }

    @Test
    void smtpShouldRejectMessageWhenIdentityIsNotMatching(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder, SmtpConfiguration.builder()
            .requireAuthentication()
            .verifyIdentity());

        assertThatThrownBy(() ->
            messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(ATTACKER, ATTACKER_PASSWORD)
                .sendMessage(USER, USER))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.Sender, "503 5.7.1 Incorrect Authentication for Specified Email Address\n"));
    }
}
