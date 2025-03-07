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

package org.apache.james.jmap.postgres.projections;

import java.time.ZonedDateTime;

import jakarta.inject.Inject;

import org.apache.james.jmap.api.projections.ThreadQueryView;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.util.streams.Limit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadQueryView implements ThreadQueryView {
    private PostgresThreadQueryViewDAO threadQueryViewDAO;

    @Inject
    public PostgresThreadQueryView(PostgresThreadQueryViewDAO threadQueryViewDAO) {
        this.threadQueryViewDAO = threadQueryViewDAO;
    }

    @Override
    public Flux<ThreadId> listLatestThreadIdsSortedByReceivedAt(MailboxId mailboxId, Limit limit) {
        return threadQueryViewDAO.listLatestThreadIdsSortedByReceivedAt(PostgresMailboxId.class.cast(mailboxId), limit);
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId, ThreadId threadId) {
        return null;
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        return null;
    }

    @Override
    public Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, ThreadId threadId) {
        return null;
    }
}
