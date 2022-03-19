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

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Inject;

import jakarta.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.FetchGroupConverter;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.FlagsFactory;
import org.apache.james.mailbox.store.mail.model.FlagsFilter;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class StoreMessageIdManager implements MessageIdManager {

    public static ImmutableSet<MailboxId> toMailboxIds(List<MailboxMessage> mailboxMessages) {
        return mailboxMessages
            .stream()
            .map(MailboxMessage::getMailboxId)
            .collect(ImmutableSet.toImmutableSet());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreMessageIdManager.class);

    private final RightManager rightManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final EventBus eventBus;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final PreDeletionHooks preDeletionHooks;

    @Inject
    public StoreMessageIdManager(RightManager rightManager, MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                 EventBus eventBus, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
                                 PreDeletionHooks preDeletionHooks) {
        this.rightManager = rightManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.eventBus = eventBus;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.preDeletionHooks = preDeletionHooks;
    }

    @Override
    public void setFlags(Flags newState, MessageManager.FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MailboxReactorUtils.block(setFlagsReactive(newState, replace, messageId, mailboxIds, mailboxSession));
    }

    @Override
    public Mono<Void> setFlagsReactive(Flags newState, MessageManager.FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        int concurrency = 4;

        return Flux.fromIterable(mailboxIds)
            .flatMap(mailboxMapper::findMailboxById, concurrency)
            .collect(ImmutableList.toImmutableList())
            .flatMap(Throwing.<List<Mailbox>, Mono<Void>>function(targetMailboxes -> {
                assertRightsOnMailboxes(targetMailboxes, mailboxSession, Right.Write);

                return messageIdMapper.setFlags(messageId, mailboxIds, newState, replace)
                    .flatMapIterable(updatedFlags -> updatedFlags.asMap().entrySet())
                    .concatMap(entry -> dispatchFlagsChange(mailboxSession, entry.getKey(), ImmutableList.copyOf(entry.getValue()), targetMailboxes))
                    .then();
            }).sneakyThrow());
    }

    @Override
    public Set<MessageId> accessibleMessages(Collection<MessageId> messageIds, MailboxSession mailboxSession) {
        return accessibleMessagesReactive(messageIds, mailboxSession).block();
    }

    @Override
    public Mono<Set<MessageId>> accessibleMessagesReactive(Collection<MessageId> messageIds, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        return Flux.fromIterable(messageIds)
            .flatMap(messageIdMapper::findMetadata, DEFAULT_CONCURRENCY)
            .collect(ImmutableList.toImmutableList())
            .flatMap(idList -> getAllowedMailboxIds(mailboxSession, idList.stream()
                .map(id -> id.getComposedMessageId().getMailboxId()), Right.Read)
                .map(allowedMailboxIds -> idList.stream()
                    .filter(id -> allowedMailboxIds.contains(id.getComposedMessageId().getMailboxId()))
                    .map(id -> id.getComposedMessageId().getMessageId())
                    .collect(ImmutableSet.toImmutableSet())));
    }

    @Override
    public List<MessageResult> getMessages(Collection<MessageId> messageIds, FetchGroup fetchGroup, MailboxSession mailboxSession) {
        return getMessagesReactive(messageIds, fetchGroup, mailboxSession)
            .collectList()
            .block();
    }

    @Override
    public Flux<MessageResult> getMessagesReactive(Collection<MessageId> messageIds, FetchGroup fetchGroup, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        MessageMapper.FetchType fetchType = FetchGroupConverter.getFetchType(fetchGroup);
        return messageIdMapper.findReactive(messageIds, fetchType)
            .groupBy(MailboxMessage::getMailboxId)
            .filterWhen(groupedFlux -> hasRightsOnMailboxReactive(mailboxSession, Right.Read).apply(groupedFlux.key()), DEFAULT_CONCURRENCY)
            .flatMap(Function.identity(), DEFAULT_CONCURRENCY)
            .map(Throwing.function(messageResultConverter(fetchGroup)).sneakyThrow());
    }

    @Override
    public Publisher<ComposedMessageIdWithMetaData> messagesMetadata(Collection<MessageId> ids, MailboxSession session) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(session);
        int concurrency = 4;
        return Flux.fromIterable(ids)
            .flatMap(messageIdMapper::findMetadata, concurrency)
            .groupBy(metaData -> metaData.getComposedMessageId().getMailboxId())
            .filterWhen(groupedFlux -> hasRightsOnMailboxReactive(session, Right.Read).apply(groupedFlux.key()), DEFAULT_CONCURRENCY)
            .flatMap(Function.identity(), DEFAULT_CONCURRENCY);
    }

    private Mono<ImmutableSet<MailboxId>> getAllowedMailboxIds(MailboxSession mailboxSession, Stream<MailboxId> idList, Right... rights) {
        return Flux.fromStream(idList)
            .distinct()
            .filterWhen(hasRightsOnMailboxReactive(mailboxSession, rights), DEFAULT_CONCURRENCY)
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public DeleteResult delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        return MailboxReactorUtils.block(deleteReactive(ImmutableList.of(messageId), mailboxIds, mailboxSession));
    }

    @Override
    public Mono<DeleteResult> deleteReactive(List<MessageId> messageIds, List<MailboxId> mailboxIds, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        return assertRightsOnMailboxIds(mailboxIds, mailboxSession, Right.DeleteMessages)
            .then(messageIdMapper.findReactive(messageIds, MessageMapper.FetchType.METADATA)
                .filter(inMailboxes(mailboxIds))
                .collectList())
            .flatMap(messageList -> {
                Set<MessageId> found = messageList.stream()
                    .map(Message::getMessageId)
                    .collect(ImmutableSet.toImmutableSet());
                Set<MessageId> notFound = Sets.difference(ImmutableSet.copyOf(messageIds), found);

                DeleteResult result = DeleteResult.builder()
                    .addDestroyed(found)
                    .addNotFound(notFound)
                    .build();


                if (!messageList.isEmpty()) {
                    return deleteWithPreHooks(messageIdMapper, messageList, mailboxSession)
                        .thenReturn(result);
                }
                return Mono.just(result);
            });
    }

    @Override
    public Mono<DeleteResult> delete(List<MessageId> messageIds, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        return messageIdMapper.findReactive(messageIds, MessageMapper.FetchType.METADATA)
            .collectList()
            .flatMap(messageList ->
                getAllowedMailboxIds(mailboxSession,
                    messageList
                        .stream()
                        .map(MailboxMessage::getMailboxId),
                    Right.DeleteMessages)
                .flatMap(allowedMailboxIds -> deleteInAllowedMailboxes(messageIds, mailboxSession, messageIdMapper, messageList, allowedMailboxIds)));
    }

    private Mono<DeleteResult> deleteInAllowedMailboxes(List<MessageId> messageIds, MailboxSession mailboxSession, MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, ImmutableSet<MailboxId> allowedMailboxIds) {
        List<MailboxMessage> accessibleMessages = messageList.stream()
            .filter(message -> allowedMailboxIds.contains(message.getMailboxId()))
            .collect(ImmutableList.toImmutableList());
        ImmutableSet<MessageId> accessibleMessageIds = accessibleMessages.stream()
            .map(MailboxMessage::getMessageId)
            .distinct()
            .collect(ImmutableSet.toImmutableSet());
        Sets.SetView<MessageId> nonAccessibleMessages = Sets.difference(ImmutableSet.copyOf(messageIds), accessibleMessageIds);

        return deleteWithPreHooks(messageIdMapper, accessibleMessages, mailboxSession)
            .thenReturn(DeleteResult.builder()
                .addDestroyed(accessibleMessageIds)
                .addNotFound(nonAccessibleMessages)
                .build());
    }

    private Mono<Void> deleteWithPreHooks(MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, MailboxSession mailboxSession) {
        ImmutableMap<MetadataWithMailboxId, MessageMetaData> metadataWithMailbox = messageList.stream()
            .collect(ImmutableMap.toImmutableMap(
                message -> MetadataWithMailboxId.from(message.metaData(), message.getMailboxId()),
                MailboxMessage::metaData));

        return preDeletionHooks.runHooks(PreDeletionHook.DeleteOperation.from(metadataWithMailbox.keySet().asList()))
            .then(delete(messageIdMapper, messageList, mailboxSession, metadataWithMailbox));
    }

    private Mono<Void> delete(MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, MailboxSession mailboxSession, Map<MetadataWithMailboxId, MessageMetaData> metadataWithMailbox) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        return messageIdMapper.deleteReactive(
            messageList.stream()
                .collect(ImmutableListMultimap.toImmutableListMultimap(
                    Message::getMessageId,
                    MailboxMessage::getMailboxId)))
            .then(
                Flux.fromIterable(metadataWithMailbox.entrySet())
                    .flatMap(metadataWithMailboxId -> mailboxMapper.findMailboxById(metadataWithMailboxId.getKey().getMailboxId())
                        .flatMap(mailbox -> eventBus.dispatch(EventFactory.expunged()
                                .randomEventId()
                                .mailboxSession(mailboxSession)
                                .mailbox(mailbox)
                                .addMetaData(metadataWithMailboxId.getValue())
                                .build(),
                            new MailboxIdRegistrationKey(metadataWithMailboxId.getKey().getMailboxId()))), DEFAULT_CONCURRENCY)
                    .then());
    }

    @Override
    public void setInMailboxes(MessageId messageId, Collection<MailboxId> targetMailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MailboxReactorUtils.block(setInMailboxesReactive(messageId, targetMailboxIds, mailboxSession));
    }

    @Override
    public Mono<Void> setInMailboxesReactive(MessageId messageId, Collection<MailboxId> targetMailboxIds, MailboxSession mailboxSession) {
        return findRelatedMailboxMessages(messageId, mailboxSession)
            .flatMap(currentMailboxMessages -> messageMovesWithMailbox(MessageMoves.builder()
                .targetMailboxIds(targetMailboxIds)
                .previousMailboxIds(toMailboxIds(currentMailboxMessages))
                .build(), mailboxSession)
                .flatMap(Throwing.<MessageMovesWithMailbox, Mono<Void>>function(messageMove -> {
                    MessageMovesWithMailbox refined = messageMove.filterPrevious(hasRightsOnMailbox(mailboxSession, Right.Read));

                    if (messageMove.getPreviousMailboxes().isEmpty()) {
                        LOGGER.info("Tried to access {} not accessible for {}", messageId, mailboxSession.getUser().asString());
                        return Mono.empty();
                    }
                    if (refined.isChange()) {
                        return applyMessageMoves(mailboxSession, currentMailboxMessages, refined);
                    }
                    return Mono.empty();
                }).sneakyThrow()));
    }

    public void setInMailboxesNoCheck(MessageId messageId, MailboxId targetMailboxId, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        List<MailboxMessage> currentMailboxMessages = messageIdMapper.find(ImmutableList.of(messageId), MessageMapper.FetchType.METADATA);


        MailboxReactorUtils.block(messageMovesWithMailbox(MessageMoves.builder()
            .targetMailboxIds(targetMailboxId)
            .previousMailboxIds(toMailboxIds(currentMailboxMessages))
            .build(), mailboxSession)
            .flatMap(messageMove -> {
                if (messageMove.isChange()) {
                    return applyMessageMoveNoMailboxChecks(mailboxSession, currentMailboxMessages, messageMove);
                }
                return Mono.empty();
            }));
    }

    private Mono<List<MailboxMessage>> findRelatedMailboxMessages(MessageId messageId, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        return messageIdMapper.findReactive(ImmutableList.of(messageId), MessageMapper.FetchType.METADATA)
            .collect(ImmutableList.toImmutableList());
    }

    private Mono<Void> applyMessageMoves(MailboxSession mailboxSession, List<MailboxMessage> currentMailboxMessages, MessageMovesWithMailbox messageMoves) throws MailboxNotFoundException {
        assertRightsOnMailboxes(messageMoves.addedMailboxes(), mailboxSession, Right.Insert);
        assertRightsOnMailboxes(messageMoves.removedMailboxes(), mailboxSession, Right.DeleteMessages);
        assertRightsOnMailboxes(messageMoves.getTargetMailboxes(), mailboxSession, Right.Read);

        return applyMessageMoveNoMailboxChecks(mailboxSession, currentMailboxMessages, messageMoves);
    }

    private Mono<Void> applyMessageMoveNoMailboxChecks(MailboxSession mailboxSession, List<MailboxMessage> currentMailboxMessages, MessageMovesWithMailbox messageMoves) {
        Optional<MailboxMessage> mailboxMessage = currentMailboxMessages.stream().findAny();

        if (mailboxMessage.isEmpty()) {
            return Mono.error(new MailboxNotFoundException("can't load message"));
        }
        List<Pair<MailboxMessage, Mailbox>> messagesToRemove = currentMailboxMessages.stream()
            .flatMap(message -> messageMoves.removedMailboxes()
                .stream()
                .filter(mailbox -> mailbox.getMailboxId().equals(message.getMailboxId()))
                .map(mailbox -> Pair.of(message, mailbox)))
            .collect(ImmutableList.toImmutableList());

        return validateQuota(messageMoves, mailboxMessage.get())
            .then(addMessageToMailboxes(mailboxMessage.get(), messageMoves, mailboxSession))
            .then(expungeMessageFromMailboxes(mailboxMessage.get().getMessageId(), messagesToRemove, mailboxSession, messageMoves))
            .then(eventBus.dispatch(EventFactory.moved()
                    .session(mailboxSession)
                    .messageMoves(messageMoves.asMessageMoves())
                    .messageId(mailboxMessage.get().getMessageId())
                    .build(),
                messageMoves.impactedMailboxes()
                    .map(Mailbox::getMailboxId)
                    .map(MailboxIdRegistrationKey::new)
                    .collect(ImmutableSet.toImmutableSet())));
    }

    private Mono<Void> expungeMessageFromMailboxes(MessageId messageId, List<Pair<MailboxMessage, Mailbox>> messages, MailboxSession mailboxSession, MessageMovesWithMailbox messageMoves) {
        if (messages.isEmpty()) {
            return Mono.empty();
        }

        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        ImmutableList<MailboxId> mailboxIds = messages.stream()
            .map(pair -> pair.getRight().getMailboxId())
            .collect(ImmutableList.toImmutableList());

        return Mono.from(messageIdMapper.deleteReactive(messageId, mailboxIds))
            .then(Flux.fromIterable(messages)
                .flatMap(message -> dispatchExpungedEvent(message, mailboxSession, messageMoves), DEFAULT_CONCURRENCY)
                .then());
    }

    private Mono<Void> dispatchExpungedEvent(Pair<MailboxMessage, Mailbox> message, MailboxSession mailboxSession, MessageMovesWithMailbox messageMoves) {
        return Mono.just(EventFactory.expunged()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(message.getRight())
                .addMetaData(message.getLeft().metaData()))
            .map(eventBuilder -> {
                if (isSingleMove(messageMoves)) {
                    return eventBuilder
                        .movedTo(messageMoves.addedMailboxes().iterator().next().getMailboxId())
                        .build();
                } else {
                    return eventBuilder.build();
                }
            })
            .flatMap(event -> eventBus.dispatch(event, new MailboxIdRegistrationKey(message.getRight().getMailboxId())));
    }

    private Mono<Void> dispatchFlagsChange(MailboxSession mailboxSession, MailboxId mailboxId, ImmutableList<UpdatedFlags> updatedFlags, List<Mailbox> knownMailboxes) {
        if (updatedFlags.stream().anyMatch(UpdatedFlags::flagsChanged)) {
            return knownMailboxes.stream()
                .filter(knownMailbox -> knownMailbox.getMailboxId().equals(mailboxId))
                .findFirst()
                .map(Mono::just)
                .orElseGet(() -> mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId))
                .flatMap(mailbox ->
                    eventBus.dispatch(EventFactory.flagsUpdated()
                            .randomEventId()
                            .mailboxSession(mailboxSession)
                            .mailbox(mailbox)
                            .updatedFlags(updatedFlags)
                            .build(),
                        new MailboxIdRegistrationKey(mailboxId)));
        }
        return Mono.empty();
    }

    private Mono<Void> validateQuota(MessageMovesWithMailbox messageMoves, MailboxMessage mailboxMessage) {
        Map<QuotaRoot, Integer> messageCountByQuotaRoot = buildMapQuotaRoot(messageMoves);

        return Flux.fromIterable(messageCountByQuotaRoot.entrySet())
            .filter(entry -> entry.getValue() > 0)
            .flatMap(entry -> Mono.from(quotaManager.getQuotasReactive(entry.getKey()))
                .map(quotas -> new QuotaChecker(quotas, entry.getKey()))
                .handle((quotaChecker, sink) -> {
                    Integer additionalCopyCount = entry.getValue();
                    long additionalOccupiedSpace = additionalCopyCount * mailboxMessage.getFullContentOctets();
                    try {
                        quotaChecker.tryAddition(additionalCopyCount, additionalOccupiedSpace);
                    } catch (OverQuotaException e) {
                        sink.error(e);
                    }
                }))
            .then();
    }

    private Map<QuotaRoot, Integer> buildMapQuotaRoot(MessageMovesWithMailbox messageMoves) {
        try {
            Map<QuotaRoot, Integer> messageCountByQuotaRoot = new HashMap<>();
            for (Mailbox mailbox : messageMoves.addedMailboxes()) {
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailbox);
                int currentCount = Optional.ofNullable(messageCountByQuotaRoot.get(quotaRoot))
                    .orElse(0);
                messageCountByQuotaRoot.put(quotaRoot, currentCount + 1);
            }
            for (Mailbox mailbox : messageMoves.removedMailboxes()) {
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailbox);
                int currentCount = Optional.ofNullable(messageCountByQuotaRoot.get(quotaRoot))
                    .orElse(0);
                messageCountByQuotaRoot.put(quotaRoot, currentCount - 1);
            }
            return messageCountByQuotaRoot;
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<Void> addMessageToMailboxes(MailboxMessage mailboxMessage, MessageMovesWithMailbox messageMoves, MailboxSession mailboxSession) {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        return Flux.fromIterable(messageMoves.addedMailboxes())
            .flatMap(Throwing.<Mailbox, Mono<Void>>function(mailbox -> {
                MailboxACL.Rfc4314Rights myRights = rightManager.myRights(mailbox, mailboxSession);
                boolean shouldPreserveFlags = myRights.contains(Right.Write);
                MailboxMessage copy = mailboxMessage.copy(mailbox);
                copy.setFlags(FlagsFactory
                    .builder()
                    .flags(mailboxMessage.createFlags())
                    .filteringFlags(
                        FlagsFilter.builder()
                            .systemFlagFilter(f -> shouldPreserveFlags)
                            .userFlagFilter(f -> shouldPreserveFlags)
                            .build())
                    .build());

                return save(messageIdMapper, copy, mailbox)
                    .flatMap(metadata -> dispatchAddedEvent(mailboxSession, mailbox, metadata, messageMoves));
            }).sneakyThrow())
            .then();
    }

    private Mono<Void> dispatchAddedEvent(MailboxSession mailboxSession, Mailbox mailbox, MessageMetaData messageMetaData, MessageMovesWithMailbox messageMoves) {
        return Mono.just(EventFactory.added()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(mailbox)
                .addMetaData(messageMetaData)
                .isDelivery(!IS_DELIVERY)
                .isAppended(!IS_APPENDED))
            .map(eventBuilder -> {
                if (isSingleMove(messageMoves)) {
                    return eventBuilder
                        .movedFrom(messageMoves.removedMailboxes().iterator().next().getMailboxId())
                        .build();
                } else {
                    return eventBuilder.build();
                }
            }).flatMap(event -> eventBus.dispatch(event, new MailboxIdRegistrationKey(mailbox.getMailboxId())));
    }

    private boolean isSingleMove(MessageMovesWithMailbox messageMoves) {
        return messageMoves.addedMailboxes().size() == 1 && messageMoves.removedMailboxes().size() == 1;
    }

    private Mono<MessageMetaData> save(MessageIdMapper messageIdMapper, MailboxMessage mailboxMessage, Mailbox mailbox) {
        return Mono.zip(
                mailboxSessionMapperFactory.getModSeqProvider().nextModSeqReactive(mailbox.getMailboxId()),
                mailboxSessionMapperFactory.getUidProvider().nextUidReactive(mailbox.getMailboxId()))
            .flatMap(modSeqAndUid -> {
                mailboxMessage.setModSeq(modSeqAndUid.getT1());
                mailboxMessage.setUid(modSeqAndUid.getT2());

                return messageIdMapper.copyInMailboxReactive(mailboxMessage, mailbox)
                    .thenReturn(mailboxMessage.metaData());
            });
    }

    private ThrowingFunction<MailboxMessage, MessageResult> messageResultConverter(FetchGroup fetchGroup) {
        return input -> ResultUtils.loadMessageResult(input, fetchGroup);
    }

    private Predicate<MailboxMessage> inMailboxes(Collection<MailboxId> mailboxIds) {
        return mailboxMessage -> mailboxIds.contains(mailboxMessage.getMailboxId());
    }

    private Function<MailboxId, Mono<Boolean>> hasRightsOnMailboxReactive(MailboxSession session, Right... rights) {
        return mailboxId -> Mono.from(rightManager.myRights(mailboxId, session))
            .map(myRights -> myRights.contains(rights))
            .onErrorResume(any -> Mono.just(false));
    }

    private Predicate<Mailbox> hasRightsOnMailbox(MailboxSession session, Right... rights) {
        return mailbox -> rightManager.myRights(mailbox, session).contains(rights);
    }

    private Mono<Void> assertRightsOnMailboxIds(Collection<MailboxId> mailboxIds, MailboxSession mailboxSession, Right... rights) {
        return Flux.fromIterable(mailboxIds)
            .filterWhen(hasRightsOnMailboxReactive(mailboxSession, rights).andThen(result -> result.map(FunctionalUtils.negate())), DEFAULT_CONCURRENCY)
            .next()
            .flatMap(mailboxForbidden -> {
                LOGGER.info("Mailbox with Id {} does not belong to {}", mailboxForbidden, mailboxSession.getUser().asString());
                return Mono.error(new MailboxNotFoundException(mailboxForbidden));
            })
            .then();
    }

    private void assertRightsOnMailboxes(Collection<Mailbox> mailboxes, MailboxSession mailboxSession, Right... rights) throws MailboxNotFoundException {
        Optional<Mailbox> firstForbiddenMailbox = mailboxes.stream()
            .filter(Predicate.not(hasRightsOnMailbox(mailboxSession, rights)))
            .findFirst();

        if (firstForbiddenMailbox.isPresent()) {
            MailboxId mailboxId = firstForbiddenMailbox.get().getMailboxId();
            LOGGER.info("Mailbox with Id {} does not belong to {}", mailboxId, mailboxSession.getUser().asString());
            throw new MailboxNotFoundException(firstForbiddenMailbox.get().generateAssociatedPath());
        }
    }

    private Mono<MessageMovesWithMailbox> messageMovesWithMailbox(MessageMoves messageMoves, MailboxSession session) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        Mono<List<Mailbox>> target = Flux.fromIterable(messageMoves.getTargetMailboxIds())
            .flatMap(mailboxMapper::findMailboxById, DEFAULT_CONCURRENCY)
            .collect(ImmutableList.toImmutableList());
        Mono<List<Mailbox>> previous = Flux.fromIterable(messageMoves.getPreviousMailboxIds())
            .flatMap(mailboxMapper::findMailboxById, DEFAULT_CONCURRENCY)
            .collect(ImmutableList.toImmutableList());

        return target.zipWith(previous)
            .map(tuple -> MessageMovesWithMailbox.builder()
                .targetMailboxes(tuple.getT1())
                .previousMailboxes(tuple.getT2())
                .build());
    }
}
