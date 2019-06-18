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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.FlagsFactory;
import org.apache.james.mailbox.store.mail.model.FlagsFilter;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class StoreMessageIdManager implements MessageIdManager {

    public static ImmutableSet<MailboxId> toMailboxIds(List<MailboxMessage> mailboxMessages) {
        return mailboxMessages
            .stream()
            .map(MailboxMessage::getMailboxId)
            .collect(Guavate.toImmutableSet());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreMessageIdManager.class);

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final EventBus eventBus;
    private final MessageId.Factory messageIdFactory;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final PreDeletionHooks preDeletionHooks;

    @Inject
    public StoreMessageIdManager(MailboxManager mailboxManager, MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                 EventBus eventBus, MessageId.Factory messageIdFactory,
                                 QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, PreDeletionHooks preDeletionHooks) {
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.eventBus = eventBus;
        this.messageIdFactory = messageIdFactory;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.preDeletionHooks = preDeletionHooks;
    }

    @Override
    public void setFlags(Flags newState, MessageManager.FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        assertRightsOnMailboxes(mailboxIds, mailboxSession, Right.Write);

        Map<MailboxId, UpdatedFlags> updatedFlags = messageIdMapper.setFlags(messageId, mailboxIds, newState, replace);
        for (Map.Entry<MailboxId, UpdatedFlags> entry : updatedFlags.entrySet()) {
            dispatchFlagsChange(mailboxSession, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<MessageId> accessibleMessages(Collection<MessageId> messageIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        List<MailboxMessage> messageList = messageIdMapper.find(messageIds, MessageMapper.FetchType.Metadata);

        ImmutableSet<MailboxId> allowedMailboxIds = getAllowedMailboxIds(mailboxSession, messageList, Right.Read);

        return messageList.stream()
            .filter(message -> allowedMailboxIds.contains(message.getMailboxId()))
            .map(MailboxMessage::getMessageId)
            .collect(Guavate.toImmutableSet());
    }

    @Override
    public List<MessageResult> getMessages(List<MessageId> messageIds, MessageResult.FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        List<MailboxMessage> messageList = messageIdMapper.find(messageIds, MessageMapper.FetchType.Full);

        ImmutableSet<MailboxId> allowedMailboxIds = getAllowedMailboxIds(mailboxSession, messageList, Right.Read);

        return messageList.stream()
            .filter(inMailboxes(allowedMailboxIds))
            .map(Throwing.function(messageResultConverter(fetchGroup)).sneakyThrow())
            .collect(Guavate.toImmutableList());
    }

    private ImmutableSet<MailboxId> getAllowedMailboxIds(MailboxSession mailboxSession, List<MailboxMessage> messageList, Right... rights) {
        return messageList.stream()
                .map(MailboxMessage::getMailboxId)
                .distinct()
                .filter(hasRightsOnMailbox(mailboxSession, rights))
                .collect(Guavate.toImmutableSet());
    }

    @Override
    public DeleteResult delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        assertRightsOnMailboxes(mailboxIds, mailboxSession, Right.DeleteMessages);

        List<MailboxMessage> messageList = messageIdMapper
            .find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .filter(inMailboxes(mailboxIds))
            .collect(Guavate.toImmutableList());

        if (!messageList.isEmpty()) {
            deleteWithPreHooks(messageIdMapper, messageList, mailboxSession);
            return DeleteResult.destroyed(messageId);
        }
        return DeleteResult.notFound(messageId);
    }

    @Override
    public DeleteResult delete(List<MessageId> messageIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        List<MailboxMessage> messageList = messageIdMapper.find(messageIds, MessageMapper.FetchType.Metadata);
        ImmutableSet<MailboxId> allowedMailboxIds = getAllowedMailboxIds(mailboxSession, messageList, Right.DeleteMessages);

        ImmutableSet<MessageId> accessibleMessages = messageList.stream()
            .filter(message -> allowedMailboxIds.contains(message.getMailboxId()))
            .map(MailboxMessage::getMessageId)
            .distinct()
            .collect(Guavate.toImmutableSet());
        Sets.SetView<MessageId> nonAccessibleMessages = Sets.difference(ImmutableSet.copyOf(messageIds), accessibleMessages);

        deleteWithPreHooks(messageIdMapper, messageList, mailboxSession);

        return DeleteResult.builder()
            .addDestroyed(accessibleMessages)
            .addNotFound(nonAccessibleMessages)
            .build();
    }

    private void deleteWithPreHooks(MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, MailboxSession mailboxSession) throws MailboxException {
        ImmutableList<MetadataWithMailboxId> metadataWithMailbox = messageList.stream()
            .map(mailboxMessage -> MetadataWithMailboxId.from(mailboxMessage.metaData(), mailboxMessage.getMailboxId()))
            .collect(Guavate.toImmutableList());

        preDeletionHooks.runHooks(PreDeletionHook.DeleteOperation.from(metadataWithMailbox))
            .then(Mono.fromRunnable(Throwing.runnable(
                () -> delete(messageIdMapper, messageList, mailboxSession, metadataWithMailbox))))
            .block();
    }

    private void delete(MessageIdMapper messageIdMapper, List<MailboxMessage> messageList, MailboxSession mailboxSession,
                        ImmutableList<MetadataWithMailboxId> metadataWithMailbox) throws MailboxException {
        messageIdMapper.delete(
            messageList.stream()
                .collect(Guavate.toImmutableListMultimap(
                    Message::getMessageId,
                    MailboxMessage::getMailboxId)));

        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        Flux.fromIterable(metadataWithMailbox)
            .flatMap(Throwing.<MetadataWithMailboxId, Mono<Void>>function(
                metadataWithMailboxId -> eventBus.dispatch(EventFactory.expunged()
                    .randomEventId()
                    .mailboxSession(mailboxSession)
                    .mailbox(mailboxMapper.findMailboxById(metadataWithMailboxId.getMailboxId()))
                    .addMetaData(metadataWithMailboxId.getMessageMetaData())
                    .build(),
                new MailboxIdRegistrationKey(metadataWithMailboxId.getMailboxId())))
                .sneakyThrow())
            .then()
            .block();
    }

    @Override
    public void setInMailboxes(MessageId messageId, Collection<MailboxId> targetMailboxIds, MailboxSession mailboxSession) throws MailboxException {
        assertRightsOnMailboxes(targetMailboxIds, mailboxSession, Right.Read);

        List<MailboxMessage> currentMailboxMessages = findRelatedMailboxMessages(messageId, mailboxSession);

        if (currentMailboxMessages.isEmpty()) {
            LOGGER.info("Tried to access {} not accessible for {}", messageId, mailboxSession.getUser().asString());
            return;
        }

        MessageMoves messageMoves = MessageMoves.builder()
            .targetMailboxIds(targetMailboxIds)
            .previousMailboxIds(toMailboxIds(currentMailboxMessages))
            .build();

        if (messageMoves.isChange()) {
            applyMessageMoves(mailboxSession, currentMailboxMessages, messageMoves);
        }
    }

    public void setInMailboxesNoCheck(MessageId messageId, MailboxId targetMailboxId, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        List<MailboxMessage> currentMailboxMessages = messageIdMapper.find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata);

        MessageMoves messageMoves = MessageMoves.builder()
            .previousMailboxIds(toMailboxIds(currentMailboxMessages))
            .targetMailboxIds(targetMailboxId)
            .build();

        if (messageMoves.isChange()) {
            applyMessageMoveNoMailboxChecks(mailboxSession, currentMailboxMessages, messageMoves);
        }
    }

    private List<MailboxMessage> findRelatedMailboxMessages(MessageId messageId, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);

        return messageIdMapper.find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .filter(hasRightsOn(mailboxSession, Right.Read))
            .collect(Guavate.toImmutableList());
    }

    private void applyMessageMoves(MailboxSession mailboxSession, List<MailboxMessage> currentMailboxMessages, MessageMoves messageMoves) throws MailboxException {
        assertRightsOnMailboxes(messageMoves.addedMailboxIds(), mailboxSession, Right.Insert);
        assertRightsOnMailboxes(messageMoves.removedMailboxIds(), mailboxSession, Right.DeleteMessages);

        applyMessageMoveNoMailboxChecks(mailboxSession, currentMailboxMessages, messageMoves);
    }

    private void applyMessageMoveNoMailboxChecks(MailboxSession mailboxSession, List<MailboxMessage> currentMailboxMessages, MessageMoves messageMoves) throws MailboxException {
        MailboxMessage mailboxMessage = currentMailboxMessages.stream().findAny().orElseThrow(() -> new MailboxNotFoundException("can't load message"));

        validateQuota(messageMoves, mailboxMessage);

        addMessageToMailboxes(mailboxMessage, messageMoves.addedMailboxIds(), mailboxSession);
        removeMessageFromMailboxes(mailboxMessage, messageMoves.removedMailboxIds(), mailboxSession);

        eventBus.dispatch(EventFactory.moved()
            .session(mailboxSession)
            .messageMoves(messageMoves)
            .messageId(mailboxMessage.getMessageId())
            .build(),
            messageMoves.impactedMailboxIds()
                .map(MailboxIdRegistrationKey::new)
                .collect(Guavate.toImmutableSet()))
            .block();
    }

    private void removeMessageFromMailboxes(MailboxMessage message, Set<MailboxId> mailboxesToRemove, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        MessageMetaData eventPayload = message.metaData();

        for (MailboxId mailboxId: mailboxesToRemove) {
            messageIdMapper.delete(message.getMessageId(), mailboxesToRemove);
            eventBus.dispatch(EventFactory.expunged()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(mailboxMapper.findMailboxById(mailboxId))
                .addMetaData(eventPayload)
                .build(),
                new MailboxIdRegistrationKey(mailboxId))
            .block();
        }
    }

    protected MailboxMessage createMessage(Date internalDate, int size, int bodyStartOctet, SharedInputStream content, Flags flags, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments, MailboxId mailboxId) {
        return new SimpleMailboxMessage(messageIdFactory.generate(), internalDate, size, bodyStartOctet, content, flags, propertyBuilder, mailboxId, attachments);
    }
    
    private void dispatchFlagsChange(MailboxSession mailboxSession, MailboxId mailboxId, UpdatedFlags updatedFlags) throws MailboxException {
        if (updatedFlags.flagsChanged()) {
            Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);

            eventBus.dispatch(EventFactory.flagsUpdated()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(mailbox)
                .updatedFlag(updatedFlags)
                .build(),
                new MailboxIdRegistrationKey(mailboxId))
                .block();
        }
    }

    private void validateQuota(MessageMoves messageMoves, MailboxMessage mailboxMessage) throws MailboxException {
        Map<QuotaRoot, Integer> messageCountByQuotaRoot = buildMapQuotaRoot(messageMoves);
        for (Map.Entry<QuotaRoot, Integer> entry : messageCountByQuotaRoot.entrySet()) {
            Integer additionalCopyCount = entry.getValue();
            if (additionalCopyCount > 0) {
                long additionalOccupiedSpace = additionalCopyCount * mailboxMessage.getFullContentOctets();
                new QuotaChecker(quotaManager.getMessageQuota(entry.getKey()), quotaManager.getStorageQuota(entry.getKey()), entry.getKey())
                    .tryAddition(additionalCopyCount, additionalOccupiedSpace);
            }
        }
    }

    private Map<QuotaRoot, Integer> buildMapQuotaRoot(MessageMoves messageMoves) throws MailboxException {
        Map<QuotaRoot, Integer> messageCountByQuotaRoot = new HashMap<>();
        for (MailboxId mailboxId : messageMoves.addedMailboxIds()) {
            QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxId);
            int currentCount = Optional.ofNullable(messageCountByQuotaRoot.get(quotaRoot))
                .orElse(0);
            messageCountByQuotaRoot.put(quotaRoot, currentCount + 1);
        }
        for (MailboxId mailboxId : messageMoves.removedMailboxIds()) {
            QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxId);
            int currentCount = Optional.ofNullable(messageCountByQuotaRoot.get(quotaRoot))
                .orElse(0);
            messageCountByQuotaRoot.put(quotaRoot, currentCount - 1);
        }
        return messageCountByQuotaRoot;
    }

    private void addMessageToMailboxes(MailboxMessage mailboxMessage, Set<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        for (MailboxId mailboxId : mailboxIds) {
            boolean shouldPreserveFlags = mailboxManager.myRights(mailboxId, mailboxSession).contains(Right.Write);
            SimpleMailboxMessage copy =
                SimpleMailboxMessage.from(mailboxMessage)
                    .mailboxId(mailboxId)
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
            save(mailboxSession, messageIdMapper, copy);

            eventBus.dispatch(EventFactory.added()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(mailboxMapper.findMailboxById(mailboxId))
                .addMessage(copy)
                .build(),
                new MailboxIdRegistrationKey(mailboxId))
                .block();
        }
    }

    private void save(MailboxSession mailboxSession, MessageIdMapper messageIdMapper, MailboxMessage mailboxMessage) throws MailboxException {
        long modSeq = mailboxSessionMapperFactory.getModSeqProvider().nextModSeq(mailboxSession, mailboxMessage.getMailboxId());
        MessageUid uid = mailboxSessionMapperFactory.getUidProvider().nextUid(mailboxSession, mailboxMessage.getMailboxId());
        mailboxMessage.setModSeq(modSeq);
        mailboxMessage.setUid(uid);
        messageIdMapper.copyInMailbox(mailboxMessage);
    }

    private ThrowingFunction<MailboxMessage, MessageResult> messageResultConverter(MessageResult.FetchGroup fetchGroup) {
        return input -> ResultUtils.loadMessageResult(input, fetchGroup);
    }

    private Predicate<MailboxMessage> inMailboxes(Collection<MailboxId> mailboxIds) {
        return mailboxMessage -> mailboxIds.contains(mailboxMessage.getMailboxId());
    }

    private Predicate<MailboxMessage> hasRightsOn(MailboxSession session, Right... rights) {
        return message -> hasRightsOnMailbox(session, rights).test(message.getMailboxId());
    }


    private Predicate<MailboxId> hasRightsOnMailbox(MailboxSession session, Right... rights) {
        return Throwing.predicate((MailboxId mailboxId) -> mailboxManager.myRights(mailboxId, session).contains(rights))
            .fallbackTo(any -> false);
    }

    private void assertRightsOnMailboxes(Collection<MailboxId> mailboxIds, MailboxSession mailboxSession, Right... rights) throws MailboxNotFoundException {
        Optional<MailboxId> mailboxForbidden = mailboxIds.stream()
            .filter(hasRightsOnMailbox(mailboxSession, rights).negate())
            .findFirst();

        if (mailboxForbidden.isPresent()) {
            LOGGER.info("Mailbox with Id {} does not belong to {}", mailboxForbidden.get(), mailboxSession.getUser().asString());
            throw new MailboxNotFoundException(mailboxForbidden.get());
        }
    }
}
