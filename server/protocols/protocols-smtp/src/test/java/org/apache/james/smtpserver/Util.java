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

import java.util.Arrays;
import java.util.Random;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.smtpserver.mock.mailet.MockMail;

/**
 * some utilities for James unit testing
 */
public class Util {

    private static final Random RANDOM = new Random();

    public static MockMail createMockMail2Recipients(MimeMessage m) throws ParseException {
        MockMail mockedMail = new MockMail();
        mockedMail.setName("ID=" + RANDOM.nextLong());
        mockedMail.setMessage(m);
        mockedMail.setRecipients(Arrays.asList(new MailAddress("test@james.apache.org"),
                new MailAddress("test2@james.apache.org")));
        return mockedMail;
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
