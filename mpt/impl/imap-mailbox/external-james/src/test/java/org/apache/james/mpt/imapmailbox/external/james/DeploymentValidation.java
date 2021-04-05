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

package org.apache.james.mpt.imapmailbox.external.james;


import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class DeploymentValidation {

    public static final String DOMAIN = "domain";
    public static final String USER = "imapuser";
    public static final String USER_ADDRESS = USER + "@" + DOMAIN;
    public static final String PASSWORD = "password";
    private static final String INBOX = "INBOX";
    private static final String ONE_MAIL = "* 1 EXISTS";

    protected abstract ImapHostSystem createImapHostSystem();

    protected abstract SmtpHostSystem createSmtpHostSystem();

    protected abstract ExternalJamesConfiguration getConfiguration();

    private ImapHostSystem system;
    private SmtpHostSystem smtpSystem;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;
    private IMAPClient imapClient = new IMAPClient();

    protected static final Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    protected static final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    protected static final ConditionFactory awaitAtMostTenSeconds = calmlyAwait.atMost(TEN_SECONDS);

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        smtpSystem = createSmtpHostSystem();

        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
            .withUser(USER_ADDRESS, PASSWORD)
            .withLocale(Locale.US);
    }

    @Test
    public void validateDeployment() throws Exception {
        simpleScriptedTestProtocol.run("ValidateDeployment");
    }

    @Test
    public void selectThenFetchWithExistingMessages() throws Exception {
        simpleScriptedTestProtocol.run("SelectThenFetchWithExistingMessages");
    }

    @Test
    public void validateDeploymentWithMailsFromSmtp() throws Exception {
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender("another-domain");
        smtpSystem.connect(smtpMessageSender)
            .authenticate(USER_ADDRESS, PASSWORD)
            .sendMessage(USER_ADDRESS, USER_ADDRESS);
        imapClient.connect(getConfiguration().getAddress(), getConfiguration().getImapPort().getValue());
        imapClient.login(USER_ADDRESS, PASSWORD);
        awaitAtMostTenSeconds.until(this::checkMailDelivery);
    }

    private Boolean checkMailDelivery() throws IOException {
        imapClient.select(INBOX);
        String replyString = imapClient.getReplyString();
        return replyString.contains(ONE_MAIL);
    }
}
