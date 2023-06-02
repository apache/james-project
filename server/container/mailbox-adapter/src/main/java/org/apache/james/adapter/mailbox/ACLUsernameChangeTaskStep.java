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
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ACLUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final MailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;

    @Inject
    public ACLUsernameChangeTaskStep(MailboxManager mailboxManager, SubscriptionManager subscriptionManager) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public StepName name() {
        return new StepName("ACLUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        MailboxSession oldSession = mailboxManager.createSystemSession(oldUsername);
        MailboxSession newSession = mailboxManager.createSystemSession(newUsername);
        return mailboxManager.search(MailboxQuery.builder().matchesAllMailboxNames().build(), oldSession)
            .filter(mailbox -> !mailbox.getPath().getUser().equals(oldUsername))
            .concatMap(mailbox -> migrateACLs(oldUsername, newUsername, mailbox))
            .then(updateSubscriptionsOnDeletedMailboxes(oldUsername, oldSession, newSession))
            .doFinally(any -> mailboxManager.endProcessingRequest(oldSession))
            .doFinally(any -> mailboxManager.endProcessingRequest(newSession));
    }

    private Mono<Void> updateSubscriptionsOnDeletedMailboxes(Username oldUsername, MailboxSession oldSession, MailboxSession newSession) {
        try {
            return Flux.from(subscriptionManager.subscriptionsReactive(oldSession))
                .filter(subscription -> !subscription.getUser().equals(oldUsername))
                .concatMap(subscription -> Mono.from(subscriptionManager.subscribeReactive(subscription, newSession))
                    .then(Mono.from(subscriptionManager.unsubscribeReactive(subscription, oldSession))))
                .then();
        } catch (SubscriptionException e) {
            return Mono.error(e);
        }
    }

    private Publisher<? extends Void> migrateACLs(Username oldUsername, Username newUsername, MailboxMetaData mailbox) {
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailbox.getPath().getUser());
        MailboxACL.Rfc4314Rights rights = Optional.ofNullable(mailbox.getMailbox().getACL().getEntries().get(MailboxACL.EntryKey.createUserEntryKey(oldUsername)))
            .orElse(MailboxACL.NO_RIGHTS);

        return Mono.fromRunnable(Throwing.runnable(() -> mailboxManager.applyRightsCommand(mailbox.getId(), MailboxACL.command().rights(rights).forUser(newUsername).asAddition(), ownerSession)))
            .then(Mono.fromRunnable(Throwing.runnable(() -> mailboxManager.applyRightsCommand(mailbox.getId(), MailboxACL.command().rights(rights).forUser(oldUsername).asRemoval(), ownerSession))))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then()
            .doFinally(any -> mailboxManager.endProcessingRequest(ownerSession));
    }
}
