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
package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class MultimailboxesSearchQueryTest {

    private static final SearchQuery EMPTY_QUERY = new SearchQuery();

    @Test(expected=NullPointerException.class)
    public void buildShouldThrowWhenQueryIsNull() {
        MultimailboxesSearchQuery.from(null);
    }

    @Test
    public void buildShouldBuildWheninMailboxes() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.<MailboxId>of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.<MailboxId>of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes);
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldBuildWhenEmptyMailboxes() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.<MailboxId>of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.<MailboxId>of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes);
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).inMailboxes().build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldBuildWhenEmptyNotInMailboxes() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.<MailboxId>of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.<MailboxId>of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes);
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).notInMailboxes().build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    
    @Test
    public void buildShouldBuildWhenOneMailbox() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.<MailboxId>of(TestId.of(("id1")));
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.<MailboxId>of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes);
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).inMailboxes(TestId.of("id1")).build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldBuildWhenOneNotInMailbox() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.<MailboxId>of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.<MailboxId>of(TestId.of(("id1")));
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes);
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).notInMailboxes(TestId.of("id1")).build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    
    @Test
    public void buildShouldBuildWhenAllDefined() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.<MailboxId>of(TestId.of("id1"));
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.<MailboxId>of(TestId.of("id2"));
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes);
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).inMailboxes(TestId.of("id1")).notInMailboxes(TestId.of("id2")).build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

}
