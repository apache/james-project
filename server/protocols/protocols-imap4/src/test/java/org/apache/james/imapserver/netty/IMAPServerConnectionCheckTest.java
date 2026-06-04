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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:membername")
class IMAPServerConnectionCheckTest extends AbstractIMAPServerTest {

    IMAPServer imapServer;
    private int port;

    @BeforeEach
    void beforeEach() throws Exception {
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerImapConnectCheck.xml"));
        imapServer = createImapServer(config);
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    @Test
    void banIpWhenBannedIpConnect() {
        imapServer.getConnectionChecks().stream()
            .filter(check -> check instanceof IpConnectionCheck)
            .map(check -> (IpConnectionCheck) check)
            .forEach(ipCheck -> ipCheck.setBannedIps(Set.of("127.0.0.1")));

        assertThatThrownBy(() -> testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS)
            .append("INBOX", SMALL_MESSAGE));
    }

    @Test
    void logoutShouldDisconnectUser() throws Exception {
        testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS);

        imapServer.disconnect(USER::equals);

        assertThatThrownBy(() -> testIMAPClient
            .append("INBOX", SMALL_MESSAGE));
    }

    @Test
    void allowConnectWithUnbannedIp() throws IOException {
        imapServer.getConnectionChecks().stream()
            .filter(check -> check instanceof IpConnectionCheck)
            .map(check -> (IpConnectionCheck) check)
            .forEach(ipCheck -> ipCheck.setBannedIps(Set.of("127.0.0.2")));

        testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS)
            .append("INBOX", SMALL_MESSAGE);

        assertThat(testIMAPClient
            .select("INBOX")
            .readFirstMessage())
            .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[] {21}\r\nheader: value\r\n\r\nBODY)\r\n");
    }
}
