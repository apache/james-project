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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Optional;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SenderHeaderVerificationHookTest {
    private static final Username BOB = Username.of("bob@example.local");
    private static final Username ALICE = Username.of("alice@example.local");
    private static final String PASSWORD = "secret";
    private static final String REJECTION = "550 5.7.1 Sender header must match the authenticated user";

    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @AfterEach
    void tearDown() {
        if (testSystem.smtpServer != null) {
            testSystem.smtpServer.destroy();
        }
    }

    private void start(String configuration) throws Exception {
        testSystem.setUp(configuration);
        testSystem.usersRepository.addUser(BOB, PASSWORD);
        testSystem.usersRepository.addUser(ALICE, PASSWORD);
    }

    private SMTPClient connect() throws IOException {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");
        return smtpProtocol;
    }

    private SMTPClient authenticate(String authorizationId, String authenticationId, String password) throws IOException {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString((authorizationId + "\0" + authenticationId + "\0" + password).getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
        return smtpProtocol;
    }

    private SMTPClient authenticateAsBob() throws IOException {
        return authenticate("", BOB.asString(), PASSWORD);
    }

    private void send(SMTPClient smtpProtocol, String mailFrom, String recipient, String headers) throws IOException {
        smtpProtocol.setSender(mailFrom);
        smtpProtocol.addRecipient(recipient);
        smtpProtocol.sendShortMessageData(headers + "Subject: test\r\n\r\nTest body\r\n");
    }

    private void assertAccepted(SMTPClient smtpProtocol) {
        assertThat(smtpProtocol.getReplyCode())
            .as(smtpProtocol.getReplyString())
            .isEqualTo(250);
        assertThat(testSystem.queue.getLastMail()).isNotNull();
    }

    private void assertRejected(SMTPClient smtpProtocol) {
        assertThat(smtpProtocol.getReplyString()).startsWith(REJECTION);
        assertThat(testSystem.queue.getLastMail()).isNull();
    }

    @Test
    void senderBeingTheAuthenticatedUserShouldBeAccepted() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: Bob <" + BOB.asString() + ">\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void senderShouldBeCaseInsensitive() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: BOB@Example.Local\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void mailWithoutSenderShouldBeAccepted() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com", "From: " + BOB.asString() + "\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void aliasOfTheAuthenticatedUserShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        String alias = "bob.alias@example.local";
        testSystem.rewriteTable.addAliasMapping(MappingSource.fromUser(Username.of(alias)), BOB.asString());
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, alias, "rcpt@example.com", "From: " + alias + "\r\nSender: " + alias + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void otherUserShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + ALICE.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void additionalAddressInSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + ", " + ALICE.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void secondSenderFieldShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + "\r\nSender: " + ALICE.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void foldedSenderShouldBeAccepted() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: Bob\r\n <" + BOB.asString() + ">\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void groupInSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: team: " + BOB.asString() + ";\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void groupNamedLikeAHeaderInSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: To: " + BOB.asString() + ";\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void unterminatedCommentInSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + " (\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void trailingCommentInSenderShouldBeAccepted() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + " (Bob)\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void addressAfterAnAngleAddressInSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: <" + BOB.asString() + "> " + ALICE.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void addressAfterACommentInSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + " (comment) " + ALICE.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void commentWithinTheDomainOfSenderShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + "(comment).evil\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void quotedLocalPartHoldingAnAtShouldBeRejected() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = authenticateAsBob();

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: \"" + BOB.asString() + "\"@example.local\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void unauthenticatedSessionsShouldNotBeChecked() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        SMTPClient smtpProtocol = connect();

        send(smtpProtocol, "someone@remote.tld", BOB.asString(),
            "From: someone@remote.tld\r\nSender: " + ALICE.asString() + "\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void authenticatedUserShouldBeExpectedUponDelegation() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        // Bob authenticates and acts as Alice
        SMTPClient smtpProtocol = authenticate(ALICE.asString(), BOB.asString(), PASSWORD);

        send(smtpProtocol, ALICE.asString(), "rcpt@example.com",
            "From: " + ALICE.asString() + "\r\nSender: " + BOB.asString() + "\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void delegatingUserShouldBeRejectedUponDelegation() throws Exception {
        start("smtpserver-sender-header-verification.xml");
        // Bob authenticates and acts as Alice
        SMTPClient smtpProtocol = authenticate(ALICE.asString(), BOB.asString(), PASSWORD);

        send(smtpProtocol, ALICE.asString(), "rcpt@example.com",
            "From: " + ALICE.asString() + "\r\nSender: " + ALICE.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    @Test
    void accountAllowedToUseOtherIdentitiesShouldNotBeChecked() throws Exception {
        start("smtpserver-sender-header-verification-other-identity.xml");
        SMTPClient smtpProtocol = authenticate("", "noreply-tcalendar@domain.tld", "secret234567");

        send(smtpProtocol, BOB.asString(), "rcpt@example.com",
            "From: " + BOB.asString() + "\r\nSender: " + BOB.asString() + "\r\n");

        assertAccepted(smtpProtocol);
    }

    @Test
    void accountNotAllowedToUseOtherIdentitiesShouldBeChecked() throws Exception {
        start("smtpserver-sender-header-verification-other-identity.xml");
        SMTPClient smtpProtocol = authenticate("", "noreply-tdrive@domain.tld", "secret123456");

        send(smtpProtocol, "", "rcpt@example.com",
            "From: noreply-tdrive@domain.tld\r\nSender: " + BOB.asString() + "\r\n");

        assertRejected(smtpProtocol);
    }

    private static SMTPSession sessionOfBob() {
        SMTPSession session = mock(SMTPSession.class);
        when(session.getUsername()).thenReturn(BOB);
        when(session.getAttachment(any(), any(ProtocolSession.State.class))).thenReturn(Optional.empty());
        return session;
    }

    private static Mail mailWithBobAsSender() throws Exception {
        return MailImpl.builder()
            .name("mail")
            .sender(BOB.asString())
            .addRecipient("rcpt@example.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(BOB.asString())
                .addHeader("Sender", BOB.asString())
                .setText("body"))
            .build();
    }

    @Test
    void senderOfARemoteDomainShouldBeRejectedEvenWhenResolvingToTheUser() throws Exception {
        // Without virtual hosting, getUsername drops the domain
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.getUsername(any(MailAddress.class))).thenReturn(BOB);
        DomainList domainList = mock(DomainList.class);
        when(domainList.containsDomain(any())).thenReturn(false);

        HookResult result = new SenderHeaderVerificationHook(usersRepository, domainList).onMessage(sessionOfBob(), mailWithBobAsSender());

        assertThat(result.getResult()).isEqualTo(HookReturnCode.deny());
        assertThat(result.getSmtpRetCode()).isEqualTo("550");
    }

    @Test
    void usersRepositoryFailuresShouldBeTemporary() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.getUsername(any(MailAddress.class))).thenThrow(new UsersRepositoryException("boom"));
        DomainList domainList = mock(DomainList.class);
        when(domainList.containsDomain(any())).thenReturn(true);

        HookResult result = new SenderHeaderVerificationHook(usersRepository, domainList).onMessage(sessionOfBob(), mailWithBobAsSender());

        assertThat(result.getResult()).isEqualTo(HookReturnCode.denySoft());
        assertThat(result.getSmtpRetCode()).isEqualTo("451");
    }

    @Test
    void domainListFailuresShouldBeTemporary() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.getUsername(any(MailAddress.class))).thenReturn(BOB);
        DomainList domainList = mock(DomainList.class);
        when(domainList.containsDomain(any())).thenThrow(new DomainListException("boom"));

        HookResult result = new SenderHeaderVerificationHook(usersRepository, domainList).onMessage(sessionOfBob(), mailWithBobAsSender());

        assertThat(result.getResult()).isEqualTo(HookReturnCode.denySoft());
        assertThat(result.getSmtpRetCode()).isEqualTo("451");
    }
}
