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

package org.apache.james.jmap.api.change;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxAndEmailChange implements JmapChange {
    public static class Factory {
        private final State.Factory stateFactory;
        private final MessageIdManager messageIdManager;
        private final SessionProvider sessionProvider;

        @Inject
        public Factory(State.Factory stateFactory, MessageIdManager messageIdManager, SessionProvider sessionProvider) {
            this.stateFactory = stateFactory;
            this.messageIdManager = messageIdManager;
            this.sessionProvider = sessionProvider;
        }

        public List<JmapChange> fromAdded(MailboxEvents.Added messageAdded, ZonedDateTime now, List<AccountId> sharees) {
            AccountId accountId = AccountId.fromUsername(messageAdded.getUsername());
            State state = stateFactory.generate();
            EmailChange ownerEmailChange = EmailChange.builder()
                .accountId(accountId)
                .state(state)
                .date(now)
                .isDelegated(false)
                .isDelivery(messageAdded.isDelivery())
                .created(messageAdded.getMessageIds())
                .build();

            MailboxChange ownerMailboxChange = MailboxChange.builder()
                .accountId(AccountId.fromUsername(messageAdded.getUsername()))
                .state(state)
                .date(now)
                .isCountChange(true)
                .shared(false)
                .updated(ImmutableList.of(messageAdded.getMailboxId()))
                .build();

            MailboxAndEmailChange ownerChange = new MailboxAndEmailChange(accountId, ownerEmailChange, ownerMailboxChange);

            Stream<MailboxAndEmailChange> shareeChanges = sharees.stream()
                .map(shareeId -> new MailboxAndEmailChange(shareeId,
                        EmailChange.builder()
                            .accountId(shareeId)
                            .state(state)
                            .date(now)
                            .isDelegated(true)
                            .isDelivery(messageAdded.isDelivery())
                            .created(messageAdded.getMessageIds())
                            .build(),
                        MailboxChange.builder()
                            .accountId(shareeId)
                            .state(state)
                            .date(now)
                            .isCountChange(true)
                            .shared(true)
                            .updated(ImmutableList.of(messageAdded.getMailboxId()))
                            .build()));

            return Stream.concat(Stream.of(ownerChange), shareeChanges)
                .collect(ImmutableList.toImmutableList());
        }

        public List<JmapChange> fromFlagsUpdated(MailboxEvents.FlagsUpdated messageFlagUpdated, ZonedDateTime now, List<AccountId> sharees) {
            boolean isSeenChanged = messageFlagUpdated.getUpdatedFlags()
                .stream()
                .anyMatch(flags -> flags.isChanged(Flags.Flag.SEEN));
            AccountId accountId = AccountId.fromUsername(messageFlagUpdated.getUsername());
            EmailChange ownerEmailChange = EmailChange.builder()
                .accountId(accountId)
                .state(stateFactory.generate())
                .date(now)
                .isDelegated(false)
                .updated(messageFlagUpdated.getMessageIds())
                .build();

            if (isSeenChanged) {
                MailboxChange ownerMailboxChange = MailboxChange.builder()
                    .accountId(AccountId.fromUsername(messageFlagUpdated.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(true)
                    .updated(ImmutableList.of(messageFlagUpdated.getMailboxId()))
                    .build();
                MailboxAndEmailChange ownerChange = new MailboxAndEmailChange(accountId, ownerEmailChange, ownerMailboxChange);

                Stream<MailboxAndEmailChange> shareeChanges = sharees.stream()
                    .map(shareeId -> new MailboxAndEmailChange(shareeId,
                        EmailChange.builder()
                            .accountId(shareeId)
                            .state(stateFactory.generate())
                            .date(now)
                            .isDelegated(true)
                            .updated(messageFlagUpdated.getMessageIds())
                            .build(),
                        MailboxChange.builder()
                            .accountId(shareeId)
                            .state(stateFactory.generate())
                            .date(now)
                            .isCountChange(true)
                            .shared(true)
                            .updated(ImmutableList.of(messageFlagUpdated.getMailboxId()))
                            .shared()
                            .build()));

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(ImmutableList.toImmutableList());
            }
            Stream<EmailChange> shareeChanges = sharees.stream()
                .map(shareeId -> EmailChange.builder()
                    .accountId(shareeId)
                    .state(stateFactory.generate())
                    .date(now)
                    .isDelegated(true)
                    .updated(messageFlagUpdated.getMessageIds())
                    .build());

            return Stream.concat(Stream.of(ownerEmailChange), shareeChanges)
                .collect(ImmutableList.toImmutableList());
        }

        public Flux<JmapChange> fromExpunged(MailboxEvents.Expunged expunged, ZonedDateTime now, List<Username> sharees) {
            State state = stateFactory.generate();
            boolean delegated = true;
            Mono<JmapChange> ownerChange = fromExpunged(expunged, now, expunged.getUsername(), state, !delegated);

            Flux<JmapChange> shareeChanges = Flux.fromIterable(sharees)
                .concatMap(shareeId -> fromExpunged(expunged, now, shareeId, state, delegated));

            return Flux.concat(ownerChange, shareeChanges);
        }

        private Mono<JmapChange> fromExpunged(MailboxEvents.Expunged expunged, ZonedDateTime now, Username username, State state, boolean delegated) {
            AccountId accountId = AccountId.fromUsername(username);
            MailboxChange mailboxChange = MailboxChange.builder()
                .accountId(accountId)
                .state(state)
                .date(now)
                .isCountChange(true)
                .shared(delegated)
                .updated(ImmutableList.of(expunged.getMailboxId()))
                .build();

            return Mono.from(messageIdManager.accessibleMessagesReactive(expunged.getMessageIds(), sessionProvider.createSystemSession(username)))
                .<JmapChange>map(accessibleMessageIds -> new MailboxAndEmailChange(accountId,
                    EmailChange.builder()
                        .accountId(AccountId.fromUsername(username))
                        .state(state)
                        .date(now)
                        .isDelegated(delegated)
                        .updated(Sets.intersection(ImmutableSet.copyOf(expunged.getMessageIds()), accessibleMessageIds))
                        .destroyed(Sets.difference(ImmutableSet.copyOf(expunged.getMessageIds()), accessibleMessageIds))
                        .build(), mailboxChange))
                .switchIfEmpty(Mono.<JmapChange>just(mailboxChange));
        }
    }

    private final AccountId accountId;
    private final EmailChange emailChange;
    private final MailboxChange mailboxChange;

    public MailboxAndEmailChange(AccountId accountId, EmailChange emailChange, MailboxChange mailboxChange) {
        Preconditions.checkArgument(accountId.equals(emailChange.getAccountId()));
        Preconditions.checkArgument(accountId.equals(mailboxChange.getAccountId()));

        this.accountId = accountId;
        this.emailChange = emailChange;
        this.mailboxChange = mailboxChange;
    }

    @Override
    public AccountId getAccountId() {
        return accountId;
    }

    public EmailChange getEmailChange() {
        return emailChange;
    }

    public MailboxChange getMailboxChange() {
        return mailboxChange;
    }
}
