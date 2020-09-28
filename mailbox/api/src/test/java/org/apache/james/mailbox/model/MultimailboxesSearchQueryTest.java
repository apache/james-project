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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.model.MultimailboxesSearchQuery.AccessibleNamespace;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class MultimailboxesSearchQueryTest {

    private static final SearchQuery EMPTY_QUERY = SearchQuery.matchAll();
    private static final TestId.Factory FACTORY = new TestId.Factory();
    private static final MailboxId ID_1 = FACTORY.fromString("1");
    private static final MailboxId ID_2 = FACTORY.fromString("2");

    @Test
    void buildShouldThrowWhenQueryIsNull() {
        assertThatThrownBy(() -> MultimailboxesSearchQuery.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldBuildWheninMailboxes() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes, new AccessibleNamespace());
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    void buildShouldBuildWhenEmptyMailboxes() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes, new AccessibleNamespace());
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).inMailboxes().build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    void buildShouldBuildWhenEmptyNotInMailboxes() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes, new AccessibleNamespace());
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).notInMailboxes().build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    
    @Test
    void buildShouldBuildWhenOneMailbox() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.of(ID_1);
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.of();
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes, new AccessibleNamespace());
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).inMailboxes(ID_1).build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    void buildShouldBuildWhenOneNotInMailbox() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.of();
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.of(ID_1);
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes, new AccessibleNamespace());
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).notInMailboxes(ID_1).build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    
    @Test
    void buildShouldBuildWhenAllDefined() {
        ImmutableSet<MailboxId> inMailboxes = ImmutableSet.of(ID_1);
        ImmutableSet<MailboxId> notInMailboxes = ImmutableSet.of(ID_2);
        MultimailboxesSearchQuery expected = new MultimailboxesSearchQuery(EMPTY_QUERY, inMailboxes, notInMailboxes, new AccessibleNamespace());
        MultimailboxesSearchQuery actual = MultimailboxesSearchQuery.from(EMPTY_QUERY).inMailboxes(ID_1).notInMailboxes(ID_2).build();
        
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

}
