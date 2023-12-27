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

import org.apache.james.backends.postgres.utils.DefaultPostgresExecutor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.ModSeqProvider;

import reactor.core.publisher.Mono;

public class PostgresModSeqProvider implements ModSeqProvider {

    public static class Factory {

        private final DefaultPostgresExecutor.Factory executorFactory;

        public Factory(DefaultPostgresExecutor.Factory executorFactory) {
            this.executorFactory = executorFactory;
        }

        public PostgresModSeqProvider create(MailboxSession session) {
            DefaultPostgresExecutor postgresExecutor = executorFactory.create(session.getUser().getDomainPart());
            return new PostgresModSeqProvider(new PostgresMailboxDAO(postgresExecutor));
        }
    }

    private final PostgresMailboxDAO mailboxDAO;

    public PostgresModSeqProvider(PostgresMailboxDAO mailboxDAO) {
        this.mailboxDAO = mailboxDAO;
    }

    @Override
    public ModSeq nextModSeq(Mailbox mailbox) throws MailboxException {
        return nextModSeq(mailbox.getMailboxId());
    }

    @Override
    public ModSeq nextModSeq(MailboxId mailboxId) throws MailboxException {
        return nextModSeqReactive(mailboxId)
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Can not retrieve modseq for " + mailboxId));
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) {
        return highestModSeqReactive(mailbox).block();
    }

    @Override
    public Mono<ModSeq> highestModSeqReactive(Mailbox mailbox) {
        return getHighestModSeq(mailbox.getMailboxId());
    }

    private Mono<ModSeq> getHighestModSeq(MailboxId mailboxId) {
        return mailboxDAO.findHighestModSeqByMailboxId(mailboxId)
            .defaultIfEmpty(ModSeq.first());
    }

    @Override
    public ModSeq highestModSeq(MailboxId mailboxId) {
        return getHighestModSeq(mailboxId).block();
    }

    @Override
    public Mono<ModSeq> nextModSeqReactive(MailboxId mailboxId) {
        return mailboxDAO.incrementAndGetModSeq(mailboxId)
            .defaultIfEmpty(ModSeq.first());
    }
}
