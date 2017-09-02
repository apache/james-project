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

package org.apache.james.mailbox.store.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mailbox.store.search.comparator.BaseSubjectComparator;
import org.apache.james.mailbox.store.search.comparator.CombinedComparator;
import org.apache.james.mailbox.store.search.comparator.HeaderDisplayComparator;
import org.apache.james.mailbox.store.search.comparator.HeaderMailboxComparator;
import org.apache.james.mailbox.store.search.comparator.InternalDateComparator;
import org.apache.james.mailbox.store.search.comparator.MessageIdComparator;
import org.apache.james.mailbox.store.search.comparator.ReverseComparator;
import org.apache.james.mailbox.store.search.comparator.SentDateComparator;
import org.apache.james.mailbox.store.search.comparator.SizeComparator;
import org.apache.james.mailbox.store.search.comparator.UidComparator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class CombinedComparatorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void createShouldThrowOnNullListOfSort() {
        expectedException.expect(NullPointerException.class);

        CombinedComparator.create(null);
    }

    @Test
    public void createShouldThrowOnEmptySort() {
        expectedException.expect(IllegalArgumentException.class);

        CombinedComparator.create(ImmutableList.<SearchQuery.Sort>of());
    }

    @Test
    public void createShouldConvertInternalDate() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.Arrival))).getComparators())
            .containsOnly(InternalDateComparator.INTERNALDATE);
    }

    @Test
    public void createShouldConvertCc() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.MailboxCc))).getComparators())
            .containsOnly(HeaderMailboxComparator.CC_COMPARATOR);
    }

    @Test
    public void createShouldConvertFrom() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.MailboxFrom))).getComparators())
            .containsOnly(HeaderMailboxComparator.FROM_COMPARATOR);
    }

    @Test
    public void createShouldConvertTo() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.MailboxTo))).getComparators())
            .containsOnly(HeaderMailboxComparator.TO_COMPARATOR);
    }

    @Test
    public void createShouldConvertSize() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.Size))).getComparators())
            .containsOnly(SizeComparator.SIZE);
    }

    @Test
    public void createShouldConvertBaseSubject() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.BaseSubject))).getComparators())
            .containsOnly(BaseSubjectComparator.BASESUBJECT);
    }

    @Test
    public void createShouldConvertUid() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.Uid))).getComparators())
            .containsOnly(UidComparator.UID);
    }

    @Test
    public void createShouldConvertSentDate() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.SentDate))).getComparators())
            .containsOnly(SentDateComparator.SENTDATE);
    }

    @Test
    public void createShouldConvertDisplayTo() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.DisplayTo))).getComparators())
            .containsOnly(HeaderDisplayComparator.TO_COMPARATOR);
    }

    @Test
    public void createShouldConvertDisplayFrom() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.DisplayFrom))).getComparators())
            .containsOnly(HeaderDisplayComparator.FROM_COMPARATOR);
    }

    @Test
    public void createShouldConvertId() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.Id))).getComparators())
            .containsOnly(MessageIdComparator.MESSAGE_ID_COMPARATOR);
    }

    @Test
    public void createShouldReverse() {
        assertThat(CombinedComparator.create(ImmutableList.of(new Sort(SortClause.DisplayFrom, Order.REVERSE))).getComparators())
            .containsOnly(new ReverseComparator(HeaderDisplayComparator.FROM_COMPARATOR));
    }
}
