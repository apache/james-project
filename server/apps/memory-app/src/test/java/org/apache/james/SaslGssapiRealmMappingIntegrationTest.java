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

package org.apache.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Optional;

import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.commons.net.imap.IMAPReply;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.sasl.kerberos.GssapiTestClient;
import org.apache.james.protocols.sasl.kerberos.KerberosTestExtension;
import org.apache.james.protocols.sasl.kerberos.KerberosTestFixture;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;

// The embedded KDC temporarily replaces the JVM-wide Kerberos configuration.
@ResourceLock(KerberosTestFixture.KRB5_CONFIGURATION_RESOURCE)
class SaslGssapiRealmMappingIntegrationTest {
    private static final String DOMAIN = "example.com";
    private static final String USERNAME = "alice@" + DOMAIN;
    private static final String HOST = "127.0.0.1";
    private static final int MAX_SASL_ROUNDS = 10;

    // Provision Kerberos credentials before James resolves them from its server configuration.
    @Order(1)
    @RegisterExtension
    static KerberosTestExtension kerberos = new KerberosTestExtension("imap");

    @Order(2)
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir -> {
        TemporaryJamesServer temporaryJamesServer = new TemporaryJamesServer(tmpDir);
        temporaryJamesServer.copyResources("imapserver-gssapi-realm-mapping.xml", "imapserver.xml");
        return MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .usersRepository(DEFAULT)
            .build();
    })
        .server(MemoryJamesServerMain::createServer)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_ENCLOSING_CLASS)
        .build();

    // The Kerberos realm is JAMES.TEST while the mail domain is example.com.
    @BeforeAll
    static void provisionMappedUser(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USERNAME, "unused-password");
    }

    @Test
    void shouldAuthenticateThroughTheConfiguredRealmMapping(GuiceJamesServer server) throws Exception {
        // Connect to IMAP over TLS, as required by the configured GSSAPI mechanism.
        int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
        AuthenticatingIMAPClient imapClient = imapClient(port);

        try (GssapiTestClient gssapiClient = kerberos.client("imap")) {
            // Authenticate as alice@JAMES.TEST, whose realm the configuration maps onto example.com.
            int replyCode = completeImapExchange(imapClient, gssapiClient,
                imapClient.sendCommand("AUTHENTICATE GSSAPI " + encode(gssapiClient.initialResponse())));

            assertThat(replyCode)
                .withFailMessage("Unexpected IMAP authentication reply: %s", imapClient.getReplyString())
                .isEqualTo(IMAPReply.OK);
            assertThat(gssapiClient.isComplete()).isTrue();

            // Verify the session is bound to the mapped account rather than to the realm as a domain.
            assertThat(imapClient.select("INBOX")).isTrue();
        } finally {
            imapClient.disconnect();
        }
    }

    @Test
    void shouldMapAnExplicitPrincipalAuthorizationIdentity(GuiceJamesServer server) throws Exception {
        // Connect over TLS and create a client requesting its own Kerberos principal as authorization identity.
        int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
        AuthenticatingIMAPClient imapClient = imapClient(port);

        try (GssapiTestClient gssapiClient = kerberos.client("imap", Optional.of(KerberosTestFixture.USER_PRINCIPAL))) {
            int replyCode = completeImapExchange(imapClient, gssapiClient,
                imapClient.sendCommand("AUTHENTICATE GSSAPI " + encode(gssapiClient.initialResponse())));

            // Verify the authorization identity went through the realm mapping too.
            assertThat(replyCode)
                .withFailMessage("Unexpected IMAP authentication reply: %s", imapClient.getReplyString())
                .isEqualTo(IMAPReply.OK);
            assertThat(gssapiClient.isComplete()).isTrue();
            assertThat(imapClient.select("INBOX")).isTrue();
        } finally {
            imapClient.disconnect();
        }
    }

    private static AuthenticatingIMAPClient imapClient(int port) throws Exception {
        AuthenticatingIMAPClient client = new AuthenticatingIMAPClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect(HOST, port);
        assertThat(client.execTLS()).isTrue();
        return client;
    }

    private static byte[] imapChallenge(String reply) {
        String challenge = reply.trim().substring(1).trim();
        return challenge.isEmpty() ? new byte[0] : Base64.getDecoder().decode(challenge);
    }

    private static String encode(byte[] token) {
        return Base64.getEncoder().encodeToString(token);
    }

    private static int completeImapExchange(AuthenticatingIMAPClient imapClient, GssapiTestClient gssapiClient, int replyCode) throws Exception {
        // Relay each server challenge through the JDK GSSAPI client until SASL completes.
        for (int round = 0; round < MAX_SASL_ROUNDS && IMAPReply.isContinuation(replyCode); round++) {
            replyCode = imapClient.sendData(encode(gssapiClient.evaluate(imapChallenge(imapClient.getReplyString()))));
        }
        return replyCode;
    }
}
