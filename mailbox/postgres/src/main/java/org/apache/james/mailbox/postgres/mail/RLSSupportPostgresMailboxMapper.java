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

import java.time.Duration;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.acl.PositiveUserACLDiff;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class RLSSupportPostgresMailboxMapper extends PostgresMailboxMapper {
    private final PostgresMailboxDAO postgresMailboxDAO;
    private final PostgresMailboxMemberDAO postgresMailboxMemberDAO;

    public RLSSupportPostgresMailboxMapper(PostgresMailboxDAO postgresMailboxDAO, PostgresMailboxMemberDAO postgresMailboxMemberDAO) {
        super(postgresMailboxDAO);
        this.postgresMailboxDAO = postgresMailboxDAO;
        this.postgresMailboxMemberDAO = postgresMailboxMemberDAO;
    }

    @Override
    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, MailboxACL.Right right) {
        return postgresMailboxMemberDAO.findMailboxIdByUsername(userName)
            .collectList()
            .filter(postgresMailboxIds -> !postgresMailboxIds.isEmpty())
            .flatMapMany(postgresMailboxDAO::findMailboxByIds)
            .filter(postgresMailbox -> postgresMailbox.getACL().getEntries().get(MailboxACL.EntryKey.createUserEntryKey(userName)).contains(right))
            .map(Function.identity());
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        return postgresMailboxDAO.getACL(mailbox.getMailboxId())
            .flatMap(pairMailboxACLAndVersion -> {
                try {
                    MailboxACL newACL = pairMailboxACLAndVersion.getLeft().apply(mailboxACLCommand);
                    return postgresMailboxDAO.upsertACL(mailbox.getMailboxId(), newACL, pairMailboxACLAndVersion.getRight())
                        .thenReturn(ACLDiff.computeDiff(pairMailboxACLAndVersion.getLeft(), newACL));
                } catch (UnsupportedRightException e) {
                    throw new RuntimeException(e);
                }
            }).retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                .filter(throwable -> throwable instanceof PostgresACLUpsertException))
            .flatMap(aclDiff -> updateMembersOfMailbox(mailbox, new PositiveUserACLDiff(aclDiff))
                .thenReturn(aclDiff));
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        return postgresMailboxDAO.getACL(mailbox.getMailboxId())
            .flatMap(pairMailboxACLAndVersion ->
                postgresMailboxDAO.upsertACL(mailbox.getMailboxId(), mailboxACL, pairMailboxACLAndVersion.getRight())
                    .thenReturn(ACLDiff.computeDiff(pairMailboxACLAndVersion.getLeft(), mailboxACL))).retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                .filter(throwable -> throwable instanceof PostgresACLUpsertException))
            .flatMap(aclDiff -> updateMembersOfMailbox(mailbox, new PositiveUserACLDiff(aclDiff))
                .thenReturn(aclDiff));
    }

    private Mono<Void> updateMembersOfMailbox(Mailbox mailbox, PositiveUserACLDiff userACLDiff) {
        return postgresMailboxMemberDAO.delete(PostgresMailboxId.class.cast(mailbox.getMailboxId()),
                userACLDiff.removedEntries().map(entry -> Username.of(entry.getKey().getName())).toList())
            .then(postgresMailboxMemberDAO.insert(PostgresMailboxId.class.cast(mailbox.getMailboxId()),
                userACLDiff.addedEntries().map(entry -> Username.of(entry.getKey().getName())).toList()));
    }
}
