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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;

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
            EmailChange.Builder emailChangeBuilder = EmailChange.builder()
                .accountId(accountId)
                .state(stateFactory.generate())
                .date(now)
                .isShared(false)
                .isDelivery(messageAdded.isDelivery());
            MailboxChange mailboxChange = MailboxChange.builder()
                .accountId(accountId)
                .state(stateFactory.generate())
                .date(now)
                .isCountChange(true)
                .updated(ImmutableList.of(messageAdded.getMailboxId()))
                .build();

            if (messageAdded.isAppended()) {
                return new MailboxAndEmailChange(accountId,
                    emailChangeBuilder
                        .created(messageAdded.getMessageIds())
                        .build(),
                    mailboxChange)
                    .propagateToSharee(sharees, stateFactory);
            }
            return new MailboxAndEmailChange(accountId,
                emailChangeBuilder
                    .updated(messageAdded.getMessageIds())
                    .build(),
                mailboxChange)
                .propagateToSharee(sharees, stateFactory);
        }

        public List<JmapChange> fromFlagsUpdated(MailboxEvents.FlagsUpdated messageFlagUpdated, ZonedDateTime now, List<AccountId> sharees) {
            return flagUpdateChange(messageFlagUpdated, now)
                .map(change -> change.propagateToSharee(sharees, stateFactory))
                .orElse(ImmutableList.of());
        }

        private Optional<JmapChange> flagUpdateChange(MailboxEvents.FlagsUpdated event, ZonedDateTime now) {
            AccountId accountId = AccountId.fromUsername(event.getUsername());
            EmailChange.Builder emailChangeBuilder = EmailChange.builder()
                .accountId(accountId)
                .state(stateFactory.generate())
                .date(now)
                .isShared(false);
            ImmutableSet.Builder<MailboxId> updatedMailboxes = ImmutableSet.builder();

            event.getUpdatedFlags().forEach(updatedFLags -> handleFlagUpdate(updatedFLags, event.getMailboxId())
                .accept(emailChangeBuilder, updatedMailboxes));

            return new MailboxAndEmailChange(
                accountId,
                emailChangeBuilder.build(),
                MailboxChange.builder()
                    .accountId(accountId)
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(true)
                    .updated(ImmutableList.copyOf(updatedMailboxes.build()))
                    .build())
                .normalize();
        }

        private BiConsumer<EmailChange.Builder, ImmutableSet.Builder<MailboxId>> handleFlagUpdate(UpdatedFlags updatedFlags, MailboxId mailboxId) {
            return (emailChangeBuilder, mailboxChangeBuilder) -> {
                MessageId messageId = updatedFlags.getMessageId().get();
                if (updatedFlags.isModifiedToSet(Flags.Flag.DELETED)) {
                    emailChangeBuilder.destroyed(messageId);
                    mailboxChangeBuilder.add(mailboxId);
                    return;
                }
                if (updatedFlags.isModifiedToUnset(Flags.Flag.DELETED)) {
                    emailChangeBuilder.created(messageId);
                    mailboxChangeBuilder.add(mailboxId);
                    return;
                }
                if (updatedFlags.getOldFlags().contains(Flags.Flag.DELETED)) {
                    return;
                }
                if (!updatedFlags.flagsChangedIgnoringRecent()) {
                    return;
                }
                emailChangeBuilder.updated(messageId);
                if (updatedFlags.isChanged(Flags.Flag.SEEN)) {
                    mailboxChangeBuilder.add(mailboxId);
                }
            };
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
            ImmutableSet<MessageId> changedMessageIds = expunged.getExpunged().values()
                .stream()
                .filter(metadata -> !metadata.getFlags().contains(Flags.Flag.DELETED))
                .map(MessageMetaData::getMessageId)
                .collect(ImmutableSet.toImmutableSet());

            if (changedMessageIds.isEmpty()) {
                return Mono.empty();
            }

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
                        .isShared(delegated)
                        .updated(Sets.intersection(changedMessageIds, accessibleMessageIds))
                        .destroyed(Sets.difference(changedMessageIds, accessibleMessageIds))
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

    public Optional<JmapChange> normalize() {
        if (isNoop()) {
            return Optional.empty();
        }
        if (mailboxChange.isNoop()) {
            return Optional.of(emailChange);
        }
        return Optional.of(this);
    }

    @Override
    public boolean isNoop() {
        return mailboxChange.isNoop() && emailChange.isNoop();
    }

    public MailboxAndEmailChange forSharee(AccountId accountId, Supplier<State> state) {
        return new MailboxAndEmailChange(accountId,
            emailChange.forSharee(accountId, state),
            mailboxChange.forSharee(accountId, state));
    }
}
