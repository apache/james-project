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

import java.util.List;

import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SortConverterTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void convertToSortsShouldThrowOnNullValue() {
        expectedException.expect(NullPointerException.class);
        SortConverter.convertToSorts(null);
    }

    @Test
    public void convertToSortsShouldReturnEmptyOnEmptyEntry() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of()))
            .isEmpty();
    }

    @Test
    public void convertToSortsShouldThrowOnNullJmapSort() {
        List<String> jmapSorts = Lists.newArrayList((String) null);
        expectedException.expect(NullPointerException.class);
        SortConverter.convertToSorts(jmapSorts);
    }

    @Test
    public void convertToSortsShouldThrowOnEmptyJmapSort() {
        expectedException.expect(IllegalArgumentException.class);
        SortConverter.convertToSorts(ImmutableList.of(""));
    }

    @Test
    public void convertToSortsShouldThrowOnUnknownJmapSort() {
        expectedException.expect(IllegalArgumentException.class);
        SortConverter.convertToSorts(ImmutableList.of("unknown"));
    }

    @Test
    public void convertToSortsShouldSupportDate() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("date desc")))
            .containsExactly(new Sort(SortClause.SentDate, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldSupportId() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("id desc")))
            .containsExactly(new Sort(SortClause.Id, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldBeDescWhenNoOrderClause() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("date")))
            .containsExactly(new Sort(SortClause.SentDate, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldThrowWhenOnUnknownOrderClause() {
        expectedException.expect(IllegalArgumentException.class);
        SortConverter.convertToSorts(ImmutableList.of("date unknown"));
    }

    @Test
    public void convertToSortsShouldSupportAscOrder() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("date asc")))
            .containsExactly(new Sort(SortClause.SentDate, Order.NATURAL));
    }

    @Test
    public void convertToSortsShouldThrowWhenJmapSortIsTooLong() {
        expectedException.expect(IllegalArgumentException.class);
        SortConverter.convertToSorts(ImmutableList.of("date asc toomuch"));
    }

    @Test
    public void convertToSortsShouldSupportMultipleSorts() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("date asc", "id desc")))
            .containsExactly(new Sort(SortClause.SentDate, Order.NATURAL),
                new Sort(SortClause.Id, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldSupportSubject() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("subject desc")))
                .containsExactly(new Sort(SortClause.BaseSubject, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldSupportFrom() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("from desc")))
                .containsExactly(new Sort(SortClause.MailboxFrom, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldSupportTo() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("to desc")))
                .containsExactly(new Sort(SortClause.MailboxTo, Order.REVERSE));
    }

    @Test
    public void convertToSortsShouldSupportSize() {
        assertThat(SortConverter.convertToSorts(ImmutableList.of("size desc")))
                .containsExactly(new Sort(SortClause.Size, Order.REVERSE));
    }
}
