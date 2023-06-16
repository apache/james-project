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

package org.apache.james.jmap.event;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxRenamed;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxPath;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PropagateLookupRightListener implements EventListener.ReactiveGroupEventListener {
    public static class PropagateLookupRightListenerGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PropagateLookupRightListener.class);
    private static final Group GROUP = new PropagateLookupRightListenerGroup();

    private final RightManager rightManager;
    private final MailboxManager mailboxManager;

    @Inject
    public PropagateLookupRightListener(RightManager rightManager, MailboxManager mailboxManager) {
        this.rightManager = rightManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxACLUpdated || event instanceof MailboxRenamed;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxACLUpdated) {
            return updateLookupRightOnParent((MailboxACLUpdated) event);
        } else if (event instanceof MailboxRenamed) {
            return updateLookupRightOnParent((MailboxRenamed) event);
        }
        return Mono.empty();
    }

    private Mono<Void> updateLookupRightOnParent(MailboxACLUpdated aclUpdateEvent) {
        MailboxSession mailboxSession = createMailboxSession(aclUpdateEvent);
        return Mono.from(mailboxManager.getMailboxReactive(aclUpdateEvent.getMailboxId(), mailboxSession))
            .map(MessageManager::getMailboxPath)
            .flatMapIterable(mailboxPath -> mailboxPath.getParents(mailboxSession.getPathDelimiter()))
            .flatMap(parentPath -> updateLookupRight(mailboxSession, parentPath, aclUpdateEvent.getAclDiff()))
            .then();
    }

    private Mono<Void> updateLookupRightOnParent(MailboxRenamed mailboxRenamed) {
        MailboxSession mailboxSession = createMailboxSession(mailboxRenamed);
        return Mono.from(rightManager.listRightsReactive(mailboxRenamed.getNewPath(), mailboxSession))
            .onErrorResume(MailboxNotFoundException.class, e -> {
                LOGGER.info("Mailbox {} not found, skip lookup right update", mailboxRenamed.getNewPath());
                return Mono.empty();
            })
            .flatMapIterable(acl -> acl.getEntries().entrySet())
            .map(mapEntry -> new Entry(mapEntry.getKey(), mapEntry.getValue()))
            .filter(updateLookupRightPredicate())
            .collectList()
            .flatMap(entries -> Flux.fromIterable(mailboxRenamed.getNewPath().getParents(mailboxSession.getPathDelimiter()))
                .flatMap(parentPath -> updateLookupRight(mailboxSession, parentPath, entries))
                .then());
    }

    private Mono<Void> updateLookupRight(MailboxSession session, MailboxPath mailboxPath, ACLDiff aclDiff) {
        return updateLookupRight(session, mailboxPath, Stream.concat(aclDiff.addedEntries(), aclDiff.changedEntries()));
    }

    private Mono<Void> updateLookupRight(MailboxSession session, MailboxPath mailboxPath, Stream<Entry> entryStream) {
        return Flux.fromStream(entryStream)
            .filter(updateLookupRightPredicate())
            .flatMap(entry -> applyLookupRight(session, mailboxPath, entry.getKey()))
            .then();
    }

    private Predicate<Entry> updateLookupRightPredicate() {
        return entry -> !entry.getKey().isNegative() && entry.getValue().contains(Right.Lookup);
    }

    private Mono<Void> updateLookupRight(MailboxSession session, MailboxPath mailboxPath, List<Entry> entries) {
        return Flux.fromIterable(entries)
            .flatMap(entry -> applyLookupRight(session, mailboxPath, entry.getKey()))
            .then();
    }

    private MailboxSession createMailboxSession(Event event) {
        return mailboxManager.createSystemSession(event.getUsername());
    }

    private Mono<Void> applyLookupRight(MailboxSession session, MailboxPath mailboxPath, MailboxACL.EntryKey entryKey) {
        return Mono.fromCallable(() -> MailboxACL.command()
                .rights(Right.Lookup)
                .key(entryKey)
                .asAddition())
            .flatMap(aclCommand -> Mono.from(rightManager.applyRightsCommandReactive(mailboxPath, aclCommand, session)));
    }
}
