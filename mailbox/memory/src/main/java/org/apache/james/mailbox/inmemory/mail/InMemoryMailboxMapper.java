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
package org.apache.james.mailbox.inmemory.mail;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InMemoryMailboxMapper implements MailboxMapper {
    
    private static final int INITIAL_SIZE = 128;
    private final ConcurrentHashMap<MailboxPath, Mailbox> mailboxesByPath;
    private final AtomicLong mailboxIdGenerator = new AtomicLong();

    public InMemoryMailboxMapper() {
        mailboxesByPath = new ConcurrentHashMap<>(INITIAL_SIZE);
    }

    @Override
    public Mono<Void> delete(Mailbox mailbox) {
        return Mono.fromRunnable(() -> mailboxesByPath.remove(mailbox.generateAssociatedPath()));
    }

    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(mailboxesByPath::clear);
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath path) {
        return Mono.defer(() -> Mono.justOrEmpty(mailboxesByPath.get(path)))
            .map(Mailbox::new);
    }

    @Override
    public Mono<Mailbox> findMailboxById(MailboxId id) {
        return Mono.fromCallable(mailboxesByPath::values)
            .flatMapIterable(Function.identity())
            .filter(mailbox -> mailbox.getMailboxId().equals(id))
            .next()
            .map(Mailbox::new)
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(id)));
    }

    @Override
    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        return Mono.fromCallable(mailboxesByPath::values)
                .flatMapIterable(Function.identity())
                .filter(query::matches)
                .map(Mailbox::new);
    }

    @Override
    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        InMemoryId id = InMemoryId.of(mailboxIdGenerator.incrementAndGet());
        Mailbox mailbox = new Mailbox(mailboxPath, uidValidity, id);

        return saveMailbox(mailbox)
            .thenReturn(mailbox);
    }

    @Override
    public Mono<MailboxId> rename(Mailbox mailbox) {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        InMemoryId id = (InMemoryId) mailbox.getMailboxId();
        return findMailboxById(id)
            .flatMap(mailboxWithPreviousName -> saveMailbox(mailbox)
                .then(Mono.fromCallable(() -> mailboxesByPath.remove(mailboxWithPreviousName.generateAssociatedPath()))))
            .thenReturn(mailbox.getMailboxId());
    }

    private Mono<Void> saveMailbox(Mailbox mailbox) {
        return Mono.defer(() -> Mono.justOrEmpty(mailboxesByPath.putIfAbsent(mailbox.generateAssociatedPath(), mailbox)))
            .flatMap(ignored -> Mono.error(new MailboxExistsException(mailbox.getName())));
    }

    @Override
    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        String mailboxName = mailbox.getName() + delimiter;
        return Mono.fromCallable(mailboxesByPath::values)
            .flatMapIterable(Function.identity())
            .filter(box -> belongsToSameUser(mailbox, box) && box.getName().startsWith(mailboxName))
            .hasElements();
    }

    private boolean belongsToSameUser(Mailbox mailbox, Mailbox otherMailbox) {
        return Objects.equal(mailbox.getNamespace(), otherMailbox.getNamespace())
            && Objects.equal(mailbox.getUser(), otherMailbox.getUser());
    }

    @Override
    public Flux<Mailbox> list() {
        return Mono.fromCallable(mailboxesByPath::values)
            .flatMapIterable(Function.identity());
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        return Mono.fromCallable(() -> {
            MailboxACL oldACL = mailbox.getACL();
            MailboxACL newACL = mailbox.getACL().apply(mailboxACLCommand);
            mailboxesByPath.get(mailbox.generateAssociatedPath()).setACL(newACL);
            return ACLDiff.computeDiff(oldACL, newACL);
        });
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        return Mono.fromCallable(() -> {
            MailboxACL oldMailboxAcl = mailbox.getACL();
            mailboxesByPath.get(mailbox.generateAssociatedPath()).setACL(mailboxACL);
            return ACLDiff.computeDiff(oldMailboxAcl, mailboxACL);
        });
    }

    @Override
    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, Right right) {
        return Mono.fromCallable(mailboxesByPath::values)
            .flatMapIterable(Function.identity())
            .filter(mailbox -> hasRightOn(mailbox, userName, right));
    }

    private Boolean hasRightOn(Mailbox mailbox, Username userName, Right right) {
        return Optional.ofNullable(
            mailbox.getACL()
                .ofPositiveNameType(NameType.user)
                .get(MailboxACL.EntryKey.createUserEntryKey(userName)))
            .map(rights -> rights.contains(right))
            .orElse(false);
    }
}
