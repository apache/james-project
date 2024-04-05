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

package org.apache.james.jmap.draft.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.jmap.model.Number;
import org.apache.james.jmap.model.mailbox.Rights;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.junit.Test;

public class MailboxTest {

    @Test(expected = NullPointerException.class)
    public void idShouldThrowWhenIdIsNull() {
        Mailbox.builder()
            .id(null);
    }

    @Test(expected = NullPointerException.class)
    public void nameShouldThrowWhenNameIsNull() {
        Mailbox.builder()
            .name(null);
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenIdIsNull() {
        Mailbox.builder().build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenNameIsNull() {
        Mailbox.builder()
            .id(InMemoryId.of(1))
            .build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenNameIsEmpty() {
        Mailbox.builder()
            .id(InMemoryId.of(1))
            .name("")
            .build();
    }

    @Test
    public void buildShouldWork() {
        Number totalMessages = Number.fromLong(456);
        Number unreadMessages = Number.fromLong(789);
        Number totalThreads = Number.fromLong(741);
        Number unreadThreads = Number.fromLong(852);
        Mailbox expectedMailbox = new Mailbox(InMemoryId.of(1), "name", Optional.of(InMemoryId.of(0)), Optional.of(Role.DRAFTS), SortOrder.of(123),
            true, true, true, true, true, true, true,
            totalMessages, unreadMessages, totalThreads, unreadThreads,
            Rights.EMPTY, MailboxNamespace.personal(), Optional.empty());

        Mailbox mailbox = Mailbox.builder()
            .id(InMemoryId.of(1))
            .name("name")
            .parentId(InMemoryId.of(0))
            .role(Optional.of(Role.DRAFTS))
            .sortOrder(SortOrder.of(123))
            .mustBeOnlyMailbox(true)
            .mayReadItems(true)
            .mayAddItems(true)
            .mayRemoveItems(true)
            .mayCreateChild(true)
            .mayRename(true)
            .mayDelete(true)
            .totalMessages(456)
            .unreadMessages(789)
            .totalThreads(741)
            .unreadThreads(852)
            .build();

        assertThat(mailbox).isEqualToComparingFieldByField(expectedMailbox);
    }

    @Test
    public void parentIdDefaultValueIsEmpty() {
        Mailbox mailbox = Mailbox.builder()
            .id(InMemoryId.of(0))
            .name("name")
            .build();

        assertThat(mailbox.getParentId()).isEmpty();
    }

    @Test
    public void totalMessagesShouldNeverBeNegative() {
        Mailbox mailbox = Mailbox.builder()
                .id(InMemoryId.of(1))
                .name("name")
                .totalMessages(-1234)
                .build();

        assertThat(mailbox.getTotalMessages()).isEqualTo(Number.ZERO);
    }

    @Test
    public void unreadMessagesShouldNeverBeNegative() {
        Mailbox mailbox = Mailbox.builder()
                .id(InMemoryId.of(1))
                .name("name")
                .unreadMessages(-1234)
                .build();

        assertThat(mailbox.getUnreadMessages()).isEqualTo(Number.ZERO);
    }

    @Test
    public void totalMessagesShouldReturnZeroWhenZero() {
        Mailbox mailbox = Mailbox.builder()
                .id(InMemoryId.of(1))
                .name("name")
                .totalMessages(0)
                .build();

        assertThat(mailbox.getTotalMessages()).isEqualTo(Number.ZERO);
    }

    @Test
    public void unreadMessagesShouldReturnZeroWhenZero() {
        Mailbox mailbox = Mailbox.builder()
                .id(InMemoryId.of(1))
                .name("name")
                .unreadMessages(0)
                .build();

        assertThat(mailbox.getUnreadMessages()).isEqualTo(Number.ZERO);
    }

    @Test
    public void totalMessagesShouldAcceptPositiveValue() {
        Mailbox mailbox = Mailbox.builder()
                .id(InMemoryId.of(1))
                .name("name")
                .totalMessages(1234)
                .build();

        Number expectedTotalMessages = Number.fromLong(1234);
        assertThat(mailbox.getTotalMessages()).isEqualTo(expectedTotalMessages);
    }

    @Test
    public void unreadMessagesShouldAcceptPositiveValue() {
        Mailbox mailbox = Mailbox.builder()
            .id(InMemoryId.of(1))
            .name("name")
            .unreadMessages(1234)
            .build();

        Number expectedTotalMessages = Number.fromLong(1234);
        assertThat(mailbox.getUnreadMessages()).isEqualTo(expectedTotalMessages);
    }

    @Test
    public void hasRoleShouldReturnFalseWhenMailboxEmptyRole() {
        Mailbox mailbox = Mailbox.builder()
            .id(InMemoryId.of(0))
            .name("name")
            .build();

        assertThat(mailbox.hasRole(Role.OUTBOX)).isFalse();
    }

    @Test
    public void hasRoleShouldReturnFalseWhenMailboxDoesNotHaveSameRole() {
        Mailbox mailbox = Mailbox.builder()
            .id(InMemoryId.of(0))
            .name("name")
            .role(Optional.of(Role.DRAFTS))
            .build();

        assertThat(mailbox.hasRole(Role.OUTBOX)).isFalse();
    }

    @Test
    public void hasRoleShouldReturnTrueWhenMailboxHasSameRole() {
        Mailbox mailbox = Mailbox.builder()
            .id(InMemoryId.of(0))
            .name("name")
            .role(Optional.of(Role.DRAFTS))
            .build();

        assertThat(mailbox.hasRole(Role.DRAFTS)).isTrue();
    }

    @Test
    public void hasSystemRoleShouldReturnFalseWhenMailboxHasNotSameRole() throws Exception {
        Mailbox mailbox = Mailbox.builder()
            .name("mailbox")
            .id(InMemoryId.of(0))
            .build();

        assertThat(mailbox.hasSystemRole()).isFalse();
    }

    @Test
    public void hasSystemRoleShouldReturnFalseWhenMailboxHasNotSystemRole() throws Exception {
        Mailbox mailbox = Mailbox.builder()
            .name("mailbox")
            .id(InMemoryId.of(0))
            .role(Role.from("any"))
            .build();

        assertThat(mailbox.hasSystemRole()).isFalse();
    }

    @Test
    public void hasSystemRoleShouldReturnTrueWhenMailboxHasSystemRole() throws Exception {
        Mailbox mailbox = Mailbox.builder()
            .name("mailbox")
            .id(InMemoryId.of(0))
            .role(Optional.of(Role.OUTBOX))
            .build();

        assertThat(mailbox.hasSystemRole()).isTrue();
    }
}
