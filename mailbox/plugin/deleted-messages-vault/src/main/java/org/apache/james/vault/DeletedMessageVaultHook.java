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

package org.apache.james.vault;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class DeletedMessageVaultHook implements PreDeletionHook {
    static class DeletedMessageMailboxContext {
        private static DeletedMessageMailboxContext combine(DeletedMessageMailboxContext first, DeletedMessageMailboxContext second) {
            Preconditions.checkArgument(first.messageId.equals(second.getMessageId()));
            Preconditions.checkArgument(first.owner.equals(second.getOwner()));

            return new DeletedMessageMailboxContext(
                first.messageId,
                first.owner,
                ImmutableList.<MailboxId>builder()
                    .addAll(first.ownerMailboxes)
                    .addAll(second.ownerMailboxes)
                    .build());
        }

        private final MessageId messageId;
        private final User owner;
        private final List<MailboxId> ownerMailboxes;

        DeletedMessageMailboxContext(MessageId messageId, User owner, List<MailboxId> ownerMailboxes) {
            this.messageId = messageId;
            this.owner = owner;
            this.ownerMailboxes = ownerMailboxes;
        }

        MessageId getMessageId() {
            return messageId;
        }

        User getOwner() {
            return owner;
        }

        List<MailboxId> getOwnerMailboxes() {
            return ownerMailboxes;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DeletedMessageMailboxContext) {
                DeletedMessageMailboxContext that = (DeletedMessageMailboxContext) o;

                return Objects.equals(this.messageId, that.getMessageId())
                    && Objects.equals(this.owner, that.getOwner())
                    && Objects.equals(this.ownerMailboxes, that.getOwnerMailboxes());
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(messageId, owner, ownerMailboxes);
        }
    }

    private final MailboxSession session;
    private final DeletedMessageVault deletedMessageVault;
    private final DeletedMessageConverter deletedMessageConverter;
    private final MailboxSessionMapperFactory mapperFactory;
    private final Clock clock;

    @Inject
    DeletedMessageVaultHook(SessionProvider sessionProvider,
                            DeletedMessageVault deletedMessageVault,
                            DeletedMessageConverter deletedMessageConverter,
                            MailboxSessionMapperFactory mapperFactory,
                            Clock clock) {
        this.session = sessionProvider.createSystemSession(getClass().getName());
        this.deletedMessageVault = deletedMessageVault;
        this.deletedMessageConverter = deletedMessageConverter;
        this.mapperFactory = mapperFactory;
        this.clock = clock;
    }

    @Override
    public Publisher<Void> notifyDelete(DeleteOperation deleteOperation) {
        Preconditions.checkNotNull(deleteOperation);

        return groupMetadataByOwnerAndMessageId(deleteOperation)
            .flatMap(Throwing.function(this::appendToTheVault).sneakyThrow())
            .then();
    }

    private Mono<Void> appendToTheVault(DeletedMessageMailboxContext deletedMessageMailboxContext) throws MailboxException {
        Optional<MailboxMessage> maybeMailboxMessage = mapperFactory.getMessageIdMapper(session)
            .find(ImmutableList.of(deletedMessageMailboxContext.getMessageId()), MessageMapper.FetchType.Full).stream()
            .findFirst();

        return maybeMailboxMessage.map(Throwing.function(mailboxMessage -> Pair.of(mailboxMessage,
                deletedMessageConverter.convert(deletedMessageMailboxContext, mailboxMessage,
                    ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)))))
            .map(Throwing.function(pairs -> Mono.from(deletedMessageVault
                .append(pairs.getRight().getOwner(), pairs.getRight(), pairs.getLeft().getFullContent()))))
            .orElse(Mono.empty());
    }

    private Flux<DeletedMessageMailboxContext> groupMetadataByOwnerAndMessageId(DeleteOperation deleteOperation) {
        return Flux.fromIterable(deleteOperation.getDeletionMetadataList())
            .groupBy(MetadataWithMailboxId::getMailboxId)
            .flatMap(Throwing.function(this::addOwnerToMetadata).sneakyThrow())
            .groupBy(this::toMessageIdUserPair)
            .flatMap(groupFlux -> groupFlux.reduce(DeletedMessageMailboxContext::combine));
    }

    private Publisher<DeletedMessageMailboxContext> addOwnerToMetadata(GroupedFlux<MailboxId, MetadataWithMailboxId> groupedFlux) throws MailboxException {
        User owner = retrieveMailboxUser(groupedFlux.key());
        return groupedFlux.map(metadata -> new DeletedMessageMailboxContext(metadata.getMessageMetaData().getMessageId(), owner, ImmutableList.of(metadata.getMailboxId())));
    }

    private Pair<MessageId, User> toMessageIdUserPair(DeletedMessageMailboxContext deletedMessageMetadata) {
        return Pair.of(deletedMessageMetadata.getMessageId(), deletedMessageMetadata.getOwner());
    }

    private User retrieveMailboxUser(MailboxId mailboxId) throws MailboxException {
        return User.fromUsername(mapperFactory.getMailboxMapper(session)
            .findMailboxById(mailboxId)
            .getUser());
    }
}