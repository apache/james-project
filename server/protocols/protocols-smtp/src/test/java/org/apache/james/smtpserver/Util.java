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

import java.util.concurrent.ThreadLocalRandom;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;

/**
 * some utilities for James unit testing
 */
public class Util {

    public static Mail createMockMail2Recipients(MimeMessage m) throws MessagingException {
        return FakeMail.builder()
            .name("ID=" + ThreadLocalRandom.current().nextLong())
            .mimeMessage(m)
            .recipients("test@james.apache.org", "test2@james.apache.org")
            .build();
    }

    public static MimeMessage createMimeMessage(String headerName, String headerValue) throws MessagingException {
        String sender = "test@james.apache.org";
        String rcpt = "test2@james.apache.org";
        return MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(headerName, headerValue)
            .setSubject("testmail")
            .setText("testtext")
            .addToRecipient(rcpt)
            .addFrom(sender)
            .build();
    }
}
