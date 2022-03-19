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

package org.apache.james.server.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

public class MimeMessageUtilTest {

    @Test
    public void testWriteMimeMessageMultipartWithMessageID() throws MessagingException, IOException {
        String message = "Received: from localhost.localdomain ([127.0.0.1])\r\n" +
        "          by athlon14 (JAMES SMTP Server 2.3-dev) with SMTP ID 694\r\n" +
        "          for <test_int1@athlon14.bf.loc>;\r\n" +
        "          Sat, 18 Feb 2006 19:30:53 +0100 (CET)\r\n" +
        "Subject: ext2int\r\n" +
        "X-James-Postage: This is a test mail sent by James Postage\r\n" +
        "Mime-Version: 1.0\r\n" +
        "Content-Type: multipart/alternative; boundary=\"XyoYyxCQIfmZ5Sxofid6XQVZt5Z09XtTnqBF4Z45XSA=\"\r\n" +
        "Date: Sat, 18 Feb 2006 19:30:53 +0100 (CET)\r\n" +
        "From: test_ext2@another.bf.loc\r\n" +
        "\r\n" +
        "\r\n" +
        "--XyoYyxCQIfmZ5Sxofid6XQVZt5Z09XtTnqBF4Z45XSA=\r\n" +
        "Content-Type: text/plain\r\n" +
        "\r\n" +
        "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\r\n" +
        "--XyoYyxCQIfmZ5Sxofid6XQVZt5Z09XtTnqBF4Z45XSA=\r\n" +
        "Content-Type: application/octet-stream\r\n" +
        "\r\n" +
        "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\r\n" +
        "--XyoYyxCQIfmZ5Sxofid6XQVZt5Z09XtTnqBF4Z45XSA=--\r\n";

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(message.getBytes()));
        mimeMessage.getSize();
        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        MimeMessageUtil.writeTo(mimeMessage, headerOut, bodyOut);
    }

}
