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

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class TooManyRecipientsTest {

    private TooManyRecipients testee;

    @BeforeEach
    void setUp() {
        testee = new TooManyRecipients();
    }

    @Test
    void initShouldThrowOnAbsentCondition() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .matcherName("matcherName")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnInvalidCondition() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .condition("a")
                .matcherName("matcherName")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnEmptyCondition() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .condition("")
                .matcherName("matcherName")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnZeroCondition() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .condition("0")
                .matcherName("matcherName")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnNegativeCondition() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .condition("-10")
                .matcherName("matcherName")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void matchShouldReturnNoRecipientWhenMailHaveNoRecipient() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .condition("3")
            .matcherName("matcherName")
            .build());

        Collection<MailAddress> result = testee.match(FakeMail.builder().name("mail").build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldAcceptMailsUnderLimit() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .condition("3")
            .matcherName("matcherName")
            .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient("cuong.tran@gmail.com")
            .build();

        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEmpty();
    }


    @Test
    void matchShouldAcceptMailsAtLimit() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .condition("3")
            .matcherName("matcherName")
            .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients("cuong.tran@gmail.com", "suu.tran@gmail.com", "tuan.tran@gmail.com")
            .build();

        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldRejectMailsOverLimit() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .condition("3")
            .matcherName("matcherName")
            .build());

        ImmutableList<MailAddress> mailAddresses = ImmutableList.of(
            new MailAddress("cuong.tran@gmail.com"),
            new MailAddress("suu.tran@gmail.com"),
            new MailAddress("tuan.tran@gmail.com"),
            new MailAddress("sang.tran@gmail.com"));


        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(mailAddresses)
            .build();

        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEqualTo(mailAddresses);
    }

}
