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

import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class RelayLimitTest {

    private RelayLimit testee;
    private MailAddress mailAddress;
    private Mail mail;
    private MimeMessage mimeMessage;

    @Before
    public void setUp() throws Exception {
        testee = new RelayLimit();
        mailAddress = new MailAddress("mail@domain.com");
        mail = new FakeMail();
        mail.setRecipients(ImmutableList.of(mailAddress));
        mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mail.setMessage(mimeMessage);
    }

    @Test(expected = MessagingException.class)
    public void relayLimitShouldBeANumber() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=Abc", new FakeMailContext()));
    }

    @Test(expected = MessagingException.class)
    public void relayLimitShouldBeSpecified() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=", new FakeMailContext()));
    }

    @Test(expected = MessagingException.class)
    public void relayLimitShouldNotBeNull() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=0", new FakeMailContext()));
    }

    @Test(expected = MessagingException.class)
    public void relayLimitShouldNotBeEqualToZero() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=-1", new FakeMailContext()));
    }

    @Test
    public void matchShouldReturnNullWhenNoReceivedHeader() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=2", new FakeMailContext()));

        assertThat(testee.match(mail)).isNull();
    }

    @Test
    public void matchShouldReturnNullWhenNotEnoughReceivedHeader() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=2", new FakeMailContext()));

        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");

        assertThat(testee.match(mail)).isNull();
    }

    @Test
    public void matchShouldReturnAddressWhenEqualToLimit() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=2", new FakeMailContext()));

        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");
        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");

        assertThat(testee.match(mail)).containsExactly(mailAddress);
    }

    @Test
    public void matchShouldReturnAddressWhenOverLimit() throws Exception {
        testee.init(new FakeMatcherConfig("RelayLimit=2", new FakeMailContext()));

        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");
        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");
        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");

        assertThat(testee.match(mail)).containsExactly(mailAddress);
    }

}
