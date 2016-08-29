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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.mail.MessagingException;

import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class HostIsLocalTest {

    public static final String JAMES_APACHE_ORG = "james.apache.org";
    public static final String JAMES2_APACHE_ORG = "james2.apache.org";

    private FakeMail mockedMail;
    private Matcher matcher;

    @Before
    public void setUp() throws Exception {
        mockedMail = new FakeMail();

        MailetContext mailContext = mock(MailetContext.class);
        when(mailContext.isLocalServer(JAMES_APACHE_ORG)).thenReturn(true);
        when(mailContext.isLocalServer(JAMES2_APACHE_ORG)).thenReturn(false);

        matcher = new HostIsLocal();
        FakeMatcherConfig mci = new FakeMatcherConfig("HostIsLocal", mailContext);
        matcher.init(mci);
    }

    // test if all recipients get returned as matched
    @Test
    public void testHostIsMatchedAllRecipients() throws MessagingException {
        MailAddress mailAddress1 = new MailAddress("test@" + JAMES_APACHE_ORG);
        MailAddress mailAddress2 = new MailAddress("test2@" + JAMES_APACHE_ORG);
        mockedMail.setRecipients(ImmutableList.of(
            mailAddress1,
            mailAddress2));

        assertThat( matcher.match(mockedMail)).containsExactly(mailAddress1, mailAddress2);
    }

    // test if one recipients get returned as matched
    @Test
    public void testHostIsMatchedOneRecipient() throws MessagingException {
        MailAddress matchingAddress = new MailAddress("test2@" +JAMES_APACHE_ORG);
        mockedMail.setRecipients(ImmutableList.of(
            new MailAddress("test@" + JAMES2_APACHE_ORG),
            matchingAddress));

        assertThat( matcher.match(mockedMail)).containsExactly(matchingAddress);
    }

    // test if no recipient get returned cause it not match
    @Test
    public void testHostIsNotMatch() throws MessagingException {
        mockedMail.setRecipients(
            ImmutableList.of(new MailAddress("test@" + JAMES2_APACHE_ORG),
            new MailAddress("test2@" + JAMES2_APACHE_ORG)));

        assertThat(matcher.match(mockedMail)).isEmpty();
    }
}
