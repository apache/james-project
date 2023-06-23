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

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class RecipientsUtilsTest {
    private RedirectNotify mailet;
    private RecipientsUtils testee;

    @BeforeEach
    void setup() {
        mailet = mock(RedirectNotify.class);
        testee = RecipientsUtils.from(mailet);
    }

    @Test
    void getRecipientsShouldReturnEmptyWhenMailetRecipientsIsEmpty() throws Exception {
        when(mailet.getRecipients())
            .thenReturn(ImmutableList.<MailAddress>of());

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> recipients = testee.getRecipients(fakeMail);

        assertThat(recipients).isEmpty();
    }

    @Test
    void getRecipientsShouldReturnEmptyWhenMailetRecipientsContainsOnlyUnaltered() throws Exception {
        when(mailet.getRecipients())
            .thenReturn(ImmutableList.of(SpecialAddress.UNALTERED));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> recipients = testee.getRecipients(fakeMail);

        assertThat(recipients).isEmpty();
    }

    @Test
    void getRecipientsShouldReturnEmptyWhenMailetRecipientsContainsOnlyRecipients() throws Exception {
        when(mailet.getRecipients())
            .thenReturn(ImmutableList.of(SpecialAddress.RECIPIENTS));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> recipients = testee.getRecipients(fakeMail);

        assertThat(recipients).isEmpty();
    }

    @Test
    void getRecipientsShouldReturnRecipientsWhenMailetRecipientsAreCommon() throws Exception {
        ImmutableList<MailAddress> expectedRecipients = ImmutableList.of(new MailAddress("test", "james.org"), new MailAddress("test2", "james.org"));
        when(mailet.getRecipients())
            .thenReturn(expectedRecipients);

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        List<MailAddress> recipients = testee.getRecipients(fakeMail);

        assertThat(recipients).containsOnly(expectedRecipients.toArray(new MailAddress[0]));
    }

    @Test
    void getRecipientsShouldReturnAddressesFromOriginalMailWhenMailetRecipientsAreSpecialAddresses() throws Exception {
        when(mailet.getRecipients())
            .thenReturn(ImmutableList.of(SpecialAddress.FROM, SpecialAddress.TO));

        MailAddress from = new MailAddress("from", "james.org");
        MailAddress to = new MailAddress("to", "james.org");
        MailAddress to2 = new MailAddress("to2", "james.org");
        FakeMail fakeMail = FakeMail.builder()
                .name("name")
                .sender(from)
                .recipients(to, to2)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(to.asString(), to2.asString())
                    .addFrom(from.asString())
                    .build())
                .build();

        List<MailAddress> recipients = testee.getRecipients(fakeMail);

        assertThat(recipients).containsOnly(from, to, to2);
    }
}
