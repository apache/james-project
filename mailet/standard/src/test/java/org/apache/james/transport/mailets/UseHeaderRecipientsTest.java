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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UseHeaderRecipientsTest {

    private static final String RECIPIENT1 = "abc1@apache1.org";
    private static final String RECIPIENT2 = "abc2@apache2.org";
    private static final String RECIPIENT3 = "abc3@apache3.org";
    private UseHeaderRecipients testee;
    private FakeMailContext mailetContext;
    private MailAddress mailAddress1;
    private MailAddress mailAddress2;
    private MailAddress mailAddress3;

    @BeforeEach
    void setUp() throws Exception {
        testee = new UseHeaderRecipients();
        mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder().mailetContext(mailetContext).build());

        mailAddress1 = new MailAddress(RECIPIENT1);
        mailAddress2 = new MailAddress(RECIPIENT2);
        mailAddress3 = new MailAddress(RECIPIENT3);
    }

    @Test
    void serviceShouldSetMimeMessageRecipients() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(RECIPIENT1, RECIPIENT2))
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getRecipients())
            .containsOnly(mailAddress1, mailAddress2);
    }

    @Test
    void serviceShouldSetToCcAndBccSpecifiedInTheMimeMessage() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(RECIPIENT1)
                .addCcRecipient(RECIPIENT2)
                .addBccRecipient(RECIPIENT3))
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getRecipients())
            .containsOnly(mailAddress1, mailAddress2, mailAddress3);
    }

    @Test
    void serviceShouldSetEmptyRecipientWhenNoRecipientsInTheMimeMessage() throws Exception {

        FakeMail fakeMail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getRecipients())
            .isEmpty();
    }

    @Test
    void serviceShouldGhostEmail() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getState())
            .isEqualTo(Mail.GHOST);
    }

    @Test
    void serviceShouldResendTheEmail() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(RECIPIENT1)
                .addCcRecipient(RECIPIENT2)
                .addBccRecipient(RECIPIENT3))
            .build();

        testee.service(fakeMail);

        assertThat(mailetContext.getSentMails())
            .containsOnly(FakeMailContext.sentMailBuilder()
                .recipients(mailAddress1, mailAddress2, mailAddress3)
                .fromMailet()
                .build());
    }

    @Test
    void serviceShouldThrowOnInvalidMailAddress() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(mailAddress1)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("invalid"))
            .build();

        assertThatThrownBy(() -> testee.service(fakeMail)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void serviceShouldSupportAddressList() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(RECIPIENT1, RECIPIENT2))
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getRecipients())
            .containsOnly(mailAddress1, mailAddress2);
    }

    @Test
    void serviceShouldSupportMailboxes() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipients()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("APACHE" + "<" + UseHeaderRecipientsTest.RECIPIENT1 + ">"))
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getRecipients())
            .containsOnly(mailAddress1);
    }
}
