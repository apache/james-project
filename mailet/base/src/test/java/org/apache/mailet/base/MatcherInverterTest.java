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

package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

class MatcherInverterTest {

    @Test
    void testAllMatch() throws MessagingException {
        MatcherInverter inverter = new MatcherInverter(new GenericMatcher() {
            @Override
            public Collection<MailAddress> match(Mail mail) throws MessagingException {
                return null;
            }
        });

        FakeMail mail = FakeMail.builder()
                .name("mail")
                .recipient(new MailAddress("user", "domain"))
                .build();

        assertThat(inverter.match(mail)).withFailMessage("Should match all recipients").isNotNull();
    }

    @Test
    void testNonMatch() throws MessagingException {
        final MailAddress address1 = new MailAddress("user", "domain");
        final MailAddress address2 = new MailAddress("user", "domain2");

        MatcherInverter inverter = new MatcherInverter(new GenericMatcher() {

            @Override
            public Collection<MailAddress> match(Mail mail) throws MessagingException {
                return mail.getRecipients();
            }
        });

        FakeMail mail = FakeMail.builder()
                .name("mail")
                .recipients(address1, address2)
                .build();

        assertThat(inverter.match(mail)).withFailMessage("Should match all recipients").isNull();
    }

    @Test
    void testOneMatch() throws MessagingException {
        MailAddress errorRecipient = new MailAddress("error@domain.tld");
        MailAddress validRecipient = new MailAddress("valid@domain.tld");

        // e.g. <mailet notMatch="RecipientIs=error@domain.tld" class="org.apache.james.Whatever"/>
        MatcherInverter notErrorRecipientMatcher = new MatcherInverter(new GenericMatcher() {
            @Override
            public Collection<MailAddress> match(Mail mail) {
                return Arrays.asList(errorRecipient);
            }
        });

        FakeMail mailToBothValidAndErrorRecipient = FakeMail.builder()
            .name("mail")
            .recipients(validRecipient, errorRecipient)
            .build();

        assertThat(notErrorRecipientMatcher.match(mailToBothValidAndErrorRecipient))
            .describedAs("Only valid address should not match the error recipient")
            .containsOnly(validRecipient);
    }
}
