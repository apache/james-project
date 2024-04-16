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

import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.util.streams.Limit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEmailQueryView implements EmailQueryView {
    private PostgresEmailQueryViewDAO emailQueryViewDAO;

    @Inject
    public PostgresEmailQueryView(PostgresEmailQueryViewDAO emailQueryViewDAO) {
        this.emailQueryViewDAO = emailQueryViewDAO;
    }

    @Override
    public Flux<MessageId> listMailboxContentSortedBySentAt(MailboxId mailboxId, Limit limit) {
        return emailQueryViewDAO.listMailboxContentSortedBySentAt(PostgresMailboxId.class.cast(mailboxId), limit);
    }

    @Override
    public Flux<MessageId> listMailboxContentSortedByReceivedAt(MailboxId mailboxId, Limit limit) {
        return emailQueryViewDAO.listMailboxContentSortedByReceivedAt(PostgresMailboxId.class.cast(mailboxId), limit);
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceAfterSortedBySentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        return emailQueryViewDAO.listMailboxContentSinceAfterSortedBySentAt(PostgresMailboxId.class.cast(mailboxId), since, limit);
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceAfterSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        return emailQueryViewDAO.listMailboxContentSinceAfterSortedByReceivedAt(PostgresMailboxId.class.cast(mailboxId), since, limit);
    }

    @Override
    public Flux<MessageId> listMailboxContentBeforeSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        return emailQueryViewDAO.listMailboxContentBeforeSortedByReceivedAt(PostgresMailboxId.class.cast(mailboxId), since, limit);
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceSentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        return emailQueryViewDAO.listMailboxContentSinceSentAt(PostgresMailboxId.class.cast(mailboxId), since, limit);
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId, MessageId messageId) {
        return emailQueryViewDAO.delete(PostgresMailboxId.class.cast(mailboxId), PostgresMessageId.class.cast(messageId));
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        return emailQueryViewDAO.delete(PostgresMailboxId.class.cast(mailboxId));
    }

    @Override
    public Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, MessageId messageId) {
        return emailQueryViewDAO.save(PostgresMailboxId.class.cast(mailboxId), sentAt, receivedAt, PostgresMessageId.class.cast(messageId));
    }
}
