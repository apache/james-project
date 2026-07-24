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
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.sasl.kerberos.GssapiTestClient;
import org.apache.james.protocols.sasl.kerberos.KerberosTestExtension;
import org.apache.james.protocols.sasl.kerberos.KerberosTestFixture;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;

// The embedded KDC temporarily replaces the JVM-wide Kerberos configuration.
@ResourceLock(KerberosTestFixture.KRB5_CONFIGURATION_RESOURCE)
class SaslGssapiIntegrationTest {
    private static final String DELEGATION_TARGET = "bob@" + KerberosTestFixture.REALM;
    private static final String HOST = "127.0.0.1";
    private static final int MAX_SASL_ROUNDS = 10;
    private static final int SMTP_AUTH_CONTINUE = 334;
    private static final int SMTP_AUTH_FAILURE = 535;
    private static final int SMTP_AUTH_SUCCESS = 235;
    private static final int SMTP_OK = 250;
    private static final byte[] INVALID_GSSAPI_TOKEN = {1, 2, 3};

    // Provision Kerberos credentials before James resolves them from its server configuration.
    @Order(1)
    @RegisterExtension
    static KerberosTestExtension kerberos = new KerberosTestExtension("imap", "smtp");

    @Order(2)
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir -> {
        TemporaryJamesServer temporaryJamesServer = new TemporaryJamesServer(tmpDir);
        temporaryJamesServer.copyResources("imapserver-gssapi.xml", "imapserver.xml");
        temporaryJamesServer.copyResources("smtpserver-gssapi.xml", "smtpserver.xml");
        return MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .usersRepository(DEFAULT)
            .build();
    })
        .server(MemoryJamesServerMain::createServer)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_ENCLOSING_CLASS)
        .build();

    @BeforeAll
    static void provisionKerberosUser(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(KerberosTestFixture.REALM)
            .addUser(KerberosTestFixture.USER_PRINCIPAL, "unused-password")
            .addUser(DELEGATION_TARGET, "unused-password");
    }

    private static AuthenticatingIMAPClient connectedImapClient(int port) throws Exception {
        AuthenticatingIMAPClient client = new AuthenticatingIMAPClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect(HOST, port);
        return client;
    }

    private static AuthenticatingIMAPClient imapClient(int port) throws Exception {
        AuthenticatingIMAPClient client = connectedImapClient(port);
        assertThat(client.execTLS()).isTrue();
        return client;
    }

    private static SMTPSClient connectedSmtpClient(int port) throws Exception {
        SMTPSClient client = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect(HOST, port);
        return client;
    }

    private static SMTPSClient smtpClient(int port) throws Exception {
        SMTPSClient client = connectedSmtpClient(port);
        assertThat(client.execTLS()).isTrue();
        return client;
    }

    private static byte[] imapChallenge(String reply) {
        String challenge = reply.trim().substring(1).trim();
        return challenge.isEmpty() ? new byte[0] : Base64.getDecoder().decode(challenge);
    }

    private static byte[] smtpChallenge(String reply) {
        String challenge = reply.trim().substring(3).trim();
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

    private static int completeSmtpExchange(SMTPSClient smtpClient, GssapiTestClient gssapiClient, int replyCode) throws Exception {
        // Relay each server challenge through the JDK GSSAPI client until SASL completes.
        for (int round = 0; round < MAX_SASL_ROUNDS && replyCode == SMTP_AUTH_CONTINUE; round++) {
            replyCode = smtpClient.sendCommand(encode(gssapiClient.evaluate(smtpChallenge(smtpClient.getReplyString()))));
        }
        return replyCode;
    }

    @Nested
    class Imap {
        @Test
        void shouldLoadConfiguredGssapiFactoryAndAuthenticate(GuiceJamesServer server) throws Exception {
            // Connect to IMAP over TLS, as required by the configured GSSAPI mechanism.
            int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
            AuthenticatingIMAPClient imapClient = imapClient(port);

            try (GssapiTestClient gssapiClient = kerberos.client("imap")) {
                // Verify James loaded and advertised the configured GSSAPI plugin.
                imapClient.capability();
                assertThat(imapClient.getReplyString()).contains("AUTH=GSSAPI");

                // Complete the Kerberos token exchange with an initial client response.
                int replyCode = completeImapExchange(imapClient, gssapiClient,
                    imapClient.sendCommand("AUTHENTICATE GSSAPI " + encode(gssapiClient.initialResponse())));

                // Verify authentication succeeded and the IMAP session remains usable.
                assertThat(replyCode)
                    .withFailMessage("Unexpected IMAP authentication reply: %s", imapClient.getReplyString())
                    .isEqualTo(IMAPReply.OK);
                assertThat(gssapiClient.isComplete()).isTrue();
                assertThat(imapClient.getReplyString()).contains("OK AUTHENTICATE completed.");
                assertThat(imapClient.sendCommand("NOOP")).isEqualTo(IMAPReply.OK);
            } finally {
                imapClient.disconnect();
            }
        }

        @Test
        void shouldOnlyAdvertiseGssapiAfterStartTls(GuiceJamesServer server) throws Exception {
            // Connect without TLS to inspect the initial IMAP capabilities.
            int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
            AuthenticatingIMAPClient imapClient = connectedImapClient(port);

            try {
                // Verify GSSAPI is hidden while the connection is clear text.
                imapClient.capability();
                assertThat(imapClient.getReplyString()).doesNotContain("AUTH=GSSAPI");

                // Start TLS and verify GSSAPI becomes available.
                assertThat(imapClient.execTLS()).isTrue();
                imapClient.capability();
                assertThat(imapClient.getReplyString()).contains("AUTH=GSSAPI");
            } finally {
                imapClient.disconnect();
            }
        }

        @Test
        void shouldAuthenticateWithExplicitSelfAuthorizationIdentity(GuiceJamesServer server) throws Exception {
            // Connect over TLS and create a client requesting its own James identity.
            int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
            AuthenticatingIMAPClient imapClient = imapClient(port);

            try (GssapiTestClient gssapiClient = kerberos.client("imap", Optional.of(KerberosTestFixture.USER_PRINCIPAL))) {
                // Authenticate with an explicit authorization identity matching the Kerberos principal.
                int replyCode = completeImapExchange(imapClient, gssapiClient,
                    imapClient.sendCommand("AUTHENTICATE GSSAPI " + encode(gssapiClient.initialResponse())));

                // Verify self-authorization is accepted.
                assertThat(replyCode).isEqualTo(IMAPReply.OK);
                assertThat(gssapiClient.isComplete()).isTrue();
            } finally {
                imapClient.disconnect();
            }
        }

        @Test
        void shouldAuthenticateWithoutInitialResponse(GuiceJamesServer server) throws Exception {
            // Connect to IMAP over TLS without preparing an inline authentication token.
            int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
            AuthenticatingIMAPClient imapClient = imapClient(port);

            try (GssapiTestClient gssapiClient = kerberos.client("imap")) {
                // Start authentication without a token and verify the server requests one.
                int replyCode = imapClient.sendCommand("AUTHENTICATE GSSAPI");
                assertThat(replyCode).isEqualTo(IMAPReply.CONT);
                assertThat(imapChallenge(imapClient.getReplyString())).isEmpty();

                // Send the initial token and complete the remaining Kerberos exchange.
                replyCode = completeImapExchange(imapClient, gssapiClient,
                    imapClient.sendData(encode(gssapiClient.initialResponse())));

                // Verify authentication succeeds without an inline initial response.
                assertThat(replyCode).isEqualTo(IMAPReply.OK);
                assertThat(gssapiClient.isComplete()).isTrue();
            } finally {
                imapClient.disconnect();
            }
        }

        @Test
        void shouldRejectInvalidTokenAndKeepConnectionUsable(GuiceJamesServer server) throws Exception {
            // Connect to IMAP over TLS before submitting a malformed GSSAPI token.
            int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
            AuthenticatingIMAPClient imapClient = imapClient(port);

            try {
                // Attempt authentication with data that is not a valid Kerberos token.
                int replyCode = imapClient.sendCommand("AUTHENTICATE GSSAPI " + encode(INVALID_GSSAPI_TOKEN));

                // Verify authentication fails without closing or corrupting the IMAP session.
                assertThat(replyCode).isEqualTo(IMAPReply.NO);
                assertThat(imapClient.getReplyString()).contains("NO AUTHENTICATE failed.");
                assertThat(imapClient.sendCommand("NOOP")).isEqualTo(IMAPReply.OK);
            } finally {
                imapClient.disconnect();
            }
        }

        @Test
        void shouldRejectUnauthorizedIdentity(GuiceJamesServer server) throws Exception {
            // Connect over TLS and request a James identity different from the Kerberos principal.
            int port = server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
            AuthenticatingIMAPClient imapClient = imapClient(port);

            try (GssapiTestClient gssapiClient = kerberos.client("imap", Optional.of(DELEGATION_TARGET))) {
                // Complete Kerberos authentication while requesting the unauthorized target identity.
                int replyCode = completeImapExchange(imapClient, gssapiClient,
                    imapClient.sendCommand("AUTHENTICATE GSSAPI " + encode(gssapiClient.initialResponse())));

                // Verify James rejects delegation while keeping the IMAP session usable.
                assertThat(replyCode).isEqualTo(IMAPReply.NO);
                assertThat(imapClient.getReplyString()).contains("Delegation is forbidden.");
                assertThat(imapClient.sendCommand("NOOP")).isEqualTo(IMAPReply.OK);
            } finally {
                imapClient.disconnect();
            }
        }
    }

    @Nested
    class Smtp {
        @Test
        void shouldLoadConfiguredGssapiFactoryAndAuthenticate(GuiceJamesServer server) throws Exception {
            // Connect to SMTP over TLS, as required by the configured GSSAPI mechanism.
            int port = server.getProbe(SmtpGuiceProbe.class).getSmtpStartTlsPort().getValue();
            SMTPSClient smtpClient = smtpClient(port);

            try (GssapiTestClient gssapiClient = kerberos.client("smtp")) {
                // Verify James loaded and advertised the configured GSSAPI plugin.
                smtpClient.sendCommand("EHLO localhost");
                assertThat(smtpClient.getReplyString()).contains("AUTH GSSAPI");

                // Complete the Kerberos token exchange with an initial client response.
                int replyCode = completeSmtpExchange(smtpClient, gssapiClient,
                    smtpClient.sendCommand("AUTH GSSAPI " + encode(gssapiClient.initialResponse())));

                // Verify authentication succeeded and the SMTP session remains usable.
                assertThat(replyCode).isEqualTo(SMTP_AUTH_SUCCESS);
                assertThat(gssapiClient.isComplete()).isTrue();
                assertThat(smtpClient.getReplyString()).contains("Authentication Successful");
                assertThat(smtpClient.noop()).isEqualTo(SMTP_OK);
            } finally {
                smtpClient.disconnect();
            }
        }

        @Test
        void shouldOnlyAdvertiseGssapiAfterStartTls(GuiceJamesServer server) throws Exception {
            // Connect without TLS to inspect the initial SMTP extensions.
            int port = server.getProbe(SmtpGuiceProbe.class).getSmtpStartTlsPort().getValue();
            SMTPSClient smtpClient = connectedSmtpClient(port);

            try {
                // Verify GSSAPI is hidden while the connection is clear text.
                smtpClient.sendCommand("EHLO localhost");
                assertThat(smtpClient.getReplyString()).doesNotContain("AUTH GSSAPI");

                // Start TLS and verify GSSAPI becomes available.
                assertThat(smtpClient.execTLS()).isTrue();
                smtpClient.sendCommand("EHLO localhost");
                assertThat(smtpClient.getReplyString()).contains("AUTH GSSAPI");
            } finally {
                smtpClient.disconnect();
            }
        }

        @Test
        void shouldAuthenticateWithoutInitialResponse(GuiceJamesServer server) throws Exception {
            // Connect to SMTP over TLS without preparing an inline authentication token.
            int port = server.getProbe(SmtpGuiceProbe.class).getSmtpStartTlsPort().getValue();
            SMTPSClient smtpClient = smtpClient(port);

            try (GssapiTestClient gssapiClient = kerberos.client("smtp")) {
                smtpClient.sendCommand("EHLO localhost");

                // Start authentication without a token and verify the server requests one.
                int replyCode = smtpClient.sendCommand("AUTH GSSAPI");
                assertThat(replyCode).isEqualTo(SMTP_AUTH_CONTINUE);
                assertThat(smtpChallenge(smtpClient.getReplyString())).isEmpty();

                // Send the initial token and complete the remaining Kerberos exchange.
                replyCode = completeSmtpExchange(smtpClient, gssapiClient,
                    smtpClient.sendCommand(encode(gssapiClient.initialResponse())));

                // Verify authentication succeeds without an inline initial response.
                assertThat(replyCode).isEqualTo(SMTP_AUTH_SUCCESS);
                assertThat(gssapiClient.isComplete()).isTrue();
            } finally {
                smtpClient.disconnect();
            }
        }

        @Test
        void shouldRejectInvalidTokenAndKeepConnectionUsable(GuiceJamesServer server) throws Exception {
            // Connect to SMTP over TLS before submitting a malformed GSSAPI token.
            int port = server.getProbe(SmtpGuiceProbe.class).getSmtpStartTlsPort().getValue();
            SMTPSClient smtpClient = smtpClient(port);

            try {
                smtpClient.sendCommand("EHLO localhost");

                // Attempt authentication with data that is not a valid Kerberos token.
                int replyCode = smtpClient.sendCommand("AUTH GSSAPI " + encode(INVALID_GSSAPI_TOKEN));

                // Verify authentication fails without closing or corrupting the SMTP session.
                assertThat(replyCode).isEqualTo(SMTP_AUTH_FAILURE);
                assertThat(smtpClient.getReplyString()).contains("535 Authentication Failed");
                assertThat(smtpClient.noop()).isEqualTo(SMTP_OK);
            } finally {
                smtpClient.disconnect();
            }
        }
    }
}
