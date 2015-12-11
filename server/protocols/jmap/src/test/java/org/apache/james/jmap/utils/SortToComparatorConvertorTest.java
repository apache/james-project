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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SortToComparatorConvertorTest {

    private List<SimpleMessage<TestId>> messages;
    private SimpleMessage<TestId> firstMessage;
    private SimpleMessage<TestId> secondMessage;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        LocalDate date = LocalDate.now();
        firstMessage = new SimpleMessage<TestId>(new Date(date.toEpochDay()), 0, 0, null, new Flags(), new PropertyBuilder(), null);
        firstMessage.setUid(1);
        secondMessage = new SimpleMessage<TestId>(new Date(date.plusDays(1).toEpochDay()), 0, 0, null, new Flags(), new PropertyBuilder(), null);
        secondMessage.setUid(2);
        messages = Lists.newArrayList(firstMessage, secondMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void comparatorForShouldBeInitialOrderWhenEmptyList() {
        Comparator<SimpleMessage<TestId>> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of());
        messages.sort(comparator);
        assertThat(messages).containsExactly(firstMessage, secondMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void comparatorForShouldBeDescByDateWhenOnlyDateInList() {
        Comparator<Message<TestId>> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(secondMessage, firstMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void comparatorForShouldBeDescByDateWhenOnlyDateDescInList() {
        Comparator<Message<TestId>> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date desc"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(secondMessage, firstMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void comparatorForShouldBeAscByDateWhenOnlyDateAscInList() {
        Comparator<Message<TestId>> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date asc"));
        messages.sort(comparator);
        assertThat(messages).containsExactly(firstMessage, secondMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void comparatorForShouldChainComparatorsWhenOnlyMultipleElementInList() {
        SimpleMessage<TestId> thirdMessage = new SimpleMessage<TestId>(secondMessage.getInternalDate(), 0, 0, null, new Flags(), new PropertyBuilder(), null);
        thirdMessage.setUid(3);
        messages.add(thirdMessage);

        Comparator<Message<TestId>> comparator = SortToComparatorConvertor.comparatorFor(ImmutableList.of("date asc", "id desc"));
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
}
