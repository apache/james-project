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

package org.apache.james.mailbox.store.mail.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ApplicableFlagCalculatorTest {

    @Test
    void constructorShouldThrowWhenNull() {
        assertThatThrownBy(() -> new ApplicableFlagCalculator(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void computeApplicableFlagsShouldReturnOnlyDefaultApplicableFlagsWhenNoMessage() {
        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(ImmutableList.<MailboxMessage>of());

        assertThat(calculator.computeApplicableFlags()).isEqualTo(getDefaultApplicableFlag());
    }

    @Test
    void computeApplicableFlagsShouldReturnOnlyDefaultApplicableFlagWhenNoMessageWithUserCustomFlag() {
        List<MailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags(Flag.ANSWERED)),
            createMessage(new Flags(Flag.DELETED)),
            createMessage(new Flags(Flag.USER)),
            createMessage(new Flags(Flag.RECENT)));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        assertThat(calculator.computeApplicableFlags()).isEqualTo(getDefaultApplicableFlag());
    }

    @Test
    void computeApplicableFlagsShouldReturnOnlyDefaultApplicableFlagAndAllUserCustomFlagUsedOneMessage() {
        List<MailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags("capture me")),
            createMessage(new Flags("french")));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        Flags expected = ApplicableFlagBuilder
            .builder()
            .add("capture me", "french")
            .build();

        assertThat(calculator.computeApplicableFlags()).isEqualTo(expected);
    }

    @Test
    void unionFlagsShouldAlwaysIgnoreRecentAndUser() {
        List<MailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags(Flag.RECENT)),
            createMessage(new Flags(Flag.USER)));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        Flags result = calculator.computeApplicableFlags();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains(Flag.RECENT)).isFalse();
            softly.assertThat(result.contains(Flag.USER)).isFalse();
        });
    }

    private MailboxMessage createMessage(Flags messageFlags) {
        String content = "Any content";
        int bodyStart = 10;

        return new SimpleMailboxMessage(new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId()), new Date(), content.length(), bodyStart,
            new ByteContent(content.getBytes()), messageFlags, new PropertyBuilder().build(), TestId.of(1));
    }

    private Flags getDefaultApplicableFlag() {
        return ApplicableFlagBuilder.builder().build();
    }
}