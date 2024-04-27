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

package org.apache.james.pop3server.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Pop3MetadataStoreContract {
    int SIZE_1 = 234;
    int SIZE_2 = 456;

    Pop3MetadataStore testee();

    MailboxId generateMailboxId();

    MessageId generateMessageId();

    @Test
    default void statShouldReturnEmptyByDefault() {
        assertThat(
            Flux.from(testee()
                    .stat(generateMailboxId()))
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    default void statShouldReturnPreviouslyAddedMetadata() {
        MailboxId mailboxId = generateMailboxId();
        Pop3MetadataStore.StatMetadata metadata = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_1);
        Mono.from(testee().add(mailboxId, metadata)).block();

        assertThat(
            Flux.from(testee()
                    .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata);
    }

    @Test
    default void statShouldReturnAllPreviouslyAddedMetadata() {
        MailboxId mailboxId = generateMailboxId();
        Pop3MetadataStore.StatMetadata metadata1 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_1);
        Pop3MetadataStore.StatMetadata metadata2 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        assertThat(
            Flux.from(testee()
                    .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata1, metadata2);
    }

    @Test
    default void statShouldNotReturnDeletedData() {
        MailboxId mailboxId = generateMailboxId();
        Pop3MetadataStore.StatMetadata metadata1 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_1);
        Pop3MetadataStore.StatMetadata metadata2 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        Mono.from(testee().remove(mailboxId, metadata2.getMessageId())).block();

        assertThat(
            Flux.from(testee()
                    .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata1);
    }

    @Test
    default void statShouldNotReturnClearedData() {
        MailboxId mailboxId = generateMailboxId();
        Pop3MetadataStore.StatMetadata metadata1 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_1);
        Pop3MetadataStore.StatMetadata metadata2 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        Mono.from(testee().clear(mailboxId)).block();

        assertThat(
            Flux.from(testee()
                    .stat(mailboxId))
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    default void addShouldUpsert() {
        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        Pop3MetadataStore.StatMetadata metadata1 = new Pop3MetadataStore.StatMetadata(messageId, SIZE_1);
        Pop3MetadataStore.StatMetadata metadata2 = new Pop3MetadataStore.StatMetadata(messageId, SIZE_2);
        Mono.from(testee().add(mailboxId, metadata1)).block();
        Mono.from(testee().add(mailboxId, metadata2)).block();

        assertThat(
            Flux.from(testee()
                    .stat(mailboxId))
                .collectList()
                .block())
            .containsOnly(metadata2);
    }

    @Test
    default void clearShouldBeIdempotent() {
        assertThatCode(() -> Mono.from(testee().clear(generateMailboxId())).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void removeShouldBeIdempotent() {
        Pop3MetadataStore.StatMetadata metadata1 = new Pop3MetadataStore.StatMetadata(generateMessageId(), SIZE_1);
        assertThatCode(() -> Mono.from(testee().remove(generateMailboxId(), metadata1.getMessageId())).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void listAllShouldReturnAllData() {
        List<Pop3MetadataStore.FullMetadata> allExpectedEntries =
            IntStream.range(1, 20)
                .mapToObj(any -> {
                    MailboxId mailboxId = generateMailboxId();
                    MessageId messageId = generateMessageId();
                    Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
                    return new Pop3MetadataStore.FullMetadata(mailboxId, messageId, SIZE_1);
                })
                .collect(ImmutableList.toImmutableList());

        assertThat(Flux.from(testee().listAllEntries()).collectList().block())
            .hasSameElementsAs(allExpectedEntries);
    }

    @Test
    default void listAllShouldReturnEmptyByDefault() {
        assertThat(
            Flux.from(testee()
                    .listAllEntries())
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    default void listAllShouldNotReturnClearedData() {
        List<Pop3MetadataStore.FullMetadata> allExpectedEntries =
            IntStream.range(1, 20)
                .mapToObj(any -> {
                    MailboxId mailboxId = generateMailboxId();
                    MessageId messageId = generateMessageId();
                    Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
                    return new Pop3MetadataStore.FullMetadata(mailboxId, messageId, SIZE_1);
                })
                .collect(ImmutableList.toImmutableList());

        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
        Mono.from(testee().clear(mailboxId)).block();

        assertThat(Flux.from(testee().listAllEntries()).collectList().block())
            .hasSameElementsAs(allExpectedEntries);
    }

    @Test
    default void listAllShouldNotReturnDeletedData() {
        List<Pop3MetadataStore.FullMetadata> allEntriesExpect =
            IntStream.range(1, 20)
                .mapToObj(any -> {
                    MailboxId mailboxId = generateMailboxId();
                    MessageId messageId = generateMessageId();
                    Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
                    return new Pop3MetadataStore.FullMetadata(mailboxId, messageId, SIZE_1);
                })
                .collect(ImmutableList.toImmutableList());

        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
        Mono.from(testee().remove(mailboxId, messageId)).block();

        assertThat(Flux.from(testee().listAllEntries()).collectList().block())
            .hasSameElementsAs(allEntriesExpect);
    }

    @Test
    default void retrieveShouldReturnEmptyByDefault() {
        assertThat(
            Flux.from(testee()
                    .retrieve(generateMailboxId(), generateMessageId()))
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    default void retrieveShouldReturnData() {
        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();

        assertThat(Flux.from(testee().retrieve(mailboxId, messageId)).collectList().block())
            .contains(new Pop3MetadataStore.FullMetadata(mailboxId, messageId, SIZE_1));
    }

    @Test
    default void retrieveShouldNotReturnClearedData() {
        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
        Mono.from(testee().clear(mailboxId)).block();

        assertThat(Flux.from(testee().retrieve(mailboxId, messageId)).collectList().block())
            .hasSize(0);
    }

    @Test
    default void retrieveShouldNotReturnDeletedData() {
        MailboxId mailboxId = generateMailboxId();
        MessageId messageId = generateMessageId();
        Mono.from(testee().add(mailboxId, new Pop3MetadataStore.StatMetadata(messageId, SIZE_1))).block();
        Mono.from(testee().remove(mailboxId, messageId)).block();

        assertThat(Flux.from(testee().retrieve(mailboxId, messageId)).collectList().block())
            .hasSize(0);
    }
}
