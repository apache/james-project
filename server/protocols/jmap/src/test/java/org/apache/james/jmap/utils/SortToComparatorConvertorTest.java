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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MessageResultImpl;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SortToComparatorConvertorTest {

    private MailboxPath mailboxPath;
    private Entry firstMessage;
    private Entry secondMessage;
    private List<Entry> messages;

    @Before
    public void setup() throws IOException {
        LocalDate date = LocalDate.now();
        SimpleMailboxMessage firstMailboxMessage = new SimpleMailboxMessage(new Date(date.toEpochDay()), 0, 0, null, new Flags(), new PropertyBuilder(), null);
        mailboxPath = new MailboxPath("#private", "user", "path");
        firstMailboxMessage.setUid(1);
        firstMessage = new Entry(mailboxPath, new MessageResultImpl(firstMailboxMessage));
        SimpleMailboxMessage secondMailboxMessage = new SimpleMailboxMessage(new Date(date.plusDays(1).toEpochDay()), 0, 0, null, new Flags(), new PropertyBuilder(), null);
        secondMailboxMessage.setUid(2);
        secondMessage = new Entry(mailboxPath, new MessageResultImpl(secondMailboxMessage));
        messages = Lists.newArrayList(firstMessage, secondMessage);
    }

    @Test
    public void comparatorForShouldBeInitialOrderWhenEmptyList() {
        Comparator<Entry> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of());
        messages.sort(comparator);
        assertThat(messages).containsExactly(firstMessage, secondMessage);
    }

    @Test
    public void comparatorForShouldBeDescByDateWhenOnlyDateInList() {
        Comparator<Entry> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(secondMessage, firstMessage);
    }

    @Test
    public void comparatorForShouldBeDescByDateWhenOnlyDateDescInList() {
        Comparator<Entry> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date desc"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(secondMessage, firstMessage);
    }

    @Test
    public void comparatorForShouldBeAscByDateWhenOnlyDateAscInList() {
        Comparator<Entry> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date asc"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(firstMessage, secondMessage);
    }

    @Test
    public void comparatorForShouldChainComparatorsWhenOnlyMultipleElementInList() throws IOException {
        SimpleMailboxMessage thirdMailboxMessage = new SimpleMailboxMessage(secondMessage.getValue().getInternalDate(), 0, 0, null, new Flags(), new PropertyBuilder(), null);
        thirdMailboxMessage.setUid(3);
        Entry thirdMessage = new Entry(mailboxPath, new MessageResultImpl(thirdMailboxMessage));
        messages.add(thirdMessage);

        Comparator<Entry> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date asc", "id desc"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(firstMessage, thirdMessage, secondMessage);
    }

    @Test(expected=IllegalArgumentException.class)
    public void comparatorForShouldThrowWhenBadFieldFormat() {
        SortToComparatorConvertor.comparatorFor(ImmutableList.of("this is a bad field"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void comparatorForShouldThrowWhenEmptyField() {
        SortToComparatorConvertor.comparatorFor(ImmutableList.of(" "));
    }

    @Test(expected=IllegalArgumentException.class)
    public void comparatorForShouldThrowWhenUnknownField() {
        SortToComparatorConvertor.comparatorFor(ImmutableList.of("unknown"));
    }
    
    private static class Entry implements Map.Entry<MailboxPath, MessageResult> {
        
        private MailboxPath mailboxPath;
        private MessageResult messageResult;

        public Entry(MailboxPath mailboxPath, MessageResult messageResult) {
            this.mailboxPath = mailboxPath;
            this.messageResult = messageResult;
        }

        @Override
        public MailboxPath getKey() {
            return mailboxPath;
        }

        @Override
        public MessageResult getValue() {
            return messageResult;
        }

        @Override
        public MessageResult setValue(MessageResult messageResult) {
            this.messageResult = messageResult;
            return this.messageResult;
        }
    }
}
