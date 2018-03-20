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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMailboxesResponseTest {

    @Test
    public void builderShouldWork() {
        ImmutableMap<MailboxCreationId, Mailbox> created = ImmutableMap.of(MailboxCreationId.of("1"),
            Mailbox.builder()
                .id(InMemoryId.of(1))
                .name("myBox")
                .build());
        ImmutableList<MailboxId> updated = ImmutableList.of(InMemoryId.of(2));
        ImmutableList<MailboxId> destroyed = ImmutableList.of(InMemoryId.of(3));
        ImmutableMap<MailboxCreationId, SetError> notCreated = ImmutableMap.of(MailboxCreationId.of("dead-beef-defec8"), SetError.builder().type(SetError.Type.INVALID_PROPERTIES).build());
        ImmutableMap<MailboxId, SetError> notUpdated = ImmutableMap.of(InMemoryId.of(4), SetError.builder().type(SetError.Type.INVALID_ARGUMENTS).build());
        ImmutableMap<MailboxId, SetError> notDestroyed  = ImmutableMap.of(InMemoryId.of(5), SetError.builder().type(SetError.Type.NOT_FOUND).build());
        SetMailboxesResponse expected = new SetMailboxesResponse(created, updated, destroyed, notCreated, notUpdated, notDestroyed);

        SetMailboxesResponse setMessagesResponse = SetMailboxesResponse.builder()
            .created(created)
            .updated(updated)
            .destroyed(destroyed)
            .notCreated(notCreated)
            .notUpdated(notUpdated)
            .notDestroyed(notDestroyed)
            .build();

        assertThat(setMessagesResponse).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void mergeIntoShouldCopyItemsWhenBuilderIsEmpty() {
        // Given
        SetMailboxesResponse.Builder emptyBuilder = SetMailboxesResponse.builder();
        SetMailboxesResponse testee = SetMailboxesResponse.builder()
                .created(buildMailbox(MailboxCreationId.of("1")))
                .destroyed(InMemoryId.of(2))
                .notCreated(ImmutableMap.of(MailboxCreationId.of("dead-beef-defec8"), SetError.builder().type(SetError.Type.INVALID_PROPERTIES).build()))
                .notDestroyed(ImmutableMap.of(InMemoryId.of(3), SetError.builder().type(SetError.Type.INVALID_PROPERTIES).build()))
                .build();

        // When
        testee.mergeInto(emptyBuilder);
        // Then
        assertThat(emptyBuilder.build()).isEqualToComparingFieldByField(testee);
    }

    private ImmutableMap<MailboxCreationId, Mailbox> buildMailbox(MailboxCreationId mailboxId) {
        return ImmutableMap.of(mailboxId, Mailbox.builder()
                .id(InMemoryId.of(Long.valueOf(mailboxId.getCreationId())))
                .name(mailboxId.getCreationId())
                .build());
    }

    @Test
    public void mergeIntoShouldMergeCreatedLists() {
        // Given
        MailboxCreationId buildersCreatedMessageId = MailboxCreationId.of("1");
        SetMailboxesResponse.Builder nonEmptyBuilder = SetMailboxesResponse.builder()
                .created(buildMailbox(buildersCreatedMessageId));
        MailboxCreationId createdMessageId = MailboxCreationId.of("2");
        SetMailboxesResponse testee = SetMailboxesResponse.builder()
                .created(buildMailbox(createdMessageId))
                .build();
        // When
        testee.mergeInto(nonEmptyBuilder);
        SetMailboxesResponse mergedResponse = nonEmptyBuilder.build();

        // Then
        assertThat(mergedResponse.getCreated().keySet()).containsExactly(buildersCreatedMessageId, createdMessageId);
    }

    @Test
    public void mergeIntoShouldMergeDestroyedLists() {
        // Given
        InMemoryId buildersDestroyedMessageId = InMemoryId.of(1);
        SetMailboxesResponse.Builder nonEmptyBuilder = SetMailboxesResponse.builder()
                .destroyed(buildersDestroyedMessageId);
        InMemoryId destroyedMessageId = InMemoryId.of(2);
        SetMailboxesResponse testee = SetMailboxesResponse.builder()
                .destroyed(destroyedMessageId)
                .build();
        // When
        testee.mergeInto(nonEmptyBuilder);
        SetMailboxesResponse mergedResponse = nonEmptyBuilder.build();

        // Then
        assertThat(mergedResponse.getDestroyed()).containsExactly(buildersDestroyedMessageId, destroyedMessageId);
    }
}
