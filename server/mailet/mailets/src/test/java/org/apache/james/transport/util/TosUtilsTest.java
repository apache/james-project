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
package org.apache.james.transport.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class TosUtilsTest {
    private RedirectNotify mailet;
    private TosUtils testee;

    @BeforeEach
    void setup() {
        mailet = mock(RedirectNotify.class);
        testee = TosUtils.from(mailet);
    }

    @Test
    void getToShouldReturnEmptyWhenMailetToIsEmpty() throws Exception {
        when(mailet.getTo())
            .thenReturn(ImmutableList.<InternetAddress>of());

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> to = testee.getTo(fakeMail);

        assertThat(to).isEmpty();
    }

    @Test
    void getToShouldReturnEmptyWhenMailetToContainsOnlyUnaltered() throws Exception {
        when(mailet.getTo())
            .thenReturn(ImmutableList.of(SpecialAddress.UNALTERED.toInternetAddress().get()));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> to = testee.getTo(fakeMail);

        assertThat(to).isEmpty();
    }

    @Test
    void getToShouldReturnEmptyWhenMailetToContainsOnlyRecipients() throws Exception {
        when(mailet.getTo())
            .thenReturn(ImmutableList.of(SpecialAddress.RECIPIENTS.toInternetAddress().get()));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> to = testee.getTo(fakeMail);

        assertThat(to).isEmpty();
    }

    @Test
    void getToShouldReturnToWhenMailetToAreCommon() throws Exception {
        MailAddress mailAddress = new MailAddress("test", "james.org");
        MailAddress mailAddress2 = new MailAddress("test2", "james.org");
        when(mailet.getTo())
            .thenReturn(ImmutableList.of(mailAddress.toInternetAddress().get(), mailAddress2.toInternetAddress().get()));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> to = testee.getTo(fakeMail);

        assertThat(to).containsOnly(mailAddress, mailAddress2);
    }

    @Test
    void getToShouldReturnAddressesFromOriginalMailWhenMailetToAreSpecialAddresses() throws Exception {
        when(mailet.getTo())
            .thenReturn(ImmutableList.of(SpecialAddress.FROM.toInternetAddress().get(), SpecialAddress.TO.toInternetAddress().get()));

        MailAddress from = new MailAddress("from", "james.org");
        MailAddress toMailAddress = new MailAddress("to", "james.org");
        MailAddress toMailAddress2 = new MailAddress("to2", "james.org");
        FakeMail fakeMail = FakeMail.builder()
                .name("name")
                .sender(from)
                .recipients(toMailAddress, toMailAddress2)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient("to@james.org", "to2@james.org"))
                .build();

        List<MailAddress> to = testee.getTo(fakeMail);

        assertThat(to).containsOnly(from, toMailAddress, toMailAddress2);
    }
}
