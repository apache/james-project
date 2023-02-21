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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final StoreMailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;

    @Inject
    public MailboxUsernameChangeTaskStep(StoreMailboxManager mailboxManager, SubscriptionManager subscriptionManager) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public StepName name() {
        return new StepName("MailboxUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        MailboxSession fromSession = mailboxManager.createSystemSession(oldUsername);
        MailboxSession toSession = mailboxManager.createSystemSession(newUsername);

        MailboxQuery queryUser = MailboxQuery.builder()
            .privateNamespace()
            .user(fromSession.getUser())
            .build();

        return mailboxManager.search(queryUser, MailboxManager.MailboxSearchFetchType.Minimal, fromSession)
            // Only keep top level, rename takes care of sub mailboxes
            .filter(mailbox -> mailbox.getPath().getHierarchyLevels(fromSession.getPathDelimiter()).size() == 1)
            .concatMap(mailbox -> migrateMailbox(fromSession, toSession, mailbox));
    }

    private Mono<Void> migrateMailbox(MailboxSession fromSession, MailboxSession toSession, org.apache.james.mailbox.model.MailboxMetaData mailbox) {
        MailboxPath renamedPath = mailbox.getPath().withUser(toSession.getUser());
        return mailboxManager.renameMailboxReactive(mailbox.getPath(), renamedPath,
            MailboxManager.RenameOption.RENAME_SUBSCRIPTIONS,
            fromSession, toSession)
            .then(renameSubscriptionsForDelegatee(mailbox, renamedPath))
            .then();
    }

    private Mono<Void> renameSubscriptionsForDelegatee(MailboxMetaData mailbox, MailboxPath renamedPath) {
        return Flux.fromIterable(mailbox.getResolvedAcls().getEntries().entrySet())
            .filter(entry -> entry.getKey().getNameType() == MailboxACL.NameType.user && !entry.getKey().isNegative())
            .map(entry -> Username.of(entry.getKey().getName()))
            .concatMap(Throwing.function(userWithAccess ->
                Flux.from(subscriptionManager.subscriptionsReactive(mailboxManager.createSystemSession(userWithAccess)))
                    .filter(subscribedMailbox -> subscribedMailbox.equals(mailbox.getPath()))
                    .concatMap(any -> Mono.from(subscriptionManager.subscribeReactive(renamedPath, mailboxManager.createSystemSession(userWithAccess)))
                    .then(Mono.from(subscriptionManager.unsubscribeReactive(mailbox.getPath(), mailboxManager.createSystemSession(userWithAccess)))))))
            .then();
    }
}
