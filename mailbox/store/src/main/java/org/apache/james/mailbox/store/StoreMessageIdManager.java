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
import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
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
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class StoreMessageIdManager implements MessageIdManager {

    public static ImmutableSet<MailboxId> toMailboxIds(List<MailboxMessage> mailboxMessages) {
        return mailboxMessages
            .stream()
            .map(MailboxMessage::getMailboxId)
            .collect(Guavate.toImmutableSet());
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
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        int concurrency = 4;
        List<Mailbox> targetMailboxes = Flux.fromIterable(mailboxIds)
            .flatMap(mailboxMapper::findMailboxById, concurrency)
            .collect(Guavate.toImmutableList())
            .subscribeOn(Schedulers.elastic())
            .block();

        assertRightsOnMailboxes(targetMailboxes, mailboxSession, Right.Write);

        Multimap<MailboxId, UpdatedFlags> updatedFlags = messageIdMapper.setFlags(messageId, mailboxIds, newState, replace);
        for (Map.Entry<MailboxId, Collection<UpdatedFlags>> entry : updatedFlags.asMap().entrySet()) {
            dispatchFlagsChange(mailboxSession, entry.getKey(), ImmutableList.copyOf(entry.getValue()), targetMailboxes);
        }
    }

    @Override
    public Set<MessageId> accessibleMessages(Collection<MessageId> messageIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        ImmutableList<ComposedMessageIdWithMetaData> idList = Flux.fromIterable(messageIds)
            .flatMap(messageIdMapper::findMetadata, DEFAULT_CONCURRENCY)
            .collect(Guavate.toImmutableList())
            .block();

        ImmutableSet<MailboxId> allowedMailboxIds = getAllowedMailboxIds(mailboxSession, idList.stream()
            .map(id -> id.getComposedMessageId().getMailboxId()), Right.Read);

        return idList.stream()
            .filter(id -> allowedMailboxIds.contains(id.getComposedMessageId().getMailboxId()))
            .map(id -> id.getComposedMessageId().getMessageId())
            .collect(Guavate.toImmutableSet());
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
            .flatMap(id -> Flux.from(messageIdMapper.findMetadata(id)), concurrency)
            .groupBy(metaData -> metaData.getComposedMessageId().getMailboxId())
            .filterWhen(groupedFlux -> hasRightsOnMailboxReactive(session, Right.Read).apply(groupedFlux.key()), DEFAULT_CONCURRENCY)
            .flatMap(Function.identity(), DEFAULT_CONCURRENCY);
    }

    private ImmutableSet<MailboxId> getAllowedMailboxIds(MailboxSession mailboxSession, Stream<MailboxId> idList, Right... rights) throws MailboxException {
        return MailboxReactorUtils.block(Flux.fromStream(idList)
            .distinct()
            .filterWhen(hasRightsOnMailboxReactive(mailboxSession, rights), DEFAULT_CONCURRENCY)
            .collect(Guavate.toImmutableSet()));
    }

    @Override
    public DeleteResult delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        MailboxReactorUtils.block(assertRightsOnMailboxIds(mailboxIds, mailboxSession, Right.DeleteMessages));

        List<MailboxMessage> messageList = messageIdMapper
            .find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .filter(inMailboxes(mailboxIds))
            .collect(Guavate.toImmutableList());

        if (!messageList.isEmpty()) {
            deleteWithPreHooks(messageIdMapper, messageList, mailboxSession)
                .subscribeOn(Schedulers.elastic())
                .block();
            return DeleteResult.destroyed(messageId);
        }
        return DeleteResult.notFound(messageId);
    }

    @Override
    public DeleteResult delete(List<MessageId> messageIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        List<MailboxMessage> messageList = messageIdMapper.find(messageIds, MessageMapper.FetchType.Metadata);
        ImmutableSet<MailboxId> allowedMailboxIds = getAllowedMailboxIds(mailboxSession, messageList
            .stream()
            .map(MailboxMessage::getMailboxId), Right.DeleteMessages);

        List<MailboxMessage> accessibleMessages = messageList.stream()
            .filter(message -> allowedMailboxIds.contains(message.getMailboxId()))
            .collect(Guavate.toImmutableList());
        ImmutableSet<MessageId> accessibleMessageIds = accessibleMessages.stream()
            .map(MailboxMessage::getMessageId)
            .distinct()
            .collect(Guavate.toImmutableSet());
        Sets.SetView<MessageId> nonAccessibleMessages = Sets.difference(ImmutableSet.copyOf(messageIds), accessibleMessageIds);

        deleteWithPreHooks(messageIdMapper, accessibleMessages, mailboxSession)
            .subscribeOn(Schedulers.elastic())
            .block();

        return DeleteResult.builder()
            .addDestroyed(accessibleMessageIds)
            .addNotFound(nonAccessibleMessages)
            .build();
    }

    private Mono<Void> deleteWithPreHooks(MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, MailboxSession mailboxSession) {
        ImmutableList<MetadataWithMailboxId> metadataWithMailbox = messageList.stream()
            .map(mailboxMessage -> MetadataWithMailboxId.from(mailboxMessage.metaData(), mailboxMessage.getMailboxId()))
            .collect(Guavate.toImmutableList());

        return preDeletionHooks.runHooks(PreDeletionHook.DeleteOperation.from(metadataWithMailbox))
            .then(delete(messageIdMapper, messageList, mailboxSession, metadataWithMailbox));
    }

    private Mono<Void> delete(MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, MailboxSession mailboxSession, ImmutableList<MetadataWithMailboxId> metadataWithMailbox) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        return messageIdMapper.deleteReactive(
            messageList.stream()
                .collect(Guavate.toImmutableListMultimap(
                    Message::getMessageId,
                    MailboxMessage::getMailboxId)))
            .then(
                Flux.fromIterable(metadataWithMailbox)
                    .flatMap(metadataWithMailboxId -> mailboxMapper.findMailboxById(metadataWithMailboxId.getMailboxId())
                        .flatMap(mailbox -> eventBus.dispatch(EventFactory.expunged()
                                .randomEventId()
                                .mailboxSession(mailboxSession)
                                .mailbox(mailbox)
                                .addMetaData(metadataWithMailboxId.getMessageMetaData())
                                .build(),
                            new MailboxIdRegistrationKey(metadataWithMailboxId.getMailboxId()))), DEFAULT_CONCURRENCY)
                    .then());
    }

    @Override
    public void setInMailboxes(MessageId messageId, Collection<MailboxId> targetMailboxIds, MailboxSession mailboxSession) throws MailboxException {
        List<MailboxMessage> currentMailboxMessages = findRelatedMailboxMessages(messageId, mailboxSession);

        MailboxReactorUtils.block(messageMovesWithMailbox(MessageMoves.builder()
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
            }).sneakyThrow())
            .subscribeOn(Schedulers.elastic()));
    }

    public void setInMailboxesNoCheck(MessageId messageId, MailboxId targetMailboxId, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        List<MailboxMessage> currentMailboxMessages = messageIdMapper.find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata);


        MailboxReactorUtils.block(messageMovesWithMailbox(MessageMoves.builder()
            .targetMailboxIds(targetMailboxId)
            .previousMailboxIds(toMailboxIds(currentMailboxMessages))
            .build(), mailboxSession)
            .flatMap(messageMove -> {
                if (messageMove.isChange()) {
                    return applyMessageMoveNoMailboxChecks(mailboxSession, currentMailboxMessages, messageMove);
                }
                return Mono.empty();
            })
            .subscribeOn(Schedulers.elastic()));
    }

    private List<MailboxMessage> findRelatedMailboxMessages(MessageId messageId, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        return MailboxReactorUtils.block(messageIdMapper.findReactive(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .collect(Guavate.toImmutableList()));
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
            .collect(Guavate.toImmutableList());

        return Mono.fromRunnable(Throwing.runnable(() -> validateQuota(messageMoves, mailboxMessage.get())).sneakyThrow())
            .then(Mono.fromRunnable(Throwing.runnable(() ->
                addMessageToMailboxes(mailboxMessage.get(), messageMoves.addedMailboxes(), mailboxSession)).sneakyThrow()))
            .then(removeMessageFromMailboxes(mailboxMessage.get().getMessageId(), messagesToRemove, mailboxSession))
            .then(eventBus.dispatch(EventFactory.moved()
                    .session(mailboxSession)
                    .messageMoves(messageMoves.asMessageMoves())
                    .messageId(mailboxMessage.get().getMessageId())
                    .build(),
                messageMoves.impactedMailboxes()
                    .map(Mailbox::getMailboxId)
                    .map(MailboxIdRegistrationKey::new)
                    .collect(Guavate.toImmutableSet())));
    }

    private Mono<Void> removeMessageFromMailboxes(MessageId messageId, List<Pair<MailboxMessage, Mailbox>> messages, MailboxSession mailboxSession) {
        if (messages.isEmpty()) {
            return Mono.empty();
        }

        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        ImmutableList<MailboxId> mailboxIds = messages.stream()
            .map(pair -> pair.getRight().getMailboxId())
            .collect(Guavate.toImmutableList());

        return Mono.from(messageIdMapper.deleteReactive(messageId, mailboxIds))
            .then(Flux.fromIterable(messages)
                .flatMap(message -> eventBus.dispatch(EventFactory.expunged()
                        .randomEventId()
                        .mailboxSession(mailboxSession)
                        .mailbox(message.getRight())
                        .addMetaData(message.getLeft().metaData())
                        .build(),
                    new MailboxIdRegistrationKey(message.getRight().getMailboxId())), DEFAULT_CONCURRENCY)
                .then());
    }
    
    private void dispatchFlagsChange(MailboxSession mailboxSession, MailboxId mailboxId, ImmutableList<UpdatedFlags> updatedFlags,
                                     List<Mailbox> knownMailboxes) throws MailboxException {
        if (updatedFlags.stream().anyMatch(UpdatedFlags::flagsChanged)) {
            Mailbox mailbox = knownMailboxes.stream()
                .filter(knownMailbox -> knownMailbox.getMailboxId().equals(mailboxId))
                .findFirst()
                .orElseGet(Throwing.supplier(() -> MailboxReactorUtils.block(mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
                        .findMailboxById(mailboxId)
                        .subscribeOn(Schedulers.elastic())))
                    .sneakyThrow());
            eventBus.dispatch(EventFactory.flagsUpdated()
                        .randomEventId()
                        .mailboxSession(mailboxSession)
                        .mailbox(mailbox)
                        .updatedFlags(updatedFlags)
                        .build(),
                    new MailboxIdRegistrationKey(mailboxId))
                .subscribeOn(Schedulers.elastic())
                .block();
        }
    }

    private void validateQuota(MessageMovesWithMailbox messageMoves, MailboxMessage mailboxMessage) throws MailboxException {
        Map<QuotaRoot, Integer> messageCountByQuotaRoot = buildMapQuotaRoot(messageMoves);
        for (Map.Entry<QuotaRoot, Integer> entry : messageCountByQuotaRoot.entrySet()) {
            Integer additionalCopyCount = entry.getValue();
            if (additionalCopyCount > 0) {
                long additionalOccupiedSpace = additionalCopyCount * mailboxMessage.getFullContentOctets();
                new QuotaChecker(quotaManager.getQuotas(entry.getKey()), entry.getKey())
                    .tryAddition(additionalCopyCount, additionalOccupiedSpace);
            }
        }
    }

    private Map<QuotaRoot, Integer> buildMapQuotaRoot(MessageMovesWithMailbox messageMoves) throws MailboxException {
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
    }

    private void addMessageToMailboxes(MailboxMessage mailboxMessage, Set<Mailbox> mailboxes, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        for (Mailbox mailbox : mailboxes) {
            MailboxACL.Rfc4314Rights myRights = rightManager.myRights(mailbox, mailboxSession);
            boolean shouldPreserveFlags = myRights.contains(Right.Write);
            SimpleMailboxMessage copy =
                SimpleMailboxMessage.from(mailboxMessage)
                    .mailboxId(mailbox.getMailboxId())
                    .flags(
                        FlagsFactory
                            .builder()
                            .flags(mailboxMessage.createFlags())
                            .filteringFlags(
                                FlagsFilter.builder()
                                    .systemFlagFilter(f -> shouldPreserveFlags)
                                    .userFlagFilter(f -> shouldPreserveFlags)
                                    .build())
                            .build())
                    .build();
            save(messageIdMapper, copy, mailbox);

            eventBus.dispatch(EventFactory.added()
                    .randomEventId()
                    .mailboxSession(mailboxSession)
                    .mailbox(mailbox)
                    .addMetaData(copy.metaData())
                    .build(),
                    new MailboxIdRegistrationKey(mailbox.getMailboxId()))
                .block();
        }
    }

    private void save(MessageIdMapper messageIdMapper, MailboxMessage mailboxMessage, Mailbox mailbox) throws MailboxException {
        ModSeq modSeq = mailboxSessionMapperFactory.getModSeqProvider().nextModSeq(mailbox.getMailboxId());
        MessageUid uid = mailboxSessionMapperFactory.getUidProvider().nextUid(mailbox.getMailboxId());
        mailboxMessage.setModSeq(modSeq);
        mailboxMessage.setUid(uid);
        messageIdMapper.copyInMailbox(mailboxMessage, mailbox);
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
            .collect(Guavate.toImmutableList());
        Mono<List<Mailbox>> previous = Flux.fromIterable(messageMoves.getPreviousMailboxIds())
            .flatMap(mailboxMapper::findMailboxById, DEFAULT_CONCURRENCY)
            .collect(Guavate.toImmutableList());

        return target.zipWith(previous)
            .map(tuple -> MessageMovesWithMailbox.builder()
                .targetMailboxes(tuple.getT1())
                .previousMailboxes(tuple.getT2())
                .build());
    }
}
