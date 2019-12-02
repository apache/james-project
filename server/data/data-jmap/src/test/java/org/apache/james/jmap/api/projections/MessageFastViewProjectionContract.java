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

package org.apache.james.jmap.api.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface MessageFastViewProjectionContract {

    Preview PREVIEW_1 = Preview.from("preview 1");
    Preview PREVIEW_2 = Preview.from("preview 2");

    MessageFastViewProjection testee();

    MessageId newMessageId();

    @Test
    default void retrieveShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().retrieve(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void retrieveShouldReturnStoredPreview() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, PREVIEW_1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId)).block())
            .isEqualTo(PREVIEW_1);
    }

    @Test
    default void retrieveShouldReturnEmptyWhenMessageIdNotFound() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId2)).blockOptional())
            .isEmpty();
    }

    @Test
    default void retrieveShouldReturnTheRightPreviewWhenStoringMultipleMessageIds() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();
        Mono.from(testee().store(messageId2, PREVIEW_2))
            .block();

        SoftAssertions.assertSoftly(softly -> {
           softly.assertThat(Mono.from(testee().retrieve(messageId1)).block())
               .isEqualTo(PREVIEW_1);
           softly.assertThat(Mono.from(testee().retrieve(messageId2)).block())
               .isEqualTo(PREVIEW_2);
        });
    }

    @Test
    default void storeShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().store(null, PREVIEW_1)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void storeShouldThrowWhenNullPreview() {
        MessageId messageId1 = newMessageId();
        assertThatThrownBy(() -> Mono.from(testee().store(messageId1, null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void storeShouldOverrideOldRecord() {
        MessageId messageId1 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();

        Mono.from(testee().store(messageId1, PREVIEW_2))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId1)).block())
            .isEqualTo(PREVIEW_2);
    }

    @Test
    default void storeShouldBeIdempotent() {
        MessageId messageId1 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();

        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId1)).block())
            .isEqualTo(PREVIEW_1);
    }

    @Test
    default void concurrentStoreShouldOverrideOldValueWhenSameMessageId() throws Exception {
        int threadCount = 10;
        int stepCount = 100;

        ConcurrentHashMap<Integer, MessageId> messageIds = new ConcurrentHashMap<>();
        IntStream.range(0, threadCount)
            .forEach(thread -> messageIds.put(thread, newMessageId()));

        ConcurrentTestRunner.builder()
            .reactorOperation((thread, step) -> testee()
                .store(messageIds.get(thread), Preview.from(String.valueOf(step))))
            .threadCount(threadCount)
            .operationCount(stepCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        IntStream.range(0, threadCount)
            .forEach(index -> assertThat(Mono.from(testee()
                    .retrieve(messageIds.get(index)))
                    .block())
                .isEqualTo(Preview.from(String.valueOf(stepCount - 1))));
    }

    @Test
    default void storeShouldBeConsistentUponSingleKeyOperation() throws Exception {
        MessageId messageId = newMessageId();
        int threadCount = 10;
        int operationCount = 100;

        ConcurrentTestRunner.builder()
            .reactorOperation((thread, step) -> testee()
                .store(messageId, Preview.from(String.valueOf(step * threadCount + thread))))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        Preview preview = Mono.from(testee().retrieve(messageId)).block();
        Integer previewAsInt = Integer.valueOf(preview.getValue());

        assertThat(previewAsInt)
            .describedAs("Ensure the stored result was generated by the last operation of one of the threads")
            .isBetween(threadCount * (operationCount - 1), threadCount * operationCount);
    }

    @Test
    default void deleteShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().delete(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotThrowWhenMessageIdNotFound() {
        MessageId messageId1 = newMessageId();
        assertThatCode(() -> Mono.from(testee().delete(messageId1)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteStoredRecord() {
        MessageId messageId1 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();

        Mono.from(testee().delete(messageId1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId1)).blockOptional())
            .isEmpty();
    }

    @Test
    default void deleteShouldNotDeleteAnotherRecord() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();
        Mono.from(testee().store(messageId2, PREVIEW_2))
            .block();

        Mono.from(testee().delete(messageId1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId2)).block())
            .isEqualTo(PREVIEW_2);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        MessageId messageId1 = newMessageId();
        Mono.from(testee().store(messageId1, PREVIEW_1))
            .block();

        Mono.from(testee().delete(messageId1))
            .block();
        Mono.from(testee().delete(messageId1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId1)).blockOptional())
            .isEmpty();
    }
}