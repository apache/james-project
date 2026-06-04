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

import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerAuthenticationRequireSSLTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void loginShouldFailWhenRequireSSLAndUnEncryptedChannel() throws Exception {
        imapServer = createImapServer("imapServerRequireSSLIsTrueAndStartSSLIsFalse.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();

        assertThatThrownBy(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS))
            .hasMessage("Login failed");

    }

    @Test
    void loginShouldSuccessWhenRequireSSLAndEncryptedChannel() throws Exception {
        imapServer = createImapServer("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();

        IMAPSClient client = new IMAPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        client.execTLS();
        client.login(USER.asString(), USER_PASS);

        assertThat(client.getReplyString()).contains("OK LOGIN completed.");
    }

    @Test
    void loginShouldSuccessWhenNOTRequireSSLAndUnEncryptedChannel() throws Exception {
        imapServer = createImapServer("imapServerRequireSSLIsFalseAndStartSSLIsFalse.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();

        assertThatCode(() ->
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE))
            .doesNotThrowAnyException();
    }

    @Test
    void loginShouldSuccessWhenNOTRequireSSLAndEncryptedChannel() throws Exception {
        imapServer = createImapServer("imapServerRequireSSLIsFalseAndStartSSLIsTrue.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();

        IMAPSClient client = new IMAPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        client.execTLS();
        client.login(USER.asString(), USER_PASS);

        assertThat(client.getReplyString()).contains("OK LOGIN completed.");
    }
}
