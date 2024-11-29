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

import java.util.Set;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.event.MailboxAggregateId;
import org.apache.james.event.acl.ACLUpdated;
import org.apache.james.eventsourcing.Command;
import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.postgres.mail.eventsourcing.acl.PostgresAclEventSubscriber;
import org.apache.james.mailbox.postgres.mail.eventsourcing.acl.DeleteMailboxCommand;
import org.apache.james.mailbox.postgres.mail.eventsourcing.acl.SetACLCommand;
import org.apache.james.mailbox.postgres.mail.eventsourcing.acl.UpdateACLCommand;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxMapper implements MailboxMapper {
    private final PostgresMailboxDAO postgresMailboxDAO;
    private final EventSourcingSystem eventSourcingSystem;

    public PostgresMailboxMapper(PostgresMailboxDAO postgresMailboxDAO,
                                 EventStore eventStore) {
        this.postgresMailboxDAO = postgresMailboxDAO;
        Set<CommandHandler<? extends Command>> commandHandlers = ImmutableSet.of(new DeleteMailboxCommand.CommandHandler(eventStore),
            new UpdateACLCommand.CommandHandler(eventStore),
            new SetACLCommand.CommandHandler(eventStore));
        Set<Subscriber> subscribers = ImmutableSet.of(new PostgresAclEventSubscriber(postgresMailboxDAO));
        eventSourcingSystem = EventSourcingSystem.fromJava(commandHandlers, subscribers, eventStore);
    }

    @Override
    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        return postgresMailboxDAO.create(mailboxPath,uidValidity);
    }

    @Override
    public Mono<MailboxId> rename(Mailbox mailbox) {
        return postgresMailboxDAO.rename(mailbox);
    }

    @Override
    public Mono<Void> delete(Mailbox mailbox) {
        return postgresMailboxDAO.delete(mailbox.getMailboxId());
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath mailboxName) {
        return postgresMailboxDAO.findMailboxByPath(mailboxName)
            .map(Function.identity());
    }

    @Override
    public Mono<Mailbox> findMailboxById(MailboxId mailboxId) {
        return postgresMailboxDAO.findMailboxById(mailboxId)
            .map(Function.identity());
    }

    @Override
    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        return postgresMailboxDAO.findMailboxWithPathLike(query)
            .map(Function.identity());
    }

    @Override
    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        return postgresMailboxDAO.hasChildren(mailbox, delimiter);
    }

    @Override
    public Flux<Mailbox> list() {
        return postgresMailboxDAO.getAll()
            .map(Function.identity());
    }

    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, MailboxACL.Right right) {
        return postgresMailboxDAO.findMailboxesByUsername(userName)
            .filter(postgresMailbox -> postgresMailbox.getACL().getEntries().get(MailboxACL.EntryKey.createUserEntryKey(userName)).contains(right))
            .map(Function.identity());
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        return Mono.from(eventSourcingSystem.dispatch(new UpdateACLCommand(new MailboxAggregateId(mailbox.getMailboxId()), mailboxACLCommand)))
            .flatMapIterable(events -> events)
            .filter(ACLUpdated.class::isInstance)
            .map(ACLUpdated.class::cast)
            .map(ACLUpdated::getAclDiff)
            .next()
            .switchIfEmpty(Mono.error(() -> new MailboxException("Unable to update ACL")));
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        return Mono.from(eventSourcingSystem.dispatch(new SetACLCommand(new MailboxAggregateId(mailbox.getMailboxId()), mailboxACL)))
            .flatMapIterable(events -> events)
            .filter(ACLUpdated.class::isInstance)
            .map(ACLUpdated.class::cast)
            .map(ACLUpdated::getAclDiff)
            .next()
            .switchIfEmpty(Mono.error(() -> new MailboxException("Unable to set ACL")));
    }

    private Mono<ACLDiff> upsertACL(Mailbox mailbox, MailboxACL oldACL, MailboxACL newACL) {
        return postgresMailboxDAO.upsertACL(mailbox.getMailboxId(), newACL)
            .then(Mono.fromCallable(() -> {
//                mailbox.setACL(newACL);
                return ACLDiff.computeDiff(oldACL, newACL);
            }));
    }
}
