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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.jmap.draft.model.UpdateMessagePatch;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesUpdateProcessorTest {

    @Test
    public void processShouldReturnEmptyUpdatedWhenRequestHasEmptyUpdate() {
        UpdateMessagePatchConverter updatePatchConverter = null;
        MessageIdManager messageIdManager = null;
        SystemMailboxesProvider systemMailboxesProvider = null;
        MailboxId.Factory mailboxIdFactory = null;
        MessageSender messageSender = null;
        ReferenceUpdater referenceUpdater = null;
        SetMessagesUpdateProcessor sut = new SetMessagesUpdateProcessor(updatePatchConverter,
            messageIdManager,
            systemMailboxesProvider,
            mailboxIdFactory,
            messageSender,
            new RecordingMetricFactory(),
            referenceUpdater);
        SetMessagesRequest requestWithEmptyUpdate = SetMessagesRequest.builder().build();

        SetMessagesResponse result = sut.process(requestWithEmptyUpdate, null);

        assertThat(result.getUpdated()).isEmpty();
        assertThat(result.getNotUpdated()).isEmpty();
    }

    @Test
    public void processShouldReturnNonEmptyNotUpdatedWhenRequestHasInvalidUpdate() {
        // Given
        UpdateMessagePatchConverter mockConverter = mock(UpdateMessagePatchConverter.class);
        UpdateMessagePatch mockInvalidPatch = mock(UpdateMessagePatch.class);
        when(mockInvalidPatch.isValid()).thenReturn(false);

        MessageProperties.MessageProperty invalidProperty = MessageProperties.MessageProperty.from;
        ImmutableList<ValidationResult> nonEmptyValidationResult = ImmutableList.of(ValidationResult.builder()
                .property(invalidProperty.toString()).build());
        when(mockInvalidPatch.getValidationErrors())
                .thenReturn(nonEmptyValidationResult);
        when(mockConverter.fromJsonNode(any(ObjectNode.class)))
                .thenReturn(mockInvalidPatch);

        MessageIdManager messageIdManager = null;
        SystemMailboxesProvider systemMailboxesProvider = null;
        MailboxId.Factory mailboxIdFactory = null;
        MessageSender messageSender = null;
        ReferenceUpdater referenceUpdater = null;
        SetMessagesUpdateProcessor sut = new SetMessagesUpdateProcessor(mockConverter,
            messageIdManager,
            systemMailboxesProvider,
            mailboxIdFactory,
            messageSender,
            new RecordingMetricFactory(),
            referenceUpdater);
        MessageId requestMessageId = TestMessageId.of(1);
        SetMessagesRequest requestWithInvalidUpdate = SetMessagesRequest.builder()
                .update(ImmutableMap.of(requestMessageId, JsonNodeFactory.instance.objectNode()))
                .build();

        // When
        SetMessagesResponse result = sut.process(requestWithInvalidUpdate, null);

        // Then
        assertThat(result.getNotUpdated()).isNotEmpty().describedAs("NotUpdated should not be empty");
        assertThat(result.getNotUpdated()).containsKey(requestMessageId);
        assertThat(result.getNotUpdated().get(requestMessageId).getProperties()).isPresent();
        assertThat(result.getNotUpdated().get(requestMessageId).getProperties().get()).contains(invalidProperty);
        assertThat(result.getUpdated()).isEmpty();
    }

}