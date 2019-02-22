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

import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.Query;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryDeletedMessagesVault implements DeletedMessageVault {
    private final Table<User, MessageId, DeletedMessage> table;

    MemoryDeletedMessagesVault() {
        table = HashBasedTable.create();
    }

    @Override
    public Mono<Void> append(User user, DeletedMessage deletedMessage) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(deletedMessage);

        table.put(user, deletedMessage.getMessageId(), deletedMessage);
        return Mono.empty();
    }

    @Override
    public Mono<Void> delete(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);

        table.remove(user, messageId);
        return Mono.empty();
    }

    @Override
    public Flux<DeletedMessage> search(User user, Query query) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(query);
        Preconditions.checkArgument(query.getCriteria().isEmpty(), "Search is not supported yet...");

        return Flux.fromIterable(table.row(user).values());
    }
}
