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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HasExceptionTest {

    private FakeMail mockedMail;
    private Matcher testee;

    @BeforeEach
    public void setUp() throws Exception {
        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mockedMail = MailUtil.createMockMail2Recipients(mimeMessage);
        testee = new HasException();
    }

    @Test
    public void matchShouldReturnAddressesWhenSpecifiedExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.ofSerializable(new AddressException())));

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.internet.AddressException")
                .build();

        testee.init(matcherConfig);

        assertThat(testee.match(mockedMail)).containsExactlyElementsOf(mockedMail.getRecipients());
    }

    @Test
    public void matchShouldReturnAddressesWhenSubclassOfSpecifiedExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.ofSerializable(new AddressException())));

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.MessagingException")
                .build();

        testee.init(matcherConfig);

        assertThat(testee.match(mockedMail)).containsExactlyElementsOf(mockedMail.getRecipients());
    }

    @Test
    public void matchShouldReturnEmptyWhenOtherExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.ofSerializable(new java.lang.RuntimeException())));

        testee.init(FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.MessagingException")
                .build());

        assertThat(testee.match(mockedMail)).isEmpty();
    }

    @Test
    public void matchShouldReturnEmptyWhenSuperclassOfSpecifiedExceptionHasOccurred() throws MessagingException {
        mockedMail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.ofSerializable(new javax.mail.MessagingException())));

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("javax.mail.internet.AddressException")
                .build();

        testee.init(matcherConfig);

        assertThat(testee.match(mockedMail)).isEmpty();
    }

    @Test
    public void matchShouldReturnEmptyWhenNoExceptionHasOccurred() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("java.lang.Exception")
                .build();

        testee.init(matcherConfig);

        assertThat(testee.match(mockedMail)).isEmpty();
    }

    @Test
    public void matchShouldReturnEmptyWhenNonExceptionIsAttached() throws MessagingException {
        mockedMail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.of(new java.lang.String())));

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("java.lang.Exception")
                .build();

        testee.init(matcherConfig);

        assertThat(testee.match(mockedMail)).isEmpty();
    }

    @Test
    public void initShouldRaiseMessagingExceptionWhenInvalidClassName() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("java.lang.InvalidClassName")
                .build();

        assertThatThrownBy(() -> testee.init(matcherConfig)).isInstanceOf(MessagingException.class);
    }

    @Test
    public void initShouldRaiseMessagingExceptionWhenClassNameIsNotException() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("java.lang.String")
                .build();

        assertThatThrownBy(() -> testee.init(matcherConfig)).isInstanceOf(MessagingException.class);
    }
    
    @Test
    public void initShouldRaiseMessagingExceptionWhenClassNameIsNotFullyQualified() throws MessagingException {
        mockedMail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.ofSerializable(new javax.mail.MessagingException())));

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasException")
                .condition("MessagingException")
                .build();

        assertThatThrownBy(() -> testee.init(matcherConfig)).isInstanceOf(MessagingException.class);
    }
}
