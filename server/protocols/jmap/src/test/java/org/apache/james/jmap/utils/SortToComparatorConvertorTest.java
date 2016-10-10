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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageResult;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SortToComparatorConvertorTest {
    private static final Date DATE_2017 = Date.from(ZonedDateTime.parse("2017-10-09T08:07:06+07:00[Asia/Vientiane]").toInstant());
    private static final Date DATE_2018 = Date.from(ZonedDateTime.parse("2018-10-09T08:07:06+07:00[Asia/Vientiane]").toInstant());

    private MailboxPath mailboxPath;
    private Entry firstMessage;
    private Entry secondMessage;
    private List<Entry> messages;

    @Before
    public void setup() throws IOException {
        MessageResult firstMessageResult = mock(MessageResult.class);
        when(firstMessageResult.getInternalDate()).thenReturn(DATE_2017);
        when(firstMessageResult.getUid()).thenReturn(MessageUid.of(1));
        firstMessage = new Entry(mailboxPath, firstMessageResult);
        MessageResult secondMessageResult = mock(MessageResult.class);
        when(secondMessageResult.getInternalDate()).thenReturn(DATE_2018);
        when(secondMessageResult.getUid()).thenReturn(MessageUid.of(2));
        secondMessage = new Entry(mailboxPath, secondMessageResult);
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
        MessageResult thirdMessageResult = mock(MessageResult.class);
        when(thirdMessageResult.getInternalDate()).thenReturn(DATE_2018);
        when(thirdMessageResult.getUid()).thenReturn(MessageUid.of(3));
        Entry thirdMessage = new Entry(mailboxPath, thirdMessageResult);
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
