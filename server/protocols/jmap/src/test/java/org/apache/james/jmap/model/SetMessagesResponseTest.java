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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.NotImplementedException;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesResponseTest {

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
        ZonedDateTime currentDate = ZonedDateTime.now();
        ImmutableList<Message> created = ImmutableList.of(
            Message.builder()
                .id(MessageId.of("user|created|1"))
                .blobId("blobId")
                .threadId("threadId")
                .mailboxIds(ImmutableList.of("mailboxId"))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(currentDate)
                .preview("preview")
                .build());
        ImmutableList<MessageId> updated = ImmutableList.of(MessageId.of("user|updated|1"));
        ImmutableList<MessageId> destroyed = ImmutableList.of(MessageId.of("user|destroyed|1"));
        ImmutableMap<MessageId, SetError> notCreated = ImmutableMap.of(MessageId.of("user|created|2"), SetError.builder().type("created").build());
        ImmutableMap<MessageId, SetError> notUpdated = ImmutableMap.of(MessageId.of("user|update|2"), SetError.builder().type("updated").build());
        ImmutableMap<MessageId, SetError> notDestroyed  = ImmutableMap.of(MessageId.of("user|destroyed|3"), SetError.builder().type("destroyed").build());
        SetMessagesResponse expected = new SetMessagesResponse(null, null, null, created, updated, destroyed, notCreated, notUpdated, notDestroyed);

        SetMessagesResponse setMessagesResponse = SetMessagesResponse.builder()
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
        SetMessagesResponse.Builder emptyBuilder = SetMessagesResponse.builder();
        SetMessagesResponse testee = SetMessagesResponse.builder()
                .created(buildMessage("user|inbox|1"))
                .updated(ImmutableList.of(MessageId.of("user|inbox|2")))
                .destroyed(ImmutableList.of(MessageId.of("user|inbox|3")))
                .notCreated(ImmutableMap.of( MessageId.of("user|inbox|4"), SetError.builder().type("type").build()))
                .notUpdated(ImmutableMap.of(MessageId.of("user|inbox|5"), SetError.builder().type("type").build()))
                .notDestroyed(ImmutableMap.of(MessageId.of("user|inbox|6"), SetError.builder().type("type").build()))
                .build();

        // When
        testee.mergeInto(emptyBuilder);
        // Then
        assertThat(emptyBuilder.build()).isEqualToComparingFieldByField(testee);
    }

    private ImmutableList<Message> buildMessage(String messageId) {
        return ImmutableList.of(Message.builder()
                .id(MessageId.of(messageId))
                .blobId("blobId")
                .threadId("threadId")
                .mailboxIds(ImmutableList.of())
                .headers(ImmutableMap.of())
                .subject("subject")
                .size(0)
                .date(ZonedDateTime.now())
                .preview("preview")
                .build());
    }

    @Test
    public void mergeIntoShouldMergeUpdatedLists() {
        // Given
        MessageId buildersUpdatedMessageId = MessageId.of("user|inbox|1");
        SetMessagesResponse.Builder nonEmptyBuilder = SetMessagesResponse.builder()
                .updated(ImmutableList.of(buildersUpdatedMessageId));
        MessageId updatedMessageId = MessageId.of("user|inbox|2");
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
        String buildersCreatedMessageId = "user|inbox|1";
        SetMessagesResponse.Builder nonEmptyBuilder = SetMessagesResponse.builder()
                .created(buildMessage(buildersCreatedMessageId));
        String createdMessageId = "user|inbox|2";
        SetMessagesResponse testee = SetMessagesResponse.builder()
                .created(buildMessage(createdMessageId))
                .build();
        // When
        testee.mergeInto(nonEmptyBuilder);
        SetMessagesResponse mergedResponse = nonEmptyBuilder.build();

        // Then
        List<String> createdMessageIds = mergedResponse.getCreated().stream()
                .map(m -> m.getId().serialize())
                .collect(Collectors.toList());
        assertThat(createdMessageIds).containsExactly(buildersCreatedMessageId, createdMessageId);
    }

    @Test
    public void mergeIntoShouldMergeDestroyedLists() {
        // Given
        MessageId buildersDestroyedMessageId = MessageId.of("user|inbox|1");
        SetMessagesResponse.Builder nonEmptyBuilder = SetMessagesResponse.builder()
                .destroyed(ImmutableList.of(buildersDestroyedMessageId));
        MessageId destroyedMessageId = MessageId.of("user|inbox|2");
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
