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

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import jakarta.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InMemoryMessageIdMapper implements MessageIdMapper {
    private final MailboxMapper mailboxMapper;
    private final InMemoryMessageMapper messageMapper;

    public InMemoryMessageIdMapper(MailboxMapper mailboxMapper, InMemoryMessageMapper messageMapper) {
        this.mailboxMapper = mailboxMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        return findReactive(messageIds, fetchType)
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    @Override
    public Publisher<ComposedMessageIdWithMetaData> findMetadata(MessageId messageId) {
        return mailboxMapper.list()
            .flatMap(mailbox -> messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.FULL, UNLIMITED), DEFAULT_CONCURRENCY)
            .map(message -> new ComposedMessageIdWithMetaData(
                new ComposedMessageId(
                    message.getMailboxId(),
                    message.getMessageId(),
                    message.getUid()),
                message.createFlags(),
                message.getModSeq(),
                message.getThreadId()));
    }

    @Override
    public Flux<MailboxMessage> findReactive(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        return mailboxMapper.list()
            .flatMap(mailbox -> messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), fetchType, UNLIMITED), DEFAULT_CONCURRENCY)
            .filter(message -> messageIds.contains(message.getMessageId()));
    }

    @Override
    public List<MailboxId> findMailboxes(MessageId messageId) {
        return find(ImmutableList.of(messageId), MessageMapper.FetchType.METADATA)
            .stream()
            .map(MailboxMessage::getMailboxId)
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public void save(MailboxMessage mailboxMessage) throws MailboxException {
        Mailbox mailbox = MailboxReactorUtils.block(mailboxMapper.findMailboxById(mailboxMessage.getMailboxId()));
        messageMapper.save(mailbox, mailboxMessage);
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage, Mailbox mailbox) throws MailboxException {
        boolean isAlreadyInMailbox = findMailboxes(mailboxMessage.getMessageId()).contains(mailbox.getMailboxId());
        if (!isAlreadyInMailbox) {
            save(mailboxMessage);
        }
    }

    @Override
    public void delete(MessageId messageId) {
        find(ImmutableList.of(messageId), MessageMapper.FetchType.METADATA)
            .forEach(Throwing.consumer(
                message -> messageMapper.delete(
                    MailboxReactorUtils.block(mailboxMapper.findMailboxById(message.getMailboxId())),
                    message)));
    }

    @Override
    public void delete(MessageId messageId, Collection<MailboxId> mailboxIds) {
        find(ImmutableList.of(messageId), MessageMapper.FetchType.METADATA)
            .stream()
            .filter(message -> mailboxIds.contains(message.getMailboxId()))
            .forEach(Throwing.consumer(
                message -> messageMapper.delete(
                    MailboxReactorUtils.block(mailboxMapper.findMailboxById(message.getMailboxId())),
                    message)));
    }

    @Override
    public Mono<Multimap<MailboxId, UpdatedFlags>> setFlags(MessageId messageId, List<MailboxId> mailboxIds,
                                                            Flags newState, FlagsUpdateMode updateMode) {
        return findReactive(ImmutableList.of(messageId), MessageMapper.FetchType.METADATA)
            .filter(message -> mailboxIds.contains(message.getMailboxId()))
            .concatMap(updateMessage(newState, updateMode))
            .distinct()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                Pair::getKey,
                Pair::getValue));
    }

    private Function<MailboxMessage, Mono<Pair<MailboxId, UpdatedFlags>>> updateMessage(Flags newState, FlagsUpdateMode updateMode) {
        return (MailboxMessage message) -> {
            FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(newState, updateMode);
            if (flagsUpdateCalculator.buildNewFlags(message.createFlags()).equals(message.createFlags())) {
                UpdatedFlags updatedFlags = UpdatedFlags.builder()
                    .modSeq(message.getModSeq())
                    .uid(message.getUid())
                    .messageId(message.getMessageId())
                    .oldFlags(message.createFlags())
                    .newFlags(newState)
                    .build();
                return Mono.just(Pair.of(message.getMailboxId(), updatedFlags));
            }
            return mailboxMapper.findMailboxById(message.getMailboxId())
                .flatMap(mailboxId -> Mono.from(messageMapper.updateFlagsReactive(
                    mailboxId,
                    flagsUpdateCalculator,
                    message.getUid().toRange()))
                    .flatMapIterable(Function.identity())
                    .next()
                    .map(updatedFlags -> Pair.of(message.getMailboxId(), updatedFlags)));
        };
    }
}
