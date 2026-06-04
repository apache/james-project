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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerAppendNoLimitTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerNoLimits.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    @Test
    void smallAppendsShouldWork() throws Exception {
        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE))
            .doesNotThrowAnyException();

        assertThat(testIMAPClient.select("INBOX")
            .readFirstMessage())
            .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
    }

    @Test
    void capabilityAdvertizeAppendLimit() throws Exception {
        assertThat(
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .capability())
            .contains("APPENDLIMIT")
            .doesNotContain("APPENDLIMIT=");
    }

    @Test
    void statusAdvertizeAppendLimit() throws Exception {
        assertThat(
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .sendCommand("STATUS \"INBOX\" (APPENDLIMIT)"))
            .contains("* STATUS \"INBOX\" (APPENDLIMIT NIL)");
    }

    @Test
    void mediumAppendsShouldWork() throws Exception {
        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", _65K_MESSAGE))
            .doesNotThrowAnyException();

        assertThat(testIMAPClient.select("INBOX")
            .readFirstMessage())
            .contains("\r\n" + _65K_MESSAGE + ")\r\n");
    }

    @Test
    void loginFixationShouldBeRejected() throws Exception {
        InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();
        mailboxManager.createMailbox(
            MailboxPath.forUser(USER, "pwnd"),
            mailboxManager.createSystemSession(USER));
        mailboxManager.createMailbox(
            MailboxPath.forUser(USER2, "notvuln"),
            mailboxManager.createSystemSession(USER2));

        testIMAPClient.connect("127.0.0.1", port)
            // Injected by a man in the middle attacker
            .rawLogin(USER.asString(), USER_PASS);

        assertThatThrownBy(() -> testIMAPClient.rawLogin(USER2.asString(), USER_PASS))
            .isInstanceOf(IOException.class)
            .hasMessage("Login failed");
    }

    @RepeatedTest(200)
    void largeAppendsShouldWork() throws Exception {
        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", _129K_MESSAGE))
            .doesNotThrowAnyException();

        assertThat(testIMAPClient.select("INBOX")
            .readFirstMessage())
            .contains("\r\n" + _129K_MESSAGE + ")\r\n");
    }
}
