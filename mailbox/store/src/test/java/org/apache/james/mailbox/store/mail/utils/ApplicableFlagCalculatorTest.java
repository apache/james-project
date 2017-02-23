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

import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class ApplicableFlagCalculatorTest {

    private static final String USER_FLAGS_VALUE = "UserFlags";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void constructorShouldThrowWhenNull() throws Exception {
        expectedException.expect(NullPointerException.class);
        new ApplicableFlagCalculator(null);
    }

    @Test
    public void unionFlagsShouldWelWhenEmpty() throws Exception {
        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(ImmutableList.<MailboxMessage>of());

        assertThat(calculator.computeApplicableFlags()).isEqualTo(new Flags());
    }

    @Test
    public void unionFlagsShouldUnionAllMessageFlagsExceptRecentAndUser() throws Exception {
        List<MailboxMessage> mailboxMessages = ImmutableList.of(
            createMessage(new Flags(Flag.ANSWERED)),
            createMessage(new Flags(Flag.DELETED)),
            createMessage(new Flags(Flag.USER)),
            createMessage(new Flags(Flag.RECENT)),
            createMessage(new FlagsBuilder().add(Flag.ANSWERED)
                .add(USER_FLAGS_VALUE)
                .build()));

        ApplicableFlagCalculator calculator = new ApplicableFlagCalculator(mailboxMessages);

        assertThat(calculator.computeApplicableFlags()).isEqualTo(new FlagsBuilder()
            .add(Flag.ANSWERED, Flag.DELETED)
            .add(USER_FLAGS_VALUE)
            .build());
    }

    private MailboxMessage createMessage(Flags messageFlags) {
        String content = "Any content";
        int bodyStart = 10;
        return new SimpleMailboxMessage(new DefaultMessageId(), new Date(), content.length(), bodyStart,
            new SharedByteArrayInputStream(content.getBytes()), messageFlags, new PropertyBuilder(), TestId.of(1));
    }

}