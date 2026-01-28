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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class AuthenticateTest {
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
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginWithNotExistingUserShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "not-existing" + "\0" + "pwd";
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginWithoutPasswordShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0";
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    // The SASL PLAIN standard (https://datatracker.ietf.org/doc/html/rfc4616) defines the following message:
    // message = [authzid] UTF8NUL authcid UTF8NUL passwd
    // The current code is more lenient and accepts the message without the first null byte.
    @Test
    void plainLoginWithoutLeadingNullByteShouldSucceed() throws IOException {
        String initialClientResponse = ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    // The SASL PLAIN standard defines UTF8NUL as separator. To stay compatible with older versions of James,
    // James is more lenient and also supports a space as the delimiter if the message is not base64-encoded.
    @Test
    void plainLoginWithSpaceAsDelimiterShouldSucceed() throws IOException {
        String initialClientResponse = " " + ManageSieveServerTestSystem.USERNAME.asString() + " " + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + initialClientResponse + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    // This tests the combination of both lenient behaviors above.
    @Test
    void plainLoginWithSpaceAsDelimiterWithoutLeadingSpaceShouldSucceed() throws IOException {
        String initialClientResponse = ManageSieveServerTestSystem.USERNAME.asString() + " " + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + initialClientResponse + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void plainLoginWithoutMechanismQuotesShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE PLAIN \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginWithoutInitialResponseQuotesShouldNotSucceed() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" " + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)));
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void plainLoginWithContinuationShouldSucceed() throws IOException {
        this.client.sendCommand("AUTHENTICATE \"PLAIN\"");
        ManageSieveClient.ServerResponse continuationResponse = this.client.readResponse();
        Assertions.assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.CONTINUATION);
        Assertions.assertThat(continuationResponse.explanation().get()).isEqualTo("");

        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand(Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)));
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void plainLoginWithContinuationCanBeAborted() throws IOException {
        this.client.sendCommand("AUTHENTICATE \"PLAIN\"");
        ManageSieveClient.ServerResponse continuationResponse = this.client.readResponse();
        Assertions.assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.CONTINUATION);
        Assertions.assertThat(continuationResponse.explanation().get()).isEqualTo("");

        this.client.sendCommand("*");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        Assertions.assertThat(authenticationResponse.explanation()).get().isEqualTo("Authentication failed with: authentication aborted by client");
    }

    @Test
    void doubleAuthenticationShouldFail() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        String command = "AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"";

        this.client.sendCommand(command);
        ManageSieveClient.ServerResponse firstAuthenticationResponse = this.client.readResponse();
        Assertions.assertThat(firstAuthenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        this.client.readResponse(); // Read capabilities

        this.client.sendCommand(command);
        ManageSieveClient.ServerResponse secondAuthenticationResponse = this.client.readResponse();
        Assertions.assertThat(secondAuthenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        Assertions.assertThat(secondAuthenticationResponse.explanation()).get().isEqualTo("already authenticated");
    }

    @Test
    void unauthenticateInUnauthenticatedStateShouldFail() throws IOException {
        this.client.sendCommand("UNAUTHENTICATE");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        Assertions.assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    @Test
    void unauthenticateInAuthenticatedStateShouldSucceed() throws IOException {
        this.authenticatePlain();

        this.client.sendCommand("UNAUTHENTICATE");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        Assertions.assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
    }

    @Test
    void authenticatedStateUnlocksNewCommands() throws IOException {
        this.client.sendCommand("LISTSCRIPTS");
        ManageSieveClient.ServerResponse unauthenticatedResponse = this.client.readResponse();
        Assertions.assertThat(unauthenticatedResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);

        this.authenticatePlain();

        this.client.sendCommand("LISTSCRIPTS");
        ManageSieveClient.ServerResponse authenticatedResponse = this.client.readResponse();
        Assertions.assertThat(authenticatedResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);

        this.client.sendCommand("UNAUTHENTICATE");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        Assertions.assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);

        this.client.sendCommand("LISTSCRIPTS");
        ManageSieveClient.ServerResponse loggedOutResponse = this.client.readResponse();
        Assertions.assertThat(loggedOutResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
    }

    // The server actually disconnects but isConnected still returns True.
    // Even when adding a delay, it still returns True.
    // There is probably something else broken with this test.
    @Disabled
    @Test
    void logoutShouldWorkInUnauthenticatedState() throws IOException, InterruptedException {
        this.client.sendCommand("LOGOUT");
        ManageSieveClient.ServerResponse response = this.client.readResponse();
        Assertions.assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        Assertions.assertThat(this.client.isConnected()).isFalse();
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
        Assertions.assertThat(response.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        Assertions.assertThat(this.client.isConnected()).isFalse();
    }

    void authenticatePlain() throws IOException {
        String initialClientResponse = "\0" + ManageSieveServerTestSystem.USERNAME.asString() + "\0" + ManageSieveServerTestSystem.PASSWORD;
        this.client.sendCommand("AUTHENTICATE \"PLAIN\" \"" + Base64.getEncoder().encodeToString(initialClientResponse.getBytes(StandardCharsets.UTF_8)) + "\"");
        ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
        Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        this.client.readResponse(); // Read capabilities
    }
}
