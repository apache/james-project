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

import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.droplists.memory.MemoryDropList;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IsInDropListTest {

    private static Matcher matcher;
    private static DropList dropList;
    private DropListEntry curentDroplistEntry;
    private static final String DENIED_SENDER = "attacker@evil.com";
    private static final String ALLOWED_SENDER = "allowed@allowed.com";
    private static final String OWNER_RECIPIENT = "owner@owner.com";
    private static final String NO_OWNER_RECIPIENT = "no_owner@noowner.com";

    @BeforeAll
    public static void setUp() throws MessagingException {
        dropList = new MemoryDropList();
        matcher = new IsInDropList(dropList);
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("IsInDropList")
            .build();
        matcher.init(matcherConfig);
    }

    @AfterEach
    public void cleanUpEach() {
        dropList.remove(curentDroplistEntry).block();
    }

    @ParameterizedTest
    @MethodSource("provideParametersForTest")
    void matchShouldMatchSenderFromDropListEmailsWhenOneRecipientAsOwner(DropListEntry dropListEntry) throws MessagingException {
        curentDroplistEntry = dropListEntry;
        dropList.add(curentDroplistEntry).block();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(DENIED_SENDER)
            .recipient(OWNER_RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideParametersForTwoRecipientTest")
    void matchShouldMatchSenderFromDropListEmailsWhenTwoRecipients(DropListEntry dropListEntry) throws MessagingException {
        curentDroplistEntry = dropListEntry;
        dropList.add(curentDroplistEntry).block();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(DENIED_SENDER)
            .recipient(OWNER_RECIPIENT)
            .recipient(NO_OWNER_RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).containsExactly(new MailAddress(NO_OWNER_RECIPIENT));
    }

    @ParameterizedTest
    @MethodSource("provideParametersForTest")
    void matchShouldNotMatchIfSenderNotFromDropListEmails(DropListEntry dropListEntry) throws MessagingException {
        curentDroplistEntry = dropListEntry;
        dropList.add(curentDroplistEntry).block();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(ALLOWED_SENDER)
            .recipient(OWNER_RECIPIENT)
            .recipient(NO_OWNER_RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).contains(new MailAddress(OWNER_RECIPIENT), new MailAddress(NO_OWNER_RECIPIENT));
    }

    static Stream<DropListEntry> getDropListTestEntries() throws AddressException {
        return Stream.of(
            DropListEntry.builder()
                .forAll()
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .forAll()
                .denyDomain(new MailAddress(DENIED_SENDER).getDomain())
                .build(),
            DropListEntry.builder()
                .domainOwner(new MailAddress(OWNER_RECIPIENT).getDomain())
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .domainOwner(new MailAddress(OWNER_RECIPIENT).getDomain())
                .denyDomain(new MailAddress(DENIED_SENDER).getDomain())
                .build(),
            DropListEntry.builder()
                .userOwner(new MailAddress(OWNER_RECIPIENT))
                .denyAddress(new MailAddress(DENIED_SENDER))
                .build(),
            DropListEntry.builder()
                .userOwner(new MailAddress(OWNER_RECIPIENT))
                .denyDomain(new MailAddress(DENIED_SENDER).getDomain())
                .build());
    }

    static Stream<Arguments> provideParametersForTest() throws AddressException {
        return getDropListTestEntries().map(Arguments::of);
    }

    static Stream<Arguments> provideParametersForTwoRecipientTest() throws AddressException {
        return getDropListTestEntries()
            .filter(dropListEntry -> !dropListEntry.getOwnerScope().equals(OwnerScope.GLOBAL))
            .map(Arguments::of);
    }
}