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
import org.apache.james.util.Port;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.rules.ExternalResource;

import com.github.fge.lambdas.Throwing;

public class SMTPMessageSender extends ExternalResource implements Closeable {

    public static SMTPMessageSender noAuthentication(String ip, int port, String senderDomain) throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient();
        smtpClient.connect(ip, port);
        return new SMTPMessageSender(smtpClient, senderDomain);
    }

    public static SMTPMessageSender authentication(String ip, int port, String senderDomain, String username, String password)
        throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient();
        smtpClient.connect(ip, port);
        if (!smtpClient.auth(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN, username, password)) {
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
        this(new AuthenticatingSMTPClient(), senderDomain);
    }

    public SMTPMessageSender connect(String ip, Port port) throws IOException {
        smtpClient.connect(ip, port.getValue());
        return this;
    }

    public SMTPMessageSender authenticate(String username, String password) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        if (!smtpClient.auth(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN, username, password)) {
            throw new SMTPSendingException(SmtpSendingStep.Authentication, smtpClient.getReplyString());
        }
        return this;
    }

    public SMTPMessageSender sendMessage(String from, String recipient) throws IOException {
        doHelo();
        doSetSender(from);
        doRCPT("<" + recipient + ">");
        doData("FROM: " + from + "\r\n" +
            "subject: test\r\n" +
            "\r\n" +
            "content\r\n" +
            ".\r\n");
        return this;
    }

    public SMTPMessageSender sendMessageNoBracket(String from, String recipient) throws IOException {
        doHelo();
        doSetSender(from);
        doRCPT(recipient);
        doData("FROM: " + from + "\r\n" +
            "subject: test\r\n" +
            "\r\n" +
            "content\r\n" +
            ".\r\n");
        return this;
    }

    public SMTPMessageSender sendMessageWithHeaders(String from, String recipient, String message) throws IOException {
        doHelo();
        doSetSender(from);
        doRCPT("<" + recipient + ">");
        doData(message);
        return this;
    }

    public SMTPMessageSender sendMessage(Mail mail) throws MessagingException, IOException {
        String from = mail.getMaybeSender().asString();
        doHelo();
        doSetSender(from);
        mail.getRecipients().stream()
            .map(MailAddress::asString)
            .forEach(Throwing.consumer(this::doAddRcpt));
        doData(asString(mail.getMessage()));
        return this;
    }

    public SMTPMessageSender sendMessage(FakeMail.Builder mail) throws MessagingException, IOException {
        return sendMessage(mail.build());
    }

    private String asString(Message message) throws IOException, MessagingException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        smtpClient.disconnect();
    }

    private void doSetSender(String from) throws IOException {
        boolean success = smtpClient.setSender(from);
        if (!success) {
            throw new SMTPSendingException(SmtpSendingStep.Sender, smtpClient.getReplyString());
        }
    }

    private void doHelo() throws IOException {
        int code = smtpClient.helo(senderDomain);
        if (code != 250) {
            throw new SMTPSendingException(SmtpSendingStep.Helo, smtpClient.getReplyString());
        }
    }

    private void doRCPT(String recipient) throws IOException {
        int code = smtpClient.rcpt(recipient);
        if (code != 250) {
            throw new SMTPSendingException(SmtpSendingStep.RCPT, smtpClient.getReplyString());
        }
    }

    private void doData(String message) throws IOException {
        boolean success = smtpClient.sendShortMessageData(message);
        if (!success) {
            throw new SMTPSendingException(SmtpSendingStep.Data, smtpClient.getReplyString());
        }
    }

    private void doAddRcpt(String rcpt) throws IOException {
        boolean success = smtpClient.addRecipient(rcpt);
        if (!success) {
            throw new SMTPSendingException(SmtpSendingStep.RCPT, smtpClient.getReplyString());
        }
    }
}
