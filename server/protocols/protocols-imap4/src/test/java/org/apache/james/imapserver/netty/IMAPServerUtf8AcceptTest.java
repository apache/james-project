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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IMAPServerUtf8AcceptTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void capabilityShouldAdvertiseUtf8Accept() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        assertThat(
            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                .sendCommand("CAPABILITY"))
            .contains("UTF8=ACCEPT");
    }

    @Test
    void enableUtf8AcceptShouldSucceed() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        assertThat(
            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                .login(USER.asString(), USER_PASS)
                .sendCommand("ENABLE UTF8=ACCEPT"))
            .contains("* ENABLED UTF8=ACCEPT")
            .contains("OK ENABLE completed.");
    }

    @Test
    void enableUtf8AcceptShouldNotEchoUnsupportedCapability() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        assertThat(
            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                .login(USER.asString(), USER_PASS)
                .sendCommand("ENABLE BOGUS-CAPABILITY UTF8=ACCEPT"))
            .contains("* ENABLED UTF8=ACCEPT")
            .doesNotContain("BOGUS-CAPABILITY")
            .contains("OK ENABLE completed.");
    }
}
