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

package org.apache.james.managesieveserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class AuthenticateTest {
    private static final SaslIdentity IDENTITY = new SaslIdentity(Username.of("authenticated"), Username.of("authorized"));

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record CustomMechanism(RecordingExchange exchange) implements SaslMechanism {
        @Override
        public String name() {
            return "CUSTOM";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            return exchange;
        }
    }

    private static class InitialResponseMechanism implements SaslMechanism {
        private byte[] initialResponse;
        private int closeCount;

        @Override
        public String name() {
            return "INITIAL";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            initialResponse = request.initialResponse().orElseThrow();
            return new SaslExchange() {
                @Override
                public SaslStep firstStep() {
                    return new SaslStep.Success(IDENTITY, Optional.empty());
                }

                @Override
                public SaslStep onResponse(byte[] clientResponse) {
                    throw new IllegalStateException("Initial response mechanism does not expect a continuation");
                }

                @Override
                public void close() {
                    closeCount++;
                }
            };
        }
    }

    private static class RecordingExchange implements SaslExchange {
        private final AtomicInteger closeCount = new AtomicInteger();
        private final CountDownLatch closed = new CountDownLatch(1);
        private byte[] clientResponse;

        @Override
        public SaslStep firstStep() {
            return new SaslStep.Challenge(Optional.of(bytes("challenge")));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            this.clientResponse = clientResponse;
            return new SaslStep.Success(IDENTITY, Optional.of(bytes("server-data")));
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            closed.countDown();
        }
    }

    private ManageSieveClient client;
    private final ManageSieveServerTestSystem testSystem;

    public AuthenticateTest() throws Exception {
        this.testSystem = new ManageSieveServerTestSystem();
    }

    @BeforeEach
    void setUp() throws Exception {
        this.testSystem.setUp();
        this.client = new ManageSieveClient();
        this.client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        this.client.readResponse();
    }

    @AfterEach
    void tearDown() {
        this.testSystem.manageSieveServer.destroy();
    }

    @Test
    void plainLoginWithCorrectCredentialsShouldSucceed() throws IOException {
        this.authenticatePlain();
    }

    @Test
    void plainLoginWithWrongPasswordShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD + "wrong";
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginWithNotExistingUserShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "not-existing" + "\0" + "pwd";
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginShouldRejectDelegation() throws IOException {
        String initialClientResponse = "other-user\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");

        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();

        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(authenticationResponse.explanation()).contains("authentication failed");
    }

    @Test
    void plainLoginWithoutPasswordShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0";
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    // The SASL PLAIN standard (https://datatracker.ietf.org/doc/html/rfc4616) defines the following message:
    // message = [authzid] UTF8NUL authcid UTF8NUL passwd
    // The current code is more lenient and accepts the message without the first null byte.
    @Test
    void plainLoginWithoutLeadingNullByteShouldSucceed() throws IOException {
        String initialClientResponse = ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    // The SASL PLAIN standard defines UTF8NUL as separator. To stay compatible with older versions of James,
    // James is more lenient and also supports a space as the delimiter if the message is not base64-encoded.
    @Test
    void plainLoginWithSpaceAsDelimiterShouldSucceed() throws IOException {
        String initialClientResponse = " " + ManageSieveServerTestSystem.USERNAME.asString() + " " + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + initialClientResponse + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    // This tests the combination of both lenient behaviors above.
    @Test
    void plainLoginWithSpaceAsDelimiterWithoutLeadingSpaceShouldSucceed() throws IOException {
        String initialClientResponse = ManageSieveServerTestSystem.USERNAME.asString() + " " + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + initialClientResponse + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void plainLoginWithoutMechanismQuotesShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE PLAIN \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginWithoutInitialResponseQuotesShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" " + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)));
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void shouldRejectUnknownSaslMechanism() throws IOException {
        this.client.sendCommand("AUTHENTICATE {8+}");
        this.client.sendCommand("BAD\"\r\nOK");

        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();

        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(authenticationResponse.explanation()).contains("Unknown SASL mechanism");
        assertThat(authenticationResponse.responseLines()).isEmpty();
    }

    @Test
    void shouldReportDisabledPlainAsUnknownSaslMechanismInsteadOfRequiringEncryption() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("managesieveserver.xml"));
        configuration.addProperty("auth.plainAuthEnabled", false);
        client.disconnect();
        testSystem.manageSieveServer.destroy();
        testSystem.setUp(configuration);
        client = new ManageSieveClient();
        client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
        client.readResponse();

        client.sendCommand("AUTHENTICATE \"PLAIN\"");
        ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();

        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(authenticationResponse.responseCode()).isEmpty();
        assertThat(authenticationResponse.explanation()).contains("Unknown SASL mechanism");
    }

    @Test
    void plainLoginWithContinuationShouldSucceed() throws IOException {
        this.client.sendCommand("AUTHENTICATE \"PLAIN\"");
        ManageSieveClient.ServerResponse continuationResponse = this.client.readSaslChallenge();
        assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.CONTINUATION);
        assertThat(continuationResponse.explanation().get()).isEqualTo("");

        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand(Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)));
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void plainLoginWithContinuationCanBeAborted() throws IOException {
        this.client.sendCommand("AUTHENTICATE \"PLAIN\"");
        ManageSieveClient.ServerResponse continuationResponse = this.client.readSaslChallenge();
        assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.CONTINUATION);
        assertThat(continuationResponse.explanation().get()).isEqualTo("");

        this.client.sendCommand("*");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(authenticationResponse.explanation()).get().isEqualTo("Authentication failed with: authentication aborted by client");
    }

    @Test
    void doubleAuthenticationShouldFail() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        String command = "AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"";

        this.client.sendCommand(command);
        ManageSieveClient.ServerResponse firstAuthenticationResponse = this.client.readResponse();
        assertThat(firstAuthenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);

        this.client.sendCommand(command);
        ManageSieveClient.ServerResponse secondAuthenticationResponse = this.client.readResponse();
        assertThat(secondAuthenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(secondAuthenticationResponse.explanation()).get().isEqualTo("already authenticated");
    }

    @Test
    void unauthenticateInUnauthenticatedStateShouldFail() throws IOException {
        this.client.sendCommand("UNAUTHENTICATE");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void unauthenticateInAuthenticatedStateShouldSucceed() throws IOException {
        this.authenticatePlain();

        this.client.sendCommand("UNAUTHENTICATE");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void authenticatedStateUnlocksNewCommands() throws IOException {
        this.client.sendCommand("LISTSCRIPTS");
        ManageSieveClient.ServerResponse unauthenticatedResponse = this.client.readResponse();
        assertThat(unauthenticatedResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);

        this.authenticatePlain();

        this.client.sendCommand("LISTSCRIPTS");
        ManageSieveClient.ServerResponse authenticatedResponse = this.client.readResponse();
        assertThat(authenticatedResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);

        this.client.sendCommand("UNAUTHENTICATE");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);

        this.client.sendCommand("LISTSCRIPTS");
        ManageSieveClient.ServerResponse loggedOutResponse = this.client.readResponse();
        assertThat(loggedOutResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    // The server actually disconnects but isConnected still returns True.
    // Even when adding a delay, it still returns True.
    // There is probably something else broken with this test.
    @Disabled
    @Test
    void logoutShouldWorkInUnauthenticatedState() throws IOException, InterruptedException {
        this.client.sendCommand("LOGOUT");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        assertThat(this.client.isConnected()).isFalse();
    }

    // The server actually disconnects but isConnected still returns True.
    // Even when adding a delay, it still returns True.
    // There is probably something else broken with this test.
    @Disabled
    @Test
    void logoutShouldWorkInAuthenticatedState() throws IOException, InterruptedException {
        this.authenticatePlain();

        this.client.sendCommand("LOGOUT");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        assertThat(this.client.isConnected()).isFalse();
    }

    @Test
    void shouldDriveCustomMultiStepMechanismAndReturnFinalServerData() throws Exception {
        RecordingExchange exchange = new RecordingExchange();
        useSaslMechanisms(ImmutableList.of(new CustomMechanism(exchange)));

        client.sendCommand("AUTHENTICATE \"CUSTOM\"");
        assertThat(client.readSaslChallenge().explanation()).contains("Y2hhbGxlbmdl");

        client.sendCommand("\"cmVzcG9uc2U=\"");
        assertThat(client.readSaslSuccessData()).containsExactly(bytes("server-data"));

        client.sendCommand("CAPABILITY");
        assertThat(client.readResponse().responseLines()).contains("\"OWNER\" \"authorized\"");
        assertThat(exchange.clientResponse).containsExactly(bytes("response"));
        assertThat(exchange.closeCount).hasValue(1);
    }

    @Test
    void shouldAcceptLiteralInitialResponseAcrossNetworkFrames() throws Exception {
        InitialResponseMechanism mechanism = new InitialResponseMechanism();
        useSaslMechanisms(ImmutableList.of(mechanism));
        String encodedResponse = "cmVzcG9uc2U=";

        client.sendCommand("AUTHENTICATE \"INITIAL\" {" + encodedResponse.length() + "+}");
        client.sendCommand(encodedResponse);

        assertThat(client.readResponse().responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        assertThat(mechanism.initialResponse).containsExactly(bytes("response"));
        assertThat(mechanism.closeCount).isEqualTo(1);
    }

    @Test
    void shouldCloseActiveExchangeWhenClientResponseExceedsMaximumLineLength() throws Exception {
        RecordingExchange exchange = new RecordingExchange();
        useSaslMechanisms(ImmutableList.of(new CustomMechanism(exchange)));
        client.sendCommand("AUTHENTICATE \"CUSTOM\"");
        client.readSaslChallenge();

        client.sendCommand("A".repeat(9000));

        ManageSieveClient.ServerResponse response = client.readResponse();
        assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(response.explanation()).contains("Maximum command line length exceeded");
        assertThat(exchange.closeCount).hasValue(1);

        client.sendCommand("CAPABILITY");
        assertThat(client.readResponse().responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void shouldCloseActiveExchangeWhenClientDisconnects() throws Exception {
        RecordingExchange exchange = new RecordingExchange();
        useSaslMechanisms(ImmutableList.of(new CustomMechanism(exchange)));
        client.sendCommand("AUTHENTICATE \"CUSTOM\"");
        client.readSaslChallenge();

        client.disconnect();

        assertThat(exchange.closed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(exchange.closeCount).hasValue(1);
    }

    private void useSaslMechanisms(ImmutableList<SaslMechanism> saslMechanisms) throws Exception {
        client.disconnect();
        testSystem.manageSieveServer.destroy();
        testSystem.setUp(saslMechanisms);
        client = new ManageSieveClient();
        client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
        client.readResponse();
    }

    void authenticatePlain() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }
}
