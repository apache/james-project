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

package org.apache.james.adapter.mailbox;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxUserDeletionTaskStep implements DeleteUserDataTaskStep {
    private final StoreMailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;

    @Inject
    public MailboxUserDeletionTaskStep(StoreMailboxManager mailboxManager, SubscriptionManager subscriptionManager) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public StepName name() {
        return new StepName("MailboxUserDeletionTaskStep");
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Publisher<Void> deleteUserData(Username username) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return getAllMailboxesOfUser(mailboxSession)
            .concatMap(mailbox -> deleteMailbox(mailboxSession, mailbox)
                .then(deleteSubscription(mailboxSession, mailbox)))
            .then(getSharedMailboxesOfUser(mailboxSession)
                .flatMap(sharedMailbox -> revokeACLs(username, sharedMailbox)
                    .then(deleteSubscription(mailboxSession, sharedMailbox)))
                .then())
            .doFinally(any -> mailboxManager.endProcessingRequest(mailboxSession));
    }

    private Flux<MailboxMetaData> getAllMailboxesOfUser(MailboxSession mailboxSession) {
        MailboxQuery queryMailboxesOfUser = MailboxQuery.builder()
            .privateNamespace()
            .user(mailboxSession.getUser())
            .build();

        return mailboxManager.search(queryMailboxesOfUser, MailboxManager.MailboxSearchFetchType.Minimal, mailboxSession);
    }

    private Flux<MailboxMetaData> getSharedMailboxesOfUser(MailboxSession mailboxSession) {
        return mailboxManager.search(MailboxQuery.builder().matchesAllMailboxNames().build(), mailboxSession)
            .filter(mailbox -> !mailbox.getPath().getUser().equals(mailboxSession.getUser()));
    }

    private Mono<Mailbox> deleteMailbox(MailboxSession mailboxSession, MailboxMetaData mailbox) {
        return mailboxManager.deleteMailboxReactive(mailbox.getId(), mailboxSession);
    }

    private Mono<Void> deleteSubscription(MailboxSession mailboxSession, MailboxMetaData mailbox) {
        return Mono.from(subscriptionManager.unsubscribeReactive(mailbox.getPath(), mailboxSession));
    }

    private Mono<Void> revokeACLs(Username username, MailboxMetaData mailbox) {
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailbox.getPath().getUser());
        MailboxACL.Rfc4314Rights rights = Optional.ofNullable(mailbox.getMailbox().getACL().getEntries().get(MailboxACL.EntryKey.createUserEntryKey(username)))
            .orElse(MailboxACL.NO_RIGHTS);

        return Mono.fromRunnable(Throwing.runnable(() -> mailboxManager.applyRightsCommand(mailbox.getId(), MailboxACL.command().rights(rights).forUser(username).asRemoval(), ownerSession)))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then()
            .doFinally(any -> mailboxManager.endProcessingRequest(ownerSession));
    }

}
