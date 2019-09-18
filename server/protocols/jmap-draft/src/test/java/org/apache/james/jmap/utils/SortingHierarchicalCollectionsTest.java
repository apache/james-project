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
import java.util.stream.Collectors;

import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.utils.DependencyGraph.CycleDetectedException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class SortingHierarchicalCollectionsTest {
    private static final InMemoryId INBOX_ID = InMemoryId.of(0);
    private static final InMemoryId A_ID = InMemoryId.of(1);
    private static final InMemoryId B_ID = InMemoryId.of(2);
    private static final InMemoryId C_ID = InMemoryId.of(3);
    private static final InMemoryId D_ID = InMemoryId.of(4);
    private static final InMemoryId E_ID = InMemoryId.of(5);

    private SortingHierarchicalCollections<Mailbox, MailboxId> sut;

    @Before
    public void setup() {
        sut = new SortingHierarchicalCollections<>(Mailbox::getId, Mailbox::getParentId);
    }

    @Test
    public void sortFromRootToLeafShouldReturnOrderedMailbox() {
        // Given
        Mailbox inbox = Mailbox.builder().name("INBOX").id(INBOX_ID).build();
        Mailbox a = Mailbox.builder().name("A").id(A_ID).parentId(INBOX_ID).build();
        Mailbox b = Mailbox.builder().name("B").id(B_ID).parentId(INBOX_ID).build();
        Mailbox c = Mailbox.builder().name("C").id(C_ID).parentId(B_ID).build();
        Mailbox d = Mailbox.builder().name("D").id(D_ID).parentId(A_ID).build();
        Mailbox e = Mailbox.builder().name("E").id(E_ID).parentId(C_ID).build();
        ImmutableList<Mailbox> input = ImmutableList.of(b, c, d, a, inbox, e);

        // When
        List<Mailbox> result = sut.sortFromRootToLeaf(input);

        // Then
        assertThat(result).extracting(Mailbox::getName).endsWith("C", "D", "E").startsWith("INBOX");
    }

    @Test
    public void sortFromRootToLeafEmptyMailboxShouldReturnEmpty() {
        ImmutableList<Mailbox> input = ImmutableList.of();
        List<Mailbox> result = sut.sortFromRootToLeaf(input);
        assertThat(result).isEmpty();
    }

    @Test
    public void sortFromRootToLeafOrphanMailboxesShouldReturnInput() {
        Mailbox a = Mailbox.builder().name("A").id(A_ID).build();
        Mailbox b = Mailbox.builder().name("B").id(B_ID).build();
        Mailbox c = Mailbox.builder().name("C").id(C_ID).build();

        ImmutableList<Mailbox> input = ImmutableList.of(a, b, c);
        List<String> result = sut.sortFromRootToLeaf(input).stream()
                .map(Mailbox::getName)
                .collect(Collectors.toList());

        assertThat(result).containsExactly("A", "B", "C");
    }

    @Test(expected = CycleDetectedException.class)
    public void sortFromRootToLeafWithLoopShouldThrow() {
        Mailbox a = Mailbox.builder().name("A").id(A_ID).parentId(B_ID).build();
        Mailbox b = Mailbox.builder().name("B").id(B_ID).parentId(A_ID).build();

        ImmutableList<Mailbox> input = ImmutableList.of(a, b);

        sut.sortFromRootToLeaf(input);
    }

    @Test
    public void sortFromLeafToRootShouldReturnOrderedMailbox() {
        //Given
        Mailbox inbox = Mailbox.builder().name("INBOX").id(INBOX_ID).build();
        Mailbox a = Mailbox.builder().name("A").id(A_ID).parentId(INBOX_ID).build();
        Mailbox b = Mailbox.builder().name("B").id(B_ID).parentId(INBOX_ID).build();
        Mailbox c = Mailbox.builder().name("C").id(C_ID).parentId(B_ID).build();
        Mailbox d = Mailbox.builder().name("D").id(D_ID).parentId(A_ID).build();
        Mailbox e = Mailbox.builder().name("E").id(E_ID).parentId(C_ID).build();

        ImmutableList<Mailbox> input = ImmutableList.of(b, c, d, a, inbox, e);

        //When
        List<Mailbox> result = sut.sortFromLeafToRoot(input);

        assertThat(result).extracting(Mailbox::getName).endsWith("INBOX").startsWith("E");
    }

    @Test
    public void sortFromLeafToRootEmptyMailboxShouldReturnEmpty() {
        ImmutableList<Mailbox> input = ImmutableList.of();
        List<Mailbox> result = sut.sortFromLeafToRoot(input);
        assertThat(result).isEmpty();
    }

    @Test
    public void sortFromLeafToRootOrphanMailboxesShouldReturnInput() {
        Mailbox a = Mailbox.builder().name("A").id(A_ID).build();
        Mailbox b = Mailbox.builder().name("B").id(B_ID).build();
        Mailbox c = Mailbox.builder().name("C").id(C_ID).build();

        ImmutableList<Mailbox> input = ImmutableList.of(a, b, c);
        List<String> result = sut.sortFromLeafToRoot(input).stream()
                .map(Mailbox::getName)
                .collect(Collectors.toList());

        assertThat(result).containsExactly("C", "B", "A");
    }

    @Test(expected = CycleDetectedException.class)
    public void sortFromLeafToRootWithLoopShouldThrow() {
        Mailbox a = Mailbox.builder().name("A").id(A_ID).parentId(B_ID).build();
        Mailbox b = Mailbox.builder().name("B").id(B_ID).parentId(A_ID).build();

        ImmutableList<Mailbox> input = ImmutableList.of(a, b);

        sut.sortFromLeafToRoot(input);
    }
}