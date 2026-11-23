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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:membername")
class IMAPServerAuthenticateBurstTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    int port;

    @BeforeEach
    void setup() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void responsesSentInOneBurstShouldGiveAtMostThreeAttempts() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(2000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
            OutputStream output = socket.getOutputStream();
            reader.readLine();

            output.write("a AUTHENTICATE PLAIN\r\n".getBytes(US_ASCII));
            output.flush();
            assertThat(reader.readLine()).startsWith("+");

            StringBuilder burst = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                burst.append(plain(USER.asString(), "wrong-" + i)).append("\r\n");
            }
            output.write(burst.toString().getBytes(US_ASCII));
            output.flush();

            assertThat(readUntilClosedOrIdle(reader))
                .filteredOn(line -> line.startsWith("a NO") || line.startsWith("a OK"))
                .hasSizeLessThanOrEqualTo(3);
        }
    }

    @Test
    void theThirdFailureShouldCloseTheConnection() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
            OutputStream output = socket.getOutputStream();
            reader.readLine();

            for (int i = 0; i < 3; i++) {
                output.write(("a" + i + " AUTHENTICATE PLAIN\r\n").getBytes(US_ASCII));
                output.flush();
                assertThat(reader.readLine()).startsWith("+");
                output.write((plain(USER.asString(), "wrong-" + i) + "\r\n").getBytes(US_ASCII));
                output.flush();
                reader.readLine();
            }

            // The connection is closed: the end of the stream is reached
            assertThat(reader.readLine()).isNull();
        }
    }

    private static String plain(String user, String password) {
        return Base64.getEncoder().encodeToString(("\0" + user + "\0" + password).getBytes(US_ASCII));
    }

    private static List<String> readUntilClosedOrIdle(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        try {
            String line = reader.readLine();
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        } catch (SocketTimeoutException e) {
            // No more answer
        }
        return lines;
    }
}
