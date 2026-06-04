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

import org.apache.james.core.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerPlainAuthEnabledWithoutRequireSSLTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerPlainAuthEnabledWithoutRequireSSL.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    @Test
    void loginShouldSucceed() {
        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS))
            .doesNotThrowAnyException();
    }

    @RepeatedTest(100)
    void authenticatePlainShouldSucceed() {
        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .authenticatePlain(USER.asString(), USER_PASS))
            .doesNotThrowAnyException();
    }

    @Test
    void capabilityShouldAdvertiseLoginAndAuthenticationPlain() throws Exception {
        testIMAPClient.connect("127.0.0.1", port);

        assertThat(testIMAPClient.capability())
            .doesNotContain("LOGINDISABLED")
            .contains("AUTH=PLAIN");
    }

    @Test
    void authenticatePlainShouldSucceedWhenPasswordHasMoreThan255Characters() {
        Username user1 = Username.of("user1@domain.org");
        String user1Password = "1".repeat(300);
        authenticator.addUser(user1, user1Password);
        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .authenticatePlain(user1.asString(), user1Password))
            .doesNotThrowAnyException();
    }
}
