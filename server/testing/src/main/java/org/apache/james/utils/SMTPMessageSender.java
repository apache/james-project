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

package org.apache.james.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.mail.Message;
import javax.mail.MessagingException;

import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.rules.ExternalResource;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Throwables;
import com.jayway.awaitility.core.ConditionFactory;

public class SMTPMessageSender extends ExternalResource implements Closeable {

    private static AuthenticatingSMTPClient createClient() {
        try {
            return new AuthenticatingSMTPClient();
        } catch (NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }
    }

    public static SMTPMessageSender noAuthentication(String ip, int port, String senderDomain) throws IOException {
        AuthenticatingSMTPClient smtpClient = createClient();
        smtpClient.connect(ip, port);
        return new SMTPMessageSender(smtpClient, senderDomain);
    }

    public static SMTPMessageSender authentication(String ip, int port, String senderDomain, String username, String password)
        throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient();
        smtpClient.connect(ip, port);
        if (smtpClient.auth(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN, username, password) == false) {
            throw new RuntimeException("auth failed");
        }
        return new SMTPMessageSender(smtpClient, senderDomain);
    }

    private final AuthenticatingSMTPClient smtpClient;
    private final String senderDomain;

    private SMTPMessageSender(AuthenticatingSMTPClient smtpClient, String senderDomain) {
        this.smtpClient = smtpClient;
        this.senderDomain = senderDomain;
    }

    public SMTPMessageSender(String senderDomain) {
        this(createClient(), senderDomain);
    }

    public SMTPMessageSender connect(String ip, int port) throws IOException {
        smtpClient.connect(ip, port);
        return this;
    }

    public SMTPMessageSender authenticate(String username, String password) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        if (smtpClient.auth(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN, username, password) == false) {
            throw new RuntimeException("auth failed");
        }
        return this;
    }

    public SMTPMessageSender sendMessage(String from, String recipient) {
        try {
            smtpClient.helo(senderDomain);
            smtpClient.setSender(from);
            smtpClient.rcpt("<" + recipient + ">");
            smtpClient.sendShortMessageData("FROM: " + from + "\r\n" +
                "subject: test\r\n" +
                "\r\n" +
                "content\r\n" +
                ".\r\n");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return this;
    }

    public SMTPMessageSender sendMessageNoBracket(String from, String recipient) {
        try {
            smtpClient.helo(senderDomain);
            smtpClient.setSender(from);
            smtpClient.rcpt(recipient);
            smtpClient.sendShortMessageData("FROM: " + from + "\r\n" +
                "subject: test\r\n" +
                "\r\n" +
                "content\r\n" +
                ".\r\n");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return this;
    }

    public SMTPMessageSender sendMessageWithHeaders(String from, String recipient, String message) {
        try {
            smtpClient.helo(senderDomain);
            smtpClient.setSender(from);
            smtpClient.rcpt("<" + recipient + ">");
            smtpClient.sendShortMessageData(message);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return this;
    }

    public SMTPMessageSender sendMessage(Mail mail) throws MessagingException {
        try {
            String from = mail.getSender().asString();
            smtpClient.helo(senderDomain);
            smtpClient.setSender(from);
            mail.getRecipients().stream()
                .map(MailAddress::asString)
                .forEach(Throwing.consumer(smtpClient::addRecipient));
            smtpClient.sendShortMessageData(asString(mail.getMessage()));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return this;
    }

    public SMTPMessageSender sendMessage(FakeMail.Builder mail) throws MessagingException {
        return sendMessage(mail.build());
    }

    private String asString(Message message) throws IOException, MessagingException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    public boolean messageHasBeenSent() throws IOException {
        return smtpClient.getReplyString()
            .contains("250 2.6.0 Message received");
    }

    public SMTPMessageSender awaitSent(ConditionFactory conditionFactory) {
        conditionFactory.until(this::messageHasBeenSent);
        return this;
    }

    public void awaitSentFail(ConditionFactory conditionFactory) {
        conditionFactory.until(this::messageSendingFailed);
    }

    public boolean messageSendingFailed() throws IOException {
        String replyString = smtpClient.getReplyString().trim();
        return replyString.startsWith("4") || replyString.startsWith("5");
    }

    public boolean messageHaveNotBeenSent() throws IOException {
        return !messageHasBeenSent();
    }

    @Override
    public void close() throws IOException {
        smtpClient.disconnect();
    }
}
