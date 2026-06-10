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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.net.imap.IMAPClient;
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
        IMAPClient client = new IMAPClient();
        client.connect(LOCALHOST_IP, imapPort(server));
        try {
            client.sendCommand("AUTHENTICATE EXAMPLE-TOKEN");
            assertThat(client.getReplyString()).contains("+ " + encode(ExampleTokenSaslMechanism.CONTINUATION_PROMPT));

            client.sendData(encode(EXPECTED_TOKEN));
            assertThat(client.getReplyString())
                .contains("OK AUTHENTICATE completed.");

            client.sendCommand("PING");
            assertThat(client.getReplyString())
                .contains("PONG");
        } finally {
            client.disconnect();
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

    private String encode(String token) {
        return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String encodePlainInitialResponse() {
        return encode(BOB.asString() + "\0" + BOB.asString() + "\0" + BOB_PASSWORD);
    }
}
