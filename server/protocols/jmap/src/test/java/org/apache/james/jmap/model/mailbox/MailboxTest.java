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

package org.apache.james.jmap.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.mailbox.inmemory.InMemoryId;
import org.junit.Test;

public class MailboxTest {

    @Test(expected=NullPointerException.class)
    public void idShouldThrowWhenIdIsNull() {
        Mailbox.builder()
            .id(null);
    }

    @Test(expected=NullPointerException.class)
    public void nameShouldThrowWhenNameIsNull() {
        Mailbox.builder()
            .name(null);
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenIdIsNull() {
        Mailbox.builder().build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenNameIsNull() {
        Mailbox.builder()
            .id(InMemoryId.of(1))
            .build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenNameIsEmpty() {
        Mailbox.builder()
            .id(InMemoryId.of(1))
            .name("")
            .build();
    }

    @Test
    public void buildShouldWork() {
        Mailbox expectedMailbox = new Mailbox(InMemoryId.of(1), "name", Optional.of(InMemoryId.of(0)), Optional.of(Role.DRAFTS), SortOrder.of(123),
                true, true, true, true, true, true, true, 456, 789, 741, 852, Rights.EMPTY);

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
}
