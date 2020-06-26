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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class RecipientIsLocalTest {

    public static final String MATCHER_NAME = "matcherName";
    private RecipientIsLocal testee;
    private MailetContext mailetContext;
    private MailAddress mailAddress1;
    private MailAddress mailAddress2;
    private Mail mail;

    @BeforeEach
    public void setUp() throws Exception {
        mailetContext = mock(MailetContext.class);
        testee = new RecipientIsLocal();
        testee.init(FakeMatcherConfig.builder()
                .matcherName(MATCHER_NAME)
                .mailetContext(mailetContext)
                .build());

        mailAddress1 = new MailAddress("mail1@domain.com");
        mailAddress2 = new MailAddress("mail2@domain.com");
        mail = FakeMail.builder()
                .name("mail")
                .recipients(mailAddress1, mailAddress2)
                .build();
    }

    @Test
    public void matchShouldNotReturnNonExistingAddress() throws Exception {
        when(mailetContext.localRecipients(any())).thenReturn(ImmutableList.of());

        assertThat(testee.match(mail)).isEmpty();
    }

    @Test
    public void matchShouldNotReturnNonExistingAddressIfSomeRecipientsExists() throws Exception {
        when(mailetContext.localRecipients(any())).thenReturn(ImmutableList.of(mailAddress1));

        assertThat(testee.match(mail)).containsOnly(mailAddress1);
    }

    @Test
    public void matchShouldHandleTwoValidAddress() throws Exception {
        when(mailetContext.localRecipients(any())).thenReturn(ImmutableList.of(mailAddress1, mailAddress2));

        assertThat(testee.match(mail)).containsOnly(mailAddress1, mailAddress2);
    }

    @Test
    public void matchShouldNotMatchMailWithNoRecipient() throws Exception {
        assertThat(testee.match(FakeMail.defaultFakeMail())).isEmpty();
    }
}
