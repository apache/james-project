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

package org.apache.james.crowdsec;

import static org.apache.james.crowdsec.CrowdsecSMTPConnectHandlerProxyProtocolTest.readUntilClosed;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The PROXY header is sent by hand: the TCP peer, hence the proxy, is 127.0.0.1 while the client is {@link #CLIENT_IP}.
 */
class CrowdsecProxyProtocolTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(MemoryJamesServerMain::createServer)
        .extension(crowdsecExtension)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
        .build();

    private static final String PROXY_IP = "127.0.0.1";
    private static final String CLIENT_IP = "10.11.12.13";
    private static final String DOMAIN = "domain.tld";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String BOB_PASSWORD = "bobPassword";

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB, BOB_PASSWORD);
        server.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(Username.of(BOB)));
    }

    private static String sendThroughProxy(int port, String commands) throws IOException {
        try (Socket socket = new Socket(PROXY_IP, port)) {
            socket.setSoTimeout(10000);
            socket.getOutputStream().write(("PROXY TCP4 " + CLIENT_IP + " " + PROXY_IP + " 12345 " + port + "\r\n" + commands)
                .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return readUntilClosed(socket.getInputStream());
        }
    }

    @Nested
    class IMAP {
        private static final String COMMANDS = "a1 LOGIN " + BOB + " " + BOB_PASSWORD + "\r\n"
            + "a2 CREATE created\r\n"
            + "a3 LOGOUT\r\n";

        private int proxiedImapPort(GuiceJamesServer server) {
            return server.getProbe(ImapGuiceProbe.class)
                .getPort(asyncServer -> asyncServer.getHelloName().equals("imapServerWithProxyEnabled"))
                .orElseThrow();
        }

        private boolean createdMailboxExists(GuiceJamesServer server) {
            return server.getProbe(MailboxProbeImpl.class).listUserMailboxes(BOB).contains("created");
        }

        @Test
        void connectionShouldBeClosedBeforeAnyCommandWhenTheProxiedClientIsBanned(GuiceJamesServer server) throws Exception {
            crowdsecExtension.banIP("--ip", CLIENT_IP);

            String received = sendThroughProxy(proxiedImapPort(server), COMMANDS);

            assertThat(received).doesNotContain("a1 OK");
            assertThat(createdMailboxExists(server)).isFalse();
        }

        @Test
        void connectionShouldBeClosedWhenTheProxiedClientIsBannedAndCommandsFollowTheHeaderLater(GuiceJamesServer server) throws Exception {
            crowdsecExtension.banIP("--ip", CLIENT_IP);
            int port = proxiedImapPort(server);

            String received;
            try (Socket socket = new Socket(PROXY_IP, port)) {
                socket.setSoTimeout(10000);
                socket.getOutputStream().write(("PROXY TCP4 " + CLIENT_IP + " " + PROXY_IP + " 12345 " + port + "\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                Thread.sleep(1000);
                try {
                    socket.getOutputStream().write(COMMANDS.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    // already closed by the server
                }
                received = readUntilClosed(socket.getInputStream());
            }

            assertThat(received).doesNotContain("a1 OK");
            assertThat(createdMailboxExists(server)).isFalse();
        }

        @Test
        void connectionShouldBeAcceptedWhenOnlyTheProxyIsBanned(GuiceJamesServer server) throws Exception {
            crowdsecExtension.banIP("--ip", PROXY_IP);
            int port = proxiedImapPort(server);

            String received;
            try (Socket socket = new Socket(PROXY_IP, port)) {
                socket.setSoTimeout(10000);
                // The PROXY header and the LOGIN in one segment, CREATE once authenticated: it depends on the LOGIN
                socket.getOutputStream().write(("PROXY TCP4 " + CLIENT_IP + " " + PROXY_IP + " 12345 " + port + "\r\n"
                    + "a1 LOGIN " + BOB + " " + BOB_PASSWORD + "\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                StringBuilder read = new StringBuilder();
                while (!read.toString().contains("a1 OK")) {
                    int b = socket.getInputStream().read();
                    if (b == -1) {
                        break;
                    }
                    read.append((char) b);
                }
                socket.getOutputStream().write("a2 CREATE created\r\na3 LOGOUT\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                received = read + readUntilClosed(socket.getInputStream());
            }

            assertThat(received).contains("a1 OK", "a2 OK");
            assertThat(createdMailboxExists(server)).isTrue();
        }
    }

    @Nested
    class POP3 {
        private static final String COMMANDS = "USER " + BOB + "\r\n"
            + "PASS " + BOB_PASSWORD + "\r\n"
            + "DELE 1\r\n"
            + "QUIT\r\n";

        private int proxiedPop3Port(GuiceJamesServer server) {
            return server.getProbe(Pop3GuiceProbe.class)
                .getPort(asyncServer -> asyncServer.getHelloName().equals("pop3ServerWithProxyEnabled"))
                .orElseThrow();
        }

        private void appendMessage(GuiceJamesServer server) throws Exception {
            server.getProbe(MailboxProbeImpl.class).appendMessage(BOB, MailboxPath.inbox(Username.of(BOB)),
                new ByteArrayInputStream("Subject: test\r\n\r\nbody\r\n".getBytes(StandardCharsets.US_ASCII)), new Date(), false, new Flags());
        }

        private int messageCount(GuiceJamesServer server) {
            return server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), BOB, 100)
                .size();
        }

        @Test
        void connectionShouldBeClosedBeforeAnyCommandWhenTheProxiedClientIsBanned(GuiceJamesServer server) throws Exception {
            appendMessage(server);
            crowdsecExtension.banIP("--ip", CLIENT_IP);

            String received = sendThroughProxy(proxiedPop3Port(server), COMMANDS);

            assertThat(received).doesNotContain("+OK Welcome");
            assertThat(messageCount(server)).isEqualTo(1);
        }

        @Test
        void connectionShouldBeAcceptedWhenOnlyTheProxyIsBanned(GuiceJamesServer server) throws Exception {
            appendMessage(server);
            crowdsecExtension.banIP("--ip", PROXY_IP);

            String received = sendThroughProxy(proxiedPop3Port(server), COMMANDS);

            assertThat(received).contains("+OK Welcome", "+OK Message deleted");
            assertThat(messageCount(server)).isZero();
        }
    }
}
