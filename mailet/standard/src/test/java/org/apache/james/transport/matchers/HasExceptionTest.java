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


package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Before;
import org.junit.Test;

public class HasExceptionTest {

    private FakeMail mockedMail;
    private Matcher matcher;

    @Before
    public void setUp() throws Exception {
        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mockedMail = MailUtil.createMockMail2Recipients(mimeMessage);
        matcher = new HasException();
    }

    @Test
    public void matchShouldReturnAddressesWhenSpecifiedExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(Mail.MAILET_ERROR_ATTRIBUTE_NAME, new javax.mail.internet.AddressException());

        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.internet.AddressException")
                .build();

        matcher.init(mci);

        assertThat(matcher.match(mockedMail)).containsAll(mockedMail.getRecipients());
    }

    @Test
    public void matchShouldReturnAddressesWhenSubclassOfSpecifiedExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(Mail.MAILET_ERROR_ATTRIBUTE_NAME, new javax.mail.internet.AddressException());

        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.MessagingException")
                .build();

        matcher.init(mci);

        assertThat(matcher.match(mockedMail)).containsAll(mockedMail.getRecipients());
    }

    @Test
    public void matchShouldReturnNullWhenOtherExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(Mail.MAILET_ERROR_ATTRIBUTE_NAME, new java.lang.RuntimeException());
        
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.MessagingException")
                .build());

        assertThat(matcher.match(mockedMail)).isNull();
    }

    @Test
    public void matchShouldReturnNullWhenSuperclassOfSpecifiedExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(Mail.MAILET_ERROR_ATTRIBUTE_NAME, new javax.mail.MessagingException());

        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.internet.AddressException")
                .build();

        matcher.init(mci);

        assertThat(matcher.match(mockedMail)).isNull();
    }
    
    @Test
    public void matchShouldReturnNullWhenNoExceptionHasOccurred() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("java.lang.Exception")
                .build());

        assertThat(matcher.match(mockedMail)).isNull();
    }

}
