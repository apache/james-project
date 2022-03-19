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

package org.apache.james.queue.api;

import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.SENDER;

import java.util.Date;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.base.test.FakeMail;

public interface Mails {

    static FakeMail.RequireName defaultMail() {
        return name -> defaultMailNoRecipient()
            .name(name)
            .recipients(RECIPIENT1, RECIPIENT2);
    }

    static FakeMail.RequireName defaultMailNoRecipient() {
        return name -> FakeMail.builder()
                .name(name)
                .mimeMessage(createMimeMessage())
                .sender(SENDER)
                .lastUpdated(new Date());
    }

    static MimeMessage createMimeMessage() {
        try {
            return MimeMessageBuilder.mimeMessageBuilder()
                .setText("test")
                .addHeader("testheader", "testvalue")
                .build();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
