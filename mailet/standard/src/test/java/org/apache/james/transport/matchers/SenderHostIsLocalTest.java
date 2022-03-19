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

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES2;
import static org.apache.mailet.base.MailAddressFixture.JAMES2_APACHE_ORG;
import static org.apache.mailet.base.MailAddressFixture.JAMES_APACHE_ORG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SenderHostIsLocalTest {

    private Matcher matcher;
    
    @BeforeEach
    public void setUp() throws MessagingException {
        MailetContext mailContext = mock(MailetContext.class);
        when(mailContext.isLocalServer(Domain.of(JAMES_APACHE_ORG))).thenReturn(true);
        when(mailContext.isLocalServer(Domain.of(JAMES2_APACHE_ORG))).thenReturn(false);
        
        matcher = new SenderHostIsLocal();
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("SenderHostIsLocal")
                .mailetContext(mailContext)
                .build();
        
        matcher.init(mci);
    }

    @Test
    public void shouldMatchWhenSenderHostIsLocal() throws MessagingException {
        //Given
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(ANY_AT_JAMES)
            .recipient(ANY_AT_JAMES2)
            .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).containsExactly(ANY_AT_JAMES2);
    }
    
    @Test
    public void shouldNotMatchWhenSenderHostIsNotLocal() throws MessagingException {
        //Given
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(ANY_AT_JAMES2)
            .recipient(ANY_AT_JAMES)
            .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test
    public void shouldNotMatchWhenNullSender() throws MessagingException {
        //Given
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(MailAddress.nullSender())
            .recipient(ANY_AT_JAMES)
            .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test
    public void shouldNotMatchWhenNoSender() throws MessagingException {
        //Given
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

}
