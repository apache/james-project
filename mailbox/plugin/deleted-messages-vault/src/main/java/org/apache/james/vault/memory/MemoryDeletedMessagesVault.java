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

package org.apache.james.vault.memory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.RetentionConfiguration;
import org.apache.james.vault.search.Query;
import org.apache.james.vault.utils.DeleteByQueryExecutor;
import org.apache.james.vault.utils.VaultGarbageCollectionTask;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryDeletedMessagesVault implements DeletedMessageVault {
    private final RetentionConfiguration retentionConfiguration;
    private final Table<User, MessageId, Pair<DeletedMessage, byte[]>> table;
    private final Clock clock;
    private DeleteByQueryExecutor deleteByQueryExecutor;

    public MemoryDeletedMessagesVault(RetentionConfiguration retentionConfiguration, Clock clock) {
        this.deleteByQueryExecutor = new DeleteByQueryExecutor(this, this::usersWithVault);
        this.retentionConfiguration = retentionConfiguration;
        this.clock = clock;
        this.table = HashBasedTable.create();
    }

    @Override
    public Mono<Void> append(User user, DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(deletedMessage);

        synchronized (table) {
            try {
                table.put(user, deletedMessage.getMessageId(),
                    Pair.of(deletedMessage, IOUtils.toByteArray(mimeMessage)));
                return Mono.empty();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Mono<Void> delete(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);

        synchronized (table) {
            table.remove(user, messageId);
            return Mono.empty();
        }
    }

    @Override
    public synchronized Mono<InputStream> loadMimeMessage(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);


        synchronized (table) {
            return Mono.justOrEmpty(Optional.ofNullable(table.get(user, messageId)))
                .map(Pair::getRight)
                .map(ByteArrayInputStream::new);
        }
    }

    @Override
    public synchronized Flux<DeletedMessage> search(User user, Query query) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(query);

        return listAll(user)
            .filter(query.toPredicate());
    }

    @VisibleForTesting
    public Publisher<User> usersWithVault() {
        return Flux.defer(
            () -> {
                synchronized (table) {
                    return Flux.fromIterable(ImmutableList.copyOf(table.rowKeySet()));
                }
            });
    }

    public Task deleteExpiredMessagesTask() {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ZonedDateTime beginningOfRetentionPeriod = now.minus(retentionConfiguration.getRetentionPeriod());

        return new VaultGarbageCollectionTask(
            getDeleteByQueryExecutor(),
            beginningOfRetentionPeriod);
    }

    @VisibleForTesting
    public DeleteByQueryExecutor getDeleteByQueryExecutor() {
        return deleteByQueryExecutor;
    }

    private Flux<DeletedMessage> listAll(User user) {
        synchronized (table) {
            return Flux.fromIterable(ImmutableList.copyOf(table.row(user).values()))
                .map(Pair::getLeft);
        }
    }
}
