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

package org.apache.james.jmap.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

public class VacationReplyTest {

    public static final String REASON = "I am in vacation dudes !";
    public static final String HTML_REASON = "<p>I am in vacation dudes !</p>";
    public static final String SUBJECT = "subject";

    private MailAddress originalSender;
    private MailAddress originalRecipient;
    private FakeMail mail;

    @Before
    public void setUp() throws Exception {
        originalSender = new MailAddress("distant@apache.org");
        originalRecipient = new MailAddress("benwa@apache.org");

        mail = new FakeMail();
        mail.setMessage(new MimeMessage(Session.getInstance(new Properties()) ,ClassLoader.getSystemResourceAsStream("spamMail.eml")));
        mail.setSender(originalSender);
    }

    @Test
    public void vacationReplyShouldGenerateASuitableAnswer() throws Exception {

        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(REASON)
                .htmlBody(HTML_REASON)
                .build())
            .receivedMailRecipient(originalRecipient)
            .build();

        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(REASON);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(HTML_REASON);
    }

    @Test
    public void vacationReplyShouldNotBeMultipartWhenVacationHaveNoHTML() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(REASON)
                .build())
            .receivedMailRecipient(originalRecipient)
            .build();

        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).isEqualTo(REASON);
    }

    @Test
    public void vacationReplyShouldAddReSuffixToSubjectByDefault() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(REASON)
                .build())
            .receivedMailRecipient(originalRecipient)
            .build();

        assertThat(vacationReply.getMimeMessage().getHeader("subject")).containsExactly("Re: Original subject");
    }

    @Test
    public void aUserShouldBeAbleToSetTheSubjectOfTheGeneratedMimeMessage() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(REASON)
                .subject(Optional.of(SUBJECT))
                .build())
            .receivedMailRecipient(originalRecipient)
            .build();

        assertThat(vacationReply.getMimeMessage().getHeader("subject")).containsExactly(SUBJECT);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullMail() {
        VacationReply.builder(null);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullOriginalEMailAddress() throws Exception {
        VacationReply.builder(new FakeMail())
            .receivedMailRecipient(null)
            .build();
    }

}
