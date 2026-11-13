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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

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

        /**
         * @param sharees users seeing the mailbox: they receive the mailbox part of the change
         * @param emailSharees users allowed to read the messages of the mailbox: they also receive the email part of the change.
         *                     Should be a subset of sharees.
         */
        public List<JmapChange> fromAdded(MailboxEvents.Added messageAdded, ZonedDateTime now, List<AccountId> sharees, Set<AccountId> emailSharees) {
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
                return propagateToSharees(new MailboxAndEmailChange(accountId,
                    emailChangeBuilder
                        .created(messageAdded.getMessageIds())
                        .build(),
                    mailboxChange),
                    sharees, emailSharees);
            }
            return propagateToSharees(new MailboxAndEmailChange(accountId,
                emailChangeBuilder
                    .updated(messageAdded.getMessageIds())
                    .build(),
                mailboxChange),
                sharees, emailSharees);
        }

        /**
         * @param sharees users seeing the mailbox: they receive the mailbox part of the change
         * @param emailSharees users allowed to read the messages of the mailbox: they also receive the email part of the change.
         *                     Should be a subset of sharees.
         */
        public List<JmapChange> fromFlagsUpdated(MailboxEvents.FlagsUpdated messageFlagUpdated, ZonedDateTime now, List<AccountId> sharees, Set<AccountId> emailSharees) {
            return flagUpdateChange(messageFlagUpdated, now)
                .map(change -> propagateToSharees(change, sharees, emailSharees))
                .orElse(ImmutableList.of());
        }

        private ImmutableList<JmapChange> propagateToSharees(JmapChange change, List<AccountId> sharees, Set<AccountId> emailSharees) {
            if (change.isNoop()) {
                return ImmutableList.of();
            }
            return Stream.concat(Stream.of(change), sharees.stream()
                    .flatMap(sharee -> forSharee(change, sharee, emailSharees.contains(sharee)).stream()))
                .collect(ImmutableList.toImmutableList());
        }

        private Optional<JmapChange> forSharee(JmapChange change, AccountId sharee, boolean canReadEmails) {
            if (canReadEmails) {
                return Optional.of(change.forSharee(sharee, stateFactory::generate));
            }
            if (change instanceof MailboxAndEmailChange mailboxAndEmailChange) {
                return Optional.of(mailboxAndEmailChange.getMailboxChange().forSharee(sharee, stateFactory::generate));
            }
            if (change instanceof MailboxChange mailboxChange) {
                return Optional.of(mailboxChange.forSharee(sharee, stateFactory::generate));
            }
            // Email only change: not propagated to sharees that can not read the messages
            return Optional.empty();
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

        /**
         * @param sharees users seeing the mailbox: they receive the mailbox part of the change
         * @param emailSharees users allowed to read the messages of the mailbox: they also receive the email part of the change.
         *                     Should be a subset of sharees.
         */
        public Flux<JmapChange> fromExpunged(MailboxEvents.Expunged expunged, ZonedDateTime now, List<Username> sharees, Set<Username> emailSharees) {
            State state = stateFactory.generate();
            boolean delegated = true;
            boolean canReadEmails = true;
            Mono<JmapChange> ownerChange = fromExpunged(expunged, now, expunged.getUsername(), state, !delegated, canReadEmails);

            Flux<JmapChange> shareeChanges = Flux.fromIterable(sharees)
                .concatMap(shareeId -> fromExpunged(expunged, now, shareeId, state, delegated, emailSharees.contains(shareeId)));

            return Flux.concat(ownerChange, shareeChanges);
        }

        private Mono<JmapChange> fromExpunged(MailboxEvents.Expunged expunged, ZonedDateTime now, Username username, State state, boolean delegated, boolean canReadEmails) {
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

            if (!canReadEmails) {
                return Mono.just(mailboxChange);
            }

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
