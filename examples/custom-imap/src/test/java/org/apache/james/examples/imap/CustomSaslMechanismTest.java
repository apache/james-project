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

package org.apache.james.examples.imap;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Predicate;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.examples.imap.sasl.ExampleTokenSaslMechanism;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CustomSaslMechanismTest {
    private static class ClientConnection implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private ClientConnection(String host, int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port));
            socket.setSoTimeout(5_000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        private void writeLine(String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        private String readUntil(Predicate<String> condition) throws IOException {
            StringBuilder response = new StringBuilder();
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new EOFException("Connection closed while waiting for IMAP response");
                }
                response.append(line).append("\n");
                if (condition.test(line)) {
                    return response.toString();
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final String EXPECTED_TOKEN = "secret-token";
    private static final String LOCALHOST_IP = "127.0.0.1";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(MemoryJamesServerMain::createServer)
        .build();

    @BeforeEach
    void setup(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD);
    }

    @Test
    void imapServerShouldAdvertiseCustomSaslMechanism(GuiceJamesServer server) throws IOException {
        assertThat(new TestIMAPClient().connect("127.0.0.1", imapPort(server))
            .sendCommand("CAPABILITY"))
            .contains("AUTH=PLAIN", "AUTH=EXAMPLE-TOKEN");
    }

    @Test
    void imapServerShouldAuthenticateCustomSaslMechanismUsingOwnConfiguration(GuiceJamesServer server) throws IOException {
        TestIMAPClient client = new TestIMAPClient().connect("127.0.0.1", imapPort(server));

        assertThat(client.sendCommand("AUTHENTICATE EXAMPLE-TOKEN " + encode(EXPECTED_TOKEN)))
            .contains("OK AUTHENTICATE completed.");
        assertThat(client.sendCommand("PING"))
            .contains("PONG");
    }

    @Test
    void imapServerShouldAuthenticateCustomSaslMechanismUsingContinuation(GuiceJamesServer server) throws IOException {
        try (ClientConnection client = clientConnection(server)) {
            client.readUntil(line -> line.startsWith("* OK"));

            client.writeLine("A01 AUTHENTICATE EXAMPLE-TOKEN");
            assertThat(client.readUntil(line -> line.startsWith("+")))
                .contains("+ " + encode(ExampleTokenSaslMechanism.CONTINUATION_PROMPT));

            client.writeLine(encode(EXPECTED_TOKEN));
            String authenticationResponse = client.readUntil(line -> line.startsWith("A01"));
            assertThat(authenticationResponse)
                .contains("OK AUTHENTICATE completed.")
                .doesNotContain("+ " + encode(ExampleTokenSaslMechanism.CONTINUATION_PROMPT));

            client.writeLine("A02 PING");
            assertThat(client.readUntil(line -> line.startsWith("A02")))
                .contains("PONG");
        }
    }

    @Test
    void imapServerShouldAuthenticateCustomSaslMechanismReturningServerDataOnSuccess(GuiceJamesServer server) throws IOException {
        try (ClientConnection client = clientConnection(server)) {
            // GIVEN a custom SASL exchange started without SASL-IR
            client.readUntil(line -> line.startsWith("* OK"));
            client.writeLine("A01 AUTHENTICATE EXAMPLE-TOKEN");
            assertThat(client.readUntil(line -> line.startsWith("+")))
                .contains("+ " + encode(ExampleTokenSaslMechanism.CONTINUATION_PROMPT));

            // WHEN the mechanism succeeds with final server data, as GSSAPI/Kerberos-like SASL mechanisms may require
            client.writeLine(encode(EXPECTED_TOKEN + ExampleTokenSaslMechanism.SUCCESS_DATA_TOKEN_SUFFIX));
            assertThat(client.readUntil(line -> line.startsWith("+") || line.startsWith("A01")))
                .contains("+ " + encode(ExampleTokenSaslMechanism.SUCCESS_DATA));

            // THEN the client acknowledges the final server data before IMAP completes authentication
            client.writeLine("");
            assertThat(client.readUntil(line -> line.startsWith("A01")))
                .contains("OK AUTHENTICATE completed.");

            // THEN the authenticated IMAP session remains usable
            client.writeLine("A02 PING");
            assertThat(client.readUntil(line -> line.startsWith("A02")))
                .contains("PONG");
        }
    }

    @Test
    void plainSaslAuthenticationShouldStillWork(GuiceJamesServer server) throws IOException {
        TestIMAPClient client = new TestIMAPClient().connect("127.0.0.1", imapPort(server));

        assertThat(client.sendCommand("AUTHENTICATE PLAIN " + encodePlainInitialResponse()))
            .contains("OK AUTHENTICATE completed.");
        assertThat(client.sendCommand("PING"))
            .contains("PONG");
    }

    @Test
    void imapServerShouldRejectInvalidCustomSaslToken(GuiceJamesServer server) throws IOException {
        assertThat(new TestIMAPClient().connect("127.0.0.1", imapPort(server))
            .sendCommand("AUTHENTICATE EXAMPLE-TOKEN " + encode("invalid-token")))
            .contains("NO AUTHENTICATE failed.");
    }

    private int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    private ClientConnection clientConnection(GuiceJamesServer server) throws IOException {
        return new ClientConnection(LOCALHOST_IP, imapPort(server));
    }

    private String encode(String token) {
        return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String encodePlainInitialResponse() {
        return encode(BOB.asString() + "\0" + BOB.asString() + "\0" + BOB_PASSWORD);
    }
}
