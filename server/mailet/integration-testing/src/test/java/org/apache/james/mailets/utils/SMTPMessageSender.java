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

package org.apache.james.mailets.utils;

import java.io.Closeable;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.commons.net.smtp.SMTPClient;

import com.google.common.base.Throwables;

public class SMTPMessageSender implements Closeable {

    public static SMTPMessageSender noAuthentication(String ip, int port, String senderDomain) throws IOException {
        SMTPClient smtpClient = new SMTPClient();
        smtpClient.connect(ip, port);
        return new SMTPMessageSender(smtpClient, senderDomain);
    }

    public static SMTPMessageSender authentication(String ip, int port, String senderDomain, String username, String password)
        throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient();
        smtpClient.connect(ip, port);
        smtpClient.auth(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN, username, password);
        return new SMTPMessageSender(smtpClient, senderDomain);
    }

    private final SMTPClient smtpClient;
    private final String senderDomain;

    private SMTPMessageSender(SMTPClient smtpClient, String senderDomain) {
        this.smtpClient = smtpClient;
        this.senderDomain = senderDomain;
    }

    public void sendMessage(String from, String recipient) {
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
    }

    public boolean messageHasBeenSent() throws IOException {
        return smtpClient.getReplyString()
            .contains("250 2.6.0 Message received");
    }

    @Override
    public void close() throws IOException {
        smtpClient.disconnect();
    }
}
