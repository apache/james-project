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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.message.view.MessageFullView;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesResponseTest {

    private static final Preview PREVIEW = Preview.from("preview");

    @Test
    public void builderShouldThrowWhenAccountIdIsGiven() {
        assertThatThrownBy(() -> SetMessagesResponse.builder().accountId(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowWhenOldStateGiven() {
        assertThatThrownBy(() -> SetMessagesResponse.builder().oldState(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowWhenNewStateIsGiven() {
        assertThatThrownBy(() -> SetMessagesResponse.builder().newState(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldWork() {
        Instant currentDate = Instant.now();
        ImmutableMap<CreationMessageId, MessageFullView> created = ImmutableMap.of(CreationMessageId.of("user|created|1"),
            MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .mailboxId(InMemoryId.of(123))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(currentDate)
                .preview(PREVIEW)
                .hasAttachment(false)
                .build());
        ImmutableList<MessageId> updated = ImmutableList.of(TestMessageId.of(2));
        ImmutableList<MessageId> destroyed = ImmutableList.of(TestMessageId.of(3));
        ImmutableMap<CreationMessageId, SetError> notCreated = ImmutableMap.of(CreationMessageId.of("dead-beef-defec8"), SetError.builder().type(SetError.Type.INVALID_PROPERTIES).build());
        ImmutableMap<MessageId, SetError> notUpdated = ImmutableMap.of(TestMessageId.of(4), SetError.builder().type(SetError.Type.INVALID_ARGUMENTS).build());
        ImmutableMap<MessageId, SetError> notDestroyed  = ImmutableMap.of(TestMessageId.of(5), SetError.builder().type(SetError.Type.ERROR).build());
        ImmutableMap<CreationMessageId, SetError> mdnNotSent = ImmutableMap.of(CreationMessageId.of("dead-beef-defec9"), SetError.builder().type(SetError.Type.NOT_FOUND).build());
        ImmutableMap<CreationMessageId, MessageId> mdnSent = ImmutableMap.of(CreationMessageId.of("dead-beef-defed0"), TestMessageId.of(12));
        SetMessagesResponse expected = new SetMessagesResponse(null, null, null, created, mdnSent, updated, destroyed, notCreated, mdnNotSent, notUpdated, notDestroyed);

        SetMessagesResponse setMessagesResponse = SetMessagesResponse.builder()
            .created(created)
            .updated(updated)
            .destroyed(destroyed)
            .notCreated(notCreated)
            .notUpdated(notUpdated)
            .notDestroyed(notDestroyed)
            .mdnNotSent(mdnNotSent)
            .mdnSent(mdnSent)
            .build();

        assertThat(setMessagesResponse).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void mergeIntoShouldCopyItemsWhenBuilderIsEmpty() {
        // Given
        SetMessagesResponse.Builder emptyBuilder = SetMessagesResponse.builder();
        SetMessagesResponse testee = SetMessagesResponse.builder()
                .created(buildMessage(CreationMessageId.of("user|inbox|1"), TestMessageId.of(1)))
                .updated(ImmutableList.of(TestMessageId.of(2)))
                .destroyed(ImmutableList.of(TestMessageId.of(3)))
                .notCreated(ImmutableMap.of(CreationMessageId.of("dead-beef-defec8"), SetError.builder().type(SetError.Type.INVALID_PROPERTIES).build()))
                .notUpdated(ImmutableMap.of(TestMessageId.of(5), SetError.builder().type(SetError.Type.ERROR).build()))
                .notDestroyed(ImmutableMap.of(TestMessageId.of(6), SetError.builder().type(SetError.Type.NOT_FOUND).build()))
                .build();

        // When
        testee.mergeInto(emptyBuilder);
        // Then
        assertThat(emptyBuilder.build()).isEqualToComparingFieldByField(testee);
    }

    private ImmutableMap<CreationMessageId, MessageFullView> buildMessage(CreationMessageId creationMessageId, MessageId messageId) {
        return ImmutableMap.of(creationMessageId, MessageFullView.builder()
                .id(messageId)
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                .headers(ImmutableMap.of())
                .subject("subject")
                .size(0)
                .date(Instant.now())
                .preview(PREVIEW)
                .hasAttachment(false)
                .build());
    }

    @Test
    public void mergeIntoShouldMergeUpdatedLists() {
        // Given
        MessageId buildersUpdatedMessageId = TestMessageId.of(1);
        SetMessagesResponse.Builder nonEmptyBuilder = SetMessagesResponse.builder()
                .updated(ImmutableList.of(buildersUpdatedMessageId));
        MessageId updatedMessageId = TestMessageId.of(2);
        SetMessagesResponse testee = SetMessagesResponse.builder()
                .updated(ImmutableList.of(updatedMessageId))
                .build();
        // When
        testee.mergeInto(nonEmptyBuilder);
        SetMessagesResponse mergedResponse = nonEmptyBuilder.build();

        // Then
        assertThat(mergedResponse.getUpdated()).containsExactly(buildersUpdatedMessageId, updatedMessageId);
    }

    @Test
    public void mergeIntoShouldMergeCreatedLists() {
        // Given
        CreationMessageId buildersCreatedMessageId = CreationMessageId.of("user|inbox|1");
        SetMessagesResponse.Builder nonEmptyBuilder = SetMessagesResponse.builder()
                .created(buildMessage(buildersCreatedMessageId, TestMessageId.of(1)));
        CreationMessageId createdMessageId = CreationMessageId.of("user|inbox|2");
        SetMessagesResponse testee = SetMessagesResponse.builder()
                .created(buildMessage(createdMessageId, TestMessageId.of(2)))
                .build();
        // When
        testee.mergeInto(nonEmptyBuilder);
        SetMessagesResponse mergedResponse = nonEmptyBuilder.build();

        // Then
        assertThat(mergedResponse.getCreated().keySet()).containsExactly(buildersCreatedMessageId, createdMessageId);
    }

    @Test
    public void mergeIntoShouldMergeDestroyedLists() {
        // Given
        MessageId buildersDestroyedMessageId = TestMessageId.of(1);
        SetMessagesResponse.Builder nonEmptyBuilder = SetMessagesResponse.builder()
                .destroyed(ImmutableList.of(buildersDestroyedMessageId));
        MessageId destroyedMessageId = TestMessageId.of(2);
        SetMessagesResponse testee = SetMessagesResponse.builder()
                .destroyed(ImmutableList.of(destroyedMessageId))
                .build();
        // When
        testee.mergeInto(nonEmptyBuilder);
        SetMessagesResponse mergedResponse = nonEmptyBuilder.build();

        // Then
        assertThat(mergedResponse.getDestroyed()).containsExactly(buildersDestroyedMessageId, destroyedMessageId);
    }
}
