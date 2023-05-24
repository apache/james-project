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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_GENERATOR;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.NOW;
import static org.apache.james.vault.DeletedMessageFixture.OLD_DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USERNAME;
import static org.apache.james.vault.DeletedMessageFixture.USERNAME_2;
import static org.apache.james.vault.search.Query.ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.task.Task;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeletedMessageVaultContract {
    Clock CLOCK = Clock.fixed(NOW.toInstant(), NOW.getZone());

    DeletedMessageVault getVault();

    UpdatableTickingClock getClock();

    @Test
    default void searchAllShouldThrowOnNullUser() {
       assertThatThrownBy(() -> getVault().search(null, ALL))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void searchAllShouldThrowOnNullQuery() {
       assertThatThrownBy(() -> getVault().search(USERNAME, null))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void appendShouldThrowOnNullMessage() {
       assertThatThrownBy(() -> getVault().append(null, new ByteArrayInputStream(CONTENT)))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void appendShouldThrowOnNullContent() {
       assertThatThrownBy(() -> getVault().append(DELETED_MESSAGE, null))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldThrowOnNullMessageId() {
       assertThatThrownBy(() -> getVault().delete(null, MESSAGE_ID))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldThrowOnNullUser() {
       assertThatThrownBy(() -> getVault().delete(USERNAME, null))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void loadMimeMessageShouldThrowOnNullMessageId() {
       assertThatThrownBy(() -> getVault().loadMimeMessage(null, MESSAGE_ID))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void loadMimeMessageShouldThrowOnNullUser() {
       assertThatThrownBy(() -> getVault().loadMimeMessage(USERNAME, null))
           .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void searchAllShouldReturnEmptyWhenNoItem() {
        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    default void searchAllShouldReturnContainedItems() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE);
    }

    @Test
    default void searchAllShouldReturnAllContainedItems() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE, DELETED_MESSAGE_2);
    }

    @Test
    default void searchAllShouldSupportLimitQuery() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        DeletedMessage deletedMessage3 = DELETED_MESSAGE_GENERATOR.apply(InMemoryMessageId.of(33).getRawId());
        Mono.from(getVault().append(deletedMessage3, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, Query.of(1, List.of()))).collectList().block())
            .hasSize(1);
        assertThat(Flux.from(getVault().search(USERNAME, Query.of(3, List.of()))).collectList().block())
            .containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2, deletedMessage3);
        assertThat(Flux.from(getVault().search(USERNAME, Query.of(4, List.of()))).collectList().block())
            .containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2, deletedMessage3);
    }

    @Test
    default void searchShouldReturnMatchingItems() {
        Mono.from(getVault().append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().append(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT))).block();

        assertThat(
            Flux.from(getVault().search(USERNAME,
                Query.of(CriterionFactory.subject().containsIgnoreCase(SUBJECT))))
                .collectList().block())
            .containsOnly(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Test
    default void vaultShouldBePartitionnedByUser() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME_2, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    default void searchAllShouldNotReturnDeletedItems() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        Mono.from(getVault().delete(USERNAME, MESSAGE_ID)).block();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    default void loadMimeMessageShouldReturnEmptyWhenNone() {
        assertThat(Mono.from(getVault().loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isEmpty();
    }

    @Test
    default void loadMimeMessageShouldReturnStoredValue() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Mono.from(getVault().loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isNotEmpty()
            .satisfies(maybeContent -> assertThat(maybeContent.get()).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
    }

    @Test
    default void loadMimeMessageShouldReturnEmptyWhenDeleted() {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        Mono.from(getVault().delete(USERNAME, MESSAGE_ID)).block();

        assertThat(Mono.from(getVault().loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isEmpty();
    }

    @Test
    default void appendShouldRunSuccessfullyInAConcurrentContext() throws Exception {
        int operationCount = 10;
        int threadCount = 10;
        ConcurrentTestRunner.builder()
            .operation((a, b) -> Mono.from(getVault().append(
                DELETED_MESSAGE_GENERATOR.apply(Long.valueOf(a * threadCount + b)),
                new ByteArrayInputStream(CONTENT))).block())
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .hasSize(threadCount * operationCount);
    }

    @Test
    default void deleteShouldRunSuccessfullyInAConcurrentContext() throws Exception {
        int operationCount = 10;
        int threadCount = 10;
        Flux.range(0, operationCount * threadCount)
            .flatMap(i -> Mono.from(getVault().append(
                DELETED_MESSAGE_GENERATOR.apply(Long.valueOf(i)),
                new ByteArrayInputStream(CONTENT))))
            .blockLast();

        ConcurrentTestRunner.builder()
            .operation((a, b) -> Mono.from(getVault().delete(USERNAME, InMemoryMessageId.of(a * threadCount + b))).block())
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    default void deleteExpiredMessagesTaskShouldCompleteWhenNoMail() throws InterruptedException {
        Task.Result result = getVault().deleteExpiredMessagesTask().run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void deleteExpiredMessagesTaskShouldCompleteWhenAllMailsDeleted() throws InterruptedException {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().delete(USERNAME, DELETED_MESSAGE.getMessageId())).block();

        Task.Result result = getVault().deleteExpiredMessagesTask().run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void deleteExpiredMessagesTaskShouldCompleteWhenOnlyRecentMails() throws InterruptedException {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        Task.Result result = getVault().deleteExpiredMessagesTask().run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void deleteExpiredMessagesTaskShouldCompleteWhenOnlyOldMails() throws InterruptedException {
        Mono.from(getVault().append(OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        Task.Result result = getVault().deleteExpiredMessagesTask().run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void deleteExpiredMessagesTaskShouldDoNothingWhenEmpty() throws InterruptedException {
        getVault().deleteExpiredMessagesTask().run();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    default void deleteExpiredMessagesTaskShouldNotDeleteRecentMails() throws InterruptedException {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        getVault().deleteExpiredMessagesTask().run();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE);
    }

    @Test
    default void deleteExpiredMessagesTaskShouldDeleteOldMails() throws InterruptedException {
        Mono.from(getVault().append(OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        getClock().setInstant(NOW.plusYears(2).toInstant());
        getVault().deleteExpiredMessagesTask().run();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    default void deleteExpiredMessagesTaskShouldDeleteOldMailsWhenRunSeveralTime() throws InterruptedException {
        Mono.from(getVault().append(OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        getClock().setInstant(NOW.plusYears(2).toInstant());
        getVault().deleteExpiredMessagesTask().run();

        Mono.from(getVault().append(OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        getClock().setInstant(NOW.plusYears(4).toInstant());
        getVault().deleteExpiredMessagesTask().run();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(getVault().search(USERNAME_2, ALL)).collectList().block())
            .isEmpty();
    }
}
