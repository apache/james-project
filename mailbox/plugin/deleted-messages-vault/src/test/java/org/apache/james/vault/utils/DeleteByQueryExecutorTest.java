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

package org.apache.james.vault.utils;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_OTHER_USER;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.james.vault.DeletedMessageVaultContract.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.apache.james.task.Task;
import org.apache.james.vault.RetentionConfiguration;
import org.apache.james.vault.memory.MemoryDeletedMessagesVault;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DeleteByQueryExecutorTest {
    private MemoryDeletedMessagesVault vault;
    private DeleteByQueryExecutor testee;
    private DeleteByQueryExecutor.Notifiers notifiers;
    private DeleteByQueryExecutor.Notifier userHandledNotifier;
    private DeleteByQueryExecutor.Notifier searchErrorNotifier;
    private DeleteByQueryExecutor.Notifier deletionErrorNotifier;
    private DeleteByQueryExecutor.Notifier permanentlyDeletedMessageNotifyer;

    @BeforeEach
    void setUp() {
        vault = Mockito.spy(new MemoryDeletedMessagesVault(RetentionConfiguration.DEFAULT, CLOCK));
        testee = new DeleteByQueryExecutor(vault, vault::usersWithVault);

        userHandledNotifier = mock(DeleteByQueryExecutor.Notifier.class);
        searchErrorNotifier = mock(DeleteByQueryExecutor.Notifier.class);
        deletionErrorNotifier = mock(DeleteByQueryExecutor.Notifier.class);
        permanentlyDeletedMessageNotifyer = mock(DeleteByQueryExecutor.Notifier.class);
        notifiers = new DeleteByQueryExecutor.Notifiers(
            userHandledNotifier,
            searchErrorNotifier,
            deletionErrorNotifier,
            permanentlyDeletedMessageNotifyer);
    }

    @Test
    void deleteByQueryShouldReturnPartialWhenListingUserFailed() {
        when(vault.usersWithVault()).thenReturn(Mono.error(new RuntimeException()));
        testee = new DeleteByQueryExecutor(vault, vault::usersWithVault);

        assertThat(testee.deleteByQuery(Query.ALL, notifiers)).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void deleteByQueryShouldNotUpdateNotifiersWhenListingUserFailed() {
        when(vault.usersWithVault()).thenReturn(Mono.error(new RuntimeException()));

        testee.deleteByQuery(Query.ALL, notifiers);

        verifyZeroInteractions(userHandledNotifier);
        verifyZeroInteractions(searchErrorNotifier);
        verifyZeroInteractions(deletionErrorNotifier);
        verifyZeroInteractions(permanentlyDeletedMessageNotifyer);
    }

    @Test
    void deleteByQueryShouldReturnCompletedUponNormalExecution() {
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(testee.deleteByQuery(Query.ALL, notifiers)).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void deleteByQueryShouldUpdateNotifiesdUponNormalExecution() {
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE_OTHER_USER, new ByteArrayInputStream(CONTENT))).block();

        testee.deleteByQuery(Query.ALL, notifiers);

        verify(userHandledNotifier, times(2)).doNotify();
        verify(permanentlyDeletedMessageNotifyer, times(3)).doNotify();
        verifyZeroInteractions(searchErrorNotifier);
        verifyZeroInteractions(deletionErrorNotifier);

        verifyNoMoreInteractions(userHandledNotifier);
        verifyNoMoreInteractions(searchErrorNotifier);
        verifyNoMoreInteractions(deletionErrorNotifier);
        verifyNoMoreInteractions(permanentlyDeletedMessageNotifyer);
    }

    @Test
    void deleteByQueryShouldReturnPartialWhenSearchingFails() {
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        when(vault.search(USER, Query.ALL)).thenReturn(Flux.error(new RuntimeException()));

        assertThat(testee.deleteByQuery(Query.ALL, notifiers)).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void deleteByQueryShouldUpdateNotifiesWhenSearchingFails() {
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE_OTHER_USER, new ByteArrayInputStream(CONTENT))).block();

        when(vault.search(USER, Query.ALL)).thenReturn(Flux.error(new RuntimeException()));

        testee.deleteByQuery(Query.ALL, notifiers);

        verify(userHandledNotifier, times(2)).doNotify();
        verify(searchErrorNotifier, times(1)).doNotify();
        verify(permanentlyDeletedMessageNotifyer, times(1)).doNotify();
        verifyZeroInteractions(deletionErrorNotifier);

        verifyNoMoreInteractions(userHandledNotifier);
        verifyNoMoreInteractions(searchErrorNotifier);
        verifyNoMoreInteractions(deletionErrorNotifier);
        verifyNoMoreInteractions(permanentlyDeletedMessageNotifyer);
    }

    @Test
    void deleteByQueryShouldReturnPartialWhenDeletionFails() {
        when(vault.delete(USER, DELETED_MESSAGE.getMessageId())).thenReturn(Mono.error(new RuntimeException()));
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(testee.deleteByQuery(Query.ALL, notifiers)).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void deleteByQueryShouldUpdateNotifiesWhenDeletionFails() {
        when(vault.delete(USER, DELETED_MESSAGE.getMessageId())).thenReturn(Mono.error(new RuntimeException()));
        Mono.from(vault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(vault.append(DELETED_MESSAGE_OTHER_USER, new ByteArrayInputStream(CONTENT))).block();

        testee.deleteByQuery(Query.ALL, notifiers);

        verify(userHandledNotifier, times(2)).doNotify();
        verify(permanentlyDeletedMessageNotifyer, times(2)).doNotify();
        verify(deletionErrorNotifier, times(1)).doNotify();
        verifyZeroInteractions(searchErrorNotifier);

        verifyNoMoreInteractions(userHandledNotifier);
        verifyNoMoreInteractions(searchErrorNotifier);
        verifyNoMoreInteractions(deletionErrorNotifier);
        verifyNoMoreInteractions(permanentlyDeletedMessageNotifyer);
    }
}