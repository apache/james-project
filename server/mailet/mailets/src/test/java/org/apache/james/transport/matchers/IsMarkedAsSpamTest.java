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

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IsMarkedAsSpamTest {
    private IsMarkedAsSpam matcher;

    @BeforeEach
    void setup() {
        matcher = new IsMarkedAsSpam();
    }

    @Test
    void isMarkedAsSpamShouldNotMatchWhenNoHeader() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipient("to@james.org")
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).isEmpty();
    }

    @Test
    void isMarkedAsSpamShouldNotMatchWhenHeaderButEmptyValue() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipient("to@james.org")
            .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("org.apache.james.spamassassin.status")
                    .value("other")
                    .build(),
                new MailAddress("to@james.org"))
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).isEmpty();
    }

    @Test
    void isMarkedAsSpamShouldNotMatchWhenHeaderButOtherValue() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipient("to@james.org")
            .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("org.apache.james.spamassassin.status")
                    .value("other")
                    .build(),
                new MailAddress("to@james.org"))
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).isEmpty();
    }

    @Test
    void isMarkedAsSpamShouldNotMatchWhenHeaderButNoValue() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipient("to@james.org")
            .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("org.apache.james.spamassassin.status")
                    .value("No, hits=1.8 required=5.0")
                    .build(),
                new MailAddress("to@james.org"))
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).isEmpty();
    }

    @Test
    void isMarkedAsSpamShouldMatchWhenHeaderAndYesValue() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipient("to@james.org")
            .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("org.apache.james.spamassassin.status")
                    .value("Yes, hits=6.8 required=5.0")
                    .build(),
                new MailAddress("to@james.org"))
            .attribute(Attribute.convertToAttribute("org.apache.james.spamassassin.status", "Yes, hits=6.8 required=5.0"))
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).contains(new MailAddress("to@james.org"));
    }

    @Test
    void isMarkedAsSpamShouldMatchOnlyRecipientsWithHeaderAndYesValue() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipients("to1@james.org", "to2@james.org")
            .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("org.apache.james.spamassassin.status")
                    .value("Yes, hits=6.8 required=5.0")
                    .build(),
                new MailAddress("to1@james.org"))
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).contains(new MailAddress("to1@james.org"));
    }

    @Test
    void isMarkedAsSpamShouldMatchWhenHeaderAndYesValueInOtherCase() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender("sender@james.org")
            .recipient("to@james.org")
            .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("org.apache.james.spamassassin.status")
                    .value("YES, hits=6.8 required=5.0")
                    .build(),
                new MailAddress("to@james.org"))
            .build();

        Collection<MailAddress> matches = matcher.match(mail);
        assertThat(matches).contains(new MailAddress("to@james.org"));
    }
}
