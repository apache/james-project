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

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class DeletedMessageVaultHook implements PreDeletionHook {
    static class DeletedMessageMailboxContext {
        private final MessageId messageId;
        private final Username owner;
        private final List<MailboxId> ownerMailboxes;

        DeletedMessageMailboxContext(MessageId messageId, Username owner, List<MailboxId> ownerMailboxes) {
            this.messageId = messageId;
            this.owner = owner;
            this.ownerMailboxes = ownerMailboxes;
        }

        MessageId getMessageId() {
            return messageId;
        }

        Username getOwner() {
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

    private static final int CONCURRENCY = 8;

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
        this.session = sessionProvider.createSystemSession(Username.of(getClass().getName()));
        this.deletedMessageVault = deletedMessageVault;
        this.deletedMessageConverter = deletedMessageConverter;
        this.mapperFactory = mapperFactory;
        this.clock = clock;
    }

    @Override
    public Publisher<Void> notifyDelete(DeleteOperation deleteOperation) {
        Preconditions.checkNotNull(deleteOperation);

        return groupMetadataByOwnerAndMessageId(deleteOperation)
            .flatMap(this::appendToTheVault, CONCURRENCY)
            .then();
    }

    private Mono<Void> appendToTheVault(DeletedMessageMailboxContext deletedMessageMailboxContext) {
        return mapperFactory.getMessageIdMapper(session)
            .findReactive(ImmutableList.of(deletedMessageMailboxContext.getMessageId()), MessageMapper.FetchType.FULL)
            .next()
            .switchIfEmpty(Mono.error(() -> new RuntimeException("Cannot find " + deletedMessageMailboxContext.getMessageId())))
            .flatMap(mailboxMessage -> Mono.fromCallable(() -> Pair.of(mailboxMessage,
                deletedMessageConverter.convert(deletedMessageMailboxContext, mailboxMessage,
                    ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)))))
            .flatMap(pairs -> Mono.fromCallable(() -> pairs.getLeft().getFullContent())
                .flatMap(fullContent -> Mono.from(deletedMessageVault.append(pairs.getRight(), fullContent))));
    }

    private Flux<DeletedMessageMailboxContext> groupMetadataByOwnerAndMessageId(DeleteOperation deleteOperation) {
        return Flux.fromIterable(deleteOperation.getDeletionMetadataList())
            .groupBy(MetadataWithMailboxId::getMailboxId)
            .flatMap(this::addOwnerToMetadata, CONCURRENCY);
    }

    private Flux<DeletedMessageMailboxContext> addOwnerToMetadata(GroupedFlux<MailboxId, MetadataWithMailboxId> groupedFlux) {
        return retrieveMailboxUser(groupedFlux.key())
            .flatMapMany(owner -> groupedFlux.map(metadata ->
                new DeletedMessageMailboxContext(metadata.getMessageId(), owner, ImmutableList.of(metadata.getMailboxId()))));
    }

    private Mono<Username> retrieveMailboxUser(MailboxId mailboxId) {
        return mapperFactory.getMailboxMapper(session)
            .findMailboxById(mailboxId)
            .map(Mailbox::getUser);
    }
}
