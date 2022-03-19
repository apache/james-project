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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.util.MimeMessageBodyGenerator;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.vacation.api.Vacation;
import org.apache.mailet.base.test.FakeMail;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;

public class VacationReplyTest {

    public static final String REASON = "I am in vacation dudes ! (plain text)";
    public static final String HTML_REASON = "<b>I am in vacation dudes !</b> (html text)";
    public static final String SUBJECT = "subject";

    private MimeMessageBodyGenerator mimeMessageBodyGenerator;
    private MailAddress originalSender;
    private MailAddress originalRecipient;
    private FakeMail mail;
    private MimeMessage generatedBody;

    @Before
    public void setUp() throws Exception {
        originalSender = new MailAddress("distant@apache.org");
        originalRecipient = new MailAddress("benwa@apache.org");

        mail = FakeMail.builder()
                .name("name")
                .mimeMessage(
                    MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("spamMail.eml")))
                .sender(originalSender)
                .build();

        HtmlTextExtractor htmlTextExtractor = mock(HtmlTextExtractor.class);
        when(htmlTextExtractor.toPlainText(any())).thenReturn("HTML");

        mimeMessageBodyGenerator = spy(new MimeMessageBodyGenerator(htmlTextExtractor));
        generatedBody = MimeMessageUtil.defaultMimeMessage();
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
            .build(mimeMessageBodyGenerator);

        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
    }

    @Test
    public void vacationReplyShouldAddReSuffixToSubjectByDefault() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .textBody(REASON)
                .build())
            .receivedMailRecipient(originalRecipient)
            .build(mimeMessageBodyGenerator);

        verify(mimeMessageBodyGenerator).from(argThat(createSubjectMatcher("Re: Original subject")), any(), any());
        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
    }

    @Test
    public void subjectShouldBeQEncodedWhenSpecialCharacters() throws Exception {
        VacationReply vacationReply = VacationReply.builder(mail)
            .vacation(Vacation.builder()
                .enabled(true)
                .subject(Optional.of("Nghiêm Thị Tuyết Nhung"))
                .textBody(REASON)
                .build())
            .receivedMailRecipient(originalRecipient)
            .build(mimeMessageBodyGenerator);

        assertThat(vacationReply.getMimeMessage().getHeader("subject")).containsOnly("=?UTF-8?Q?Nghi=C3=AAm_Th=E1=BB=8B_Tuy=E1=BA=BFt_Nhung?=");
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
            .build(mimeMessageBodyGenerator);

        verify(mimeMessageBodyGenerator).from(argThat(createSubjectMatcher(SUBJECT)), any(), any());
        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullMail() {
        VacationReply.builder(null);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullOriginalEMailAddress() throws Exception {
        VacationReply.builder(FakeMail.defaultFakeMail())
            .receivedMailRecipient(null);
    }

    private BaseMatcher<MimeMessage> createSubjectMatcher(final String expectedSubject) {
        return new BaseMatcher<MimeMessage>() {
            @Override
            public boolean matches(Object o) {
                MimeMessage mimeMessage = (MimeMessage) o;
                try {
                    return mimeMessage.getSubject().equals(expectedSubject);
                } catch (MessagingException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void describeTo(Description description) {

            }
        };
    }
}
