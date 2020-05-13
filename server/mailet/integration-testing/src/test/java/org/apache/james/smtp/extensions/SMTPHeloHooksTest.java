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

package org.apache.james.smtp.extensions;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.smtp.extensions.hooks.DeclinedHeloHook;
import org.apache.james.smtp.extensions.hooks.DenyHeloHook;
import org.apache.james.smtp.extensions.hooks.DenySoftHeloHook;
import org.apache.james.smtp.extensions.hooks.OkHeloHook;
import org.apache.james.smtp.extensions.hooks.RecordingHeloHook;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SMTPHeloHooksTest {
    private static final String FROM = "fromuser@" + DEFAULT_DOMAIN;
    private static final String TO = "to@" + DEFAULT_DOMAIN;

    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public StaticInputChecker resultChecker = new StaticInputChecker();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private TemporaryJamesServer jamesServer;

    private void createJamesServer(SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(CommonProcessors.deliverOnlyTransport());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withSmtpConfiguration(smtpConfiguration)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder.newFolder());

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(TO, PASSWORD);
    }

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void heloHookShouldBeCalledWithTheRightArgument() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(RecordingHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, TO);

        awaitAtMostOneMinute.until(() -> resultChecker.getResults().size() > 0);
        assertThat(resultChecker.getResults())
            .containsExactly(Pair.of(RecordingHeloHook.class, DEFAULT_DOMAIN));
    }

    @Test
    public void mailShouldBeWellDeliveredUponDeclinedHeloHook() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(DeclinedHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, TO);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(TO, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void mailShouldBeWellDeliveredUponOKHeloHook() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(OkHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, TO);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(TO, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void mailShouldBeWellDeliveredUponOKHeloHookFollowedByADenyHook() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(OkHeloHook.class.getCanonicalName())
            .addHook(DenyHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, TO);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(TO, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void denyHeloHookShouldBeAppliedAfterADeclinedHeloHook() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(DeclinedHeloHook.class.getCanonicalName())
            .addHook(DenyHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        assertThatThrownBy(() -> messageSender.sendMessage(FROM, TO))
            .isInstanceOf(SMTPSendingException.class)
            .hasMessageContaining("Error upon step Helo: 554 Email rejected");
    }

    @Test
    public void smtpSessionShouldBeAbortedUponDenyHeloHook() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(DenyHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        assertThatThrownBy(() -> messageSender.sendMessage(FROM, TO))
            .isInstanceOf(SMTPSendingException.class)
            .hasMessageContaining("Error upon step Helo: 554 Email rejected");
    }

    @Test
    public void smtpSessionShouldBeAbortedUponDenySoftHeloHook() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .addHook(DenySoftHeloHook.class.getCanonicalName()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        assertThatThrownBy(() -> messageSender.sendMessage(FROM, TO))
            .isInstanceOf(SMTPSendingException.class)
            .hasMessageContaining("Error upon step Helo: 451 Temporary problem. Please try again later");
    }
}
