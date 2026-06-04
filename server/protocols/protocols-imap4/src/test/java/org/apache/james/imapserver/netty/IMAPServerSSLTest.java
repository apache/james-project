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

import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerSSLTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private MailboxSession mailboxSession;
    private MessageManager inbox;
    private Socket clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerSSL.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();
        mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new BlindTrustManager()}, null);
        clientConnection = ctx.getSocketFactory().createSocket();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        byte[] buffer = new byte[8193];
        clientConnection.getInputStream().read(buffer);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    @Test
    void startTlsCapabilityShouldFailWhenSSLSocket() throws Exception {
        clientConnection.getOutputStream().write("a0 STARTTLS\r\n".getBytes(StandardCharsets.UTF_8));
        assertThat(readString(clientConnection)).startsWith("a0 BAD STARTTLS failed. Unknown command.");
    }

    @Test
    void startTlsCapabilityShouldNotBeAdvertisedWhenSSLSocket() throws Exception {
        clientConnection.getOutputStream().write("a0 CAPABILITY\r\n".getBytes(StandardCharsets.UTF_8));
        assertThat(readString(clientConnection)).doesNotContain("STARTTLS");
    }

    private String readString(Socket channel) throws IOException {
        byte[] buffer = new byte[8193];
        int read = channel.getInputStream().read(buffer);
        return new String(buffer, 0, read, StandardCharsets.US_ASCII);
    }
}
