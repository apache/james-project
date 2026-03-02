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
package org.apache.james.smtpserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.smtpserver.SMTPServerTestSystem.BOB;
import static org.apache.james.smtpserver.SMTPServerTestSystem.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AllowedUnauthenticatedSenderTest {

    @Nested
    class WithNullSenderForbidden {
        private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

        @BeforeEach
        void setUp() throws Exception {
            testSystem.setUp("smtpserver-allowed-unauthenticated-sender.xml");
        }

        @AfterEach
        void tearDown() {
            testSystem.smtpServer.destroy();
        }

        @Test
        void unauthenticatedSenderShouldBeAcceptedWhenInAllowedList() throws Exception {
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <allowed@example.com>")).isEqualTo(250);
            }
        }

        @Test
        void unauthenticatedSenderShouldBeRejectedWhenNotInAllowedList() throws Exception {
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <forbidden@example.com>")).isEqualTo(550);
            }
        }

        @Test
        void unauthenticatedSenderWithIpRestrictionShouldBeRejectedFromWrongIp() throws Exception {
            // ip-restricted@example.com is only allowed from 172.34.56.0/24
            // Proxy simulates connection from 127.0.0.1 which is not in that range
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <ip-restricted@example.com>")).isEqualTo(550);
            }
        }

        @Test
        void nullSenderShouldBeRejectedWhenAllowNullSenderIsFalse() throws Exception {
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <>")).isEqualTo(550);
            }
        }

        @Test
        void authenticatedUserShouldBypassAllowedSenderRestriction() throws Exception {
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.authenticate();
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <forbidden@example.com>")).isEqualTo(250);
            }
        }
    }

    @Nested
    class WithNullSenderAllowed {
        private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

        @BeforeEach
        void setUp() throws Exception {
            testSystem.setUp("smtpserver-allowed-unauthenticated-sender-allow-null.xml");
        }

        @AfterEach
        void tearDown() {
            testSystem.smtpServer.destroy();
        }

        @Test
        void nullSenderShouldBeAcceptedWhenAllowNullSenderIsTrue() throws Exception {
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <>")).isEqualTo(250);
            }
        }

        @Test
        void nonNullSenderShouldStillBeCheckedAgainstAllowedList() throws Exception {
            try (SmtpClient client = SmtpClient.connectWithProxy(testSystem.getBindedAddress(), "127.0.0.1")) {
                client.sendCommand("EHLO localhost");
                assertThat(client.sendCommand("MAIL FROM: <forbidden@example.com>")).isEqualTo(550);
            }
        }
    }

    private static class SmtpClient implements Closeable {
        private final Socket socket;
        private final BufferedReader reader;
        private final OutputStream out;

        private SmtpClient(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
            this.out = socket.getOutputStream();
        }

        static SmtpClient connectWithProxy(InetSocketAddress serverAddress, String sourceIp) throws IOException {
            Socket socket = new Socket(serverAddress.getAddress(), serverAddress.getPort());
            socket.setSoTimeout(5000);
            SmtpClient client = new SmtpClient(socket);
            client.readResponse(); // consume greeting
            client.sendRaw("PROXY TCP4 " + sourceIp + " 127.0.0.1 12345 25");
            return client;
        }

        int sendCommand(String command) throws IOException {
            sendRaw(command);
            return Integer.parseInt(readResponse().substring(0, 3));
        }

        void authenticate() throws IOException {
            sendCommand("AUTH PLAIN");
            int code = sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
            assertThat(code).as("authenticated").isEqualTo(235);
        }

        private void sendRaw(String line) throws IOException {
            out.write((line + "\r\n").getBytes(UTF_8));
            out.flush();
        }

        private String readResponse() throws IOException {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                // A final response line has a space after the 3-digit code (not a dash)
                if (line.length() >= 4
                        && Character.isDigit(line.charAt(0))
                        && Character.isDigit(line.charAt(1))
                        && Character.isDigit(line.charAt(2))
                        && line.charAt(3) == ' ') {
                    break;
                }
            }
            return sb.toString();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
