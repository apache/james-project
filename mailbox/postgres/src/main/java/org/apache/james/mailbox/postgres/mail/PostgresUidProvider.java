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

package org.apache.james.mailbox.postgres.mail;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.UidProvider;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class PostgresUidProvider implements UidProvider {

    public static class Factory {

        private final PostgresExecutor.Factory executorFactory;

        public Factory(PostgresExecutor.Factory executorFactory) {
            this.executorFactory = executorFactory;
        }

        public PostgresUidProvider create(MailboxSession session) {
            PostgresExecutor postgresExecutor = executorFactory.create(session.getUser().getDomainPart());
            return new PostgresUidProvider(new PostgresMailboxDAO(postgresExecutor));
        }
    }

    private final PostgresMailboxDAO mailboxDAO;

    public PostgresUidProvider(PostgresMailboxDAO mailboxDAO) {
        this.mailboxDAO = mailboxDAO;
    }

    @Override
    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        return nextUid(mailbox.getMailboxId());
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) {
        return lastUidReactive(mailbox).block();
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) throws MailboxException {
        return nextUidReactive(mailboxId)
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Error during Uid update"));
    }

    @Override
    public Mono<Optional<MessageUid>> lastUidReactive(Mailbox mailbox) {
        return mailboxDAO.findLastUidByMailboxId(mailbox.getMailboxId())
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    @Override
    public Mono<MessageUid> nextUidReactive(MailboxId mailboxId) {
        return mailboxDAO.incrementAndGetLastUid(mailboxId, 1)
            .defaultIfEmpty(MessageUid.MIN_VALUE);
    }

    @Override
    public Mono<List<MessageUid>> nextUids(MailboxId mailboxId, int count) {
        Preconditions.checkArgument(count > 0, "Count need to be positive");
        Mono<MessageUid> updateNewLastUid = mailboxDAO.incrementAndGetLastUid(mailboxId, count)
            .defaultIfEmpty(MessageUid.MIN_VALUE);
        return updateNewLastUid.map(lastUid -> range(lastUid, count));
    }

    private List<MessageUid> range(MessageUid higherInclusive, int count) {
        return LongStream.range(higherInclusive.asLong() - count + 1, higherInclusive.asLong() + 1)
            .mapToObj(MessageUid::of)
            .collect(ImmutableList.toImmutableList());
    }

}
