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
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class StoreMessageIdManager implements MessageIdManager {

    private static class MessageMoves {
        private final ImmutableSet<MailboxId> previousMailboxIds;
        private final ImmutableSet<MailboxId> targetMailboxIds;

        public MessageMoves(Collection<MailboxId> previousMailboxIds, Collection<MailboxId> targetMailboxIds) {
            this.previousMailboxIds = ImmutableSet.copyOf(previousMailboxIds);
            this.targetMailboxIds = ImmutableSet.copyOf(targetMailboxIds);
        }

        public boolean isChange() {
            return !previousMailboxIds.equals(targetMailboxIds);
        }

        public Set<MailboxId> addedMailboxIds() {
            return Sets.difference(targetMailboxIds, previousMailboxIds);
        }

        public Set<MailboxId> removedMailboxIds() {
            return Sets.difference(previousMailboxIds, targetMailboxIds);
        }
    }

    private static class MetadataWithMailboxId {
        private final MessageMetaData messageMetaData;
        private final MailboxId mailboxId;

        public MetadataWithMailboxId(MessageMetaData messageMetaData, MailboxId mailboxId) {
            this.messageMetaData = messageMetaData;
            this.mailboxId = mailboxId;
        }
    }

    private static class WrappedException extends RuntimeException {
        private final MailboxException cause;

        public WrappedException(MailboxException cause) {
            this.cause = cause;
        }

        public MailboxException unwrap() throws MailboxException {
            throw cause;
        }
    }

    private static MetadataWithMailboxId toMetadataWithMailboxId(MailboxMessage message) {
        return new MetadataWithMailboxId(new SimpleMessageMetaData(message), message.getMailboxId());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreMessageIdManager.class);

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MailboxEventDispatcher dispatcher;
    private final MessageId.Factory messageIdFactory;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public StoreMessageIdManager(MailboxManager mailboxManager, MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                 MailboxEventDispatcher dispatcher, MessageId.Factory messageIdFactory,
                                 QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) {
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.dispatcher = dispatcher;
        this.messageIdFactory = messageIdFactory;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
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
        return messageList.stream()
            .filter(hasRightsOn(mailboxSession, Right.Read))
            .map(message -> message.getComposedMessageIdWithMetaData().getComposedMessageId().getMessageId())
            .collect(Guavate.toImmutableSet());
    }

    @Override
    public List<MessageResult> getMessages(List<MessageId> messageIds, MessageResult.FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        try {
            MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
            List<MailboxMessage> messageList = messageIdMapper.find(messageIds, MessageMapper.FetchType.Full);

            ImmutableSet<MailboxId> allowedMailboxIds = messageList.stream()
                .map(MailboxMessage::getMailboxId)
                .distinct()
                .filter(hasRightsOnMailbox(mailboxSession, Right.Read))
                .collect(Guavate.toImmutableSet());

            return messageList.stream()
                .filter(inMailboxes(allowedMailboxIds))
                .map(messageResultConverter(fetchGroup))
                .collect(Guavate.toImmutableList());
        } catch (WrappedException wrappedException) {
            throw wrappedException.unwrap();
        }
    }

    @Override
    public void delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        assertRightsOnMailboxes(mailboxIds, mailboxSession, Right.DeleteMessages);

        ImmutableList<MetadataWithMailboxId> metadataWithMailbox = messageIdMapper
            .find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .filter(inMailboxes(mailboxIds))
            .map(StoreMessageIdManager::toMetadataWithMailboxId)
            .collect(Guavate.toImmutableList());

        messageIdMapper.delete(messageId, mailboxIds);

        for (MetadataWithMailboxId metadataWithMailboxId : metadataWithMailbox) {
            dispatcher.expunged(mailboxSession, metadataWithMailboxId.messageMetaData, mailboxMapper.findMailboxById(metadataWithMailboxId.mailboxId));
        }
    }

    @Override
    public void setInMailboxes(MessageId messageId, List<MailboxId> targetMailboxIds, MailboxSession mailboxSession) throws MailboxException {
        assertRightsOnMailboxes(targetMailboxIds, mailboxSession, Right.Read);

        List<MailboxMessage> currentMailboxMessages = findRelatedMailboxMessages(messageId, mailboxSession);

        if (currentMailboxMessages.isEmpty()) {
            LOGGER.info("Tried to access {} not accessible for {}", messageId, mailboxSession.getUser().getUserName());
            return;
        }

        MessageMoves messageMoves = new MessageMoves(toMailboxIds(currentMailboxMessages), targetMailboxIds);

        if (messageMoves.isChange()) {
            applyMessageMoves(mailboxSession, currentMailboxMessages, messageMoves);
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

        MailboxMessage mailboxMessage = currentMailboxMessages.stream().findAny().orElseThrow(() -> new MailboxNotFoundException("can't load message"));

        validateQuota(messageMoves, mailboxSession, mailboxMessage);

        addMessageToMailboxes(mailboxMessage, messageMoves.addedMailboxIds(), mailboxSession);
        removeMessageFromMailboxes(mailboxMessage, messageMoves.removedMailboxIds(), mailboxSession);
    }

    private void removeMessageFromMailboxes(MailboxMessage message, Set<MailboxId> mailboxesToRemove, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        SimpleMessageMetaData eventPayload = new SimpleMessageMetaData(message);

        for (MailboxId mailboxId: mailboxesToRemove) {
            messageIdMapper.delete(message.getMessageId(), mailboxesToRemove);
            dispatcher.expunged(mailboxSession, eventPayload, mailboxMapper.findMailboxById(mailboxId));
        }
    }

    private ImmutableSet<MailboxId> toMailboxIds(List<MailboxMessage> mailboxMessages) {
        return mailboxMessages
            .stream()
            .map(MailboxMessage::getMailboxId)
            .collect(Guavate.toImmutableSet());
    }

    protected MailboxMessage createMessage(Date internalDate, int size, int bodyStartOctet, SharedInputStream content, Flags flags, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments, MailboxId mailboxId) throws MailboxException {
        return new SimpleMailboxMessage(messageIdFactory.generate(), internalDate, size, bodyStartOctet, content, flags, propertyBuilder, mailboxId, attachments);
    }
    
    private void dispatchFlagsChange(MailboxSession mailboxSession, MailboxId mailboxId, UpdatedFlags updatedFlags) throws MailboxException {
        if (updatedFlags.flagsChanged()) {
            Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
            dispatcher.flagsUpdated(mailboxSession, updatedFlags.getUid(), mailbox, updatedFlags);
        }
    }

    private void validateQuota(MessageMoves messageMoves, MailboxSession mailboxSession, MailboxMessage mailboxMessage) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        Map<QuotaRoot, Integer> messageCountByQuotaRoot = buildMapQuotaRoot(messageMoves, mailboxMapper);
        for (Map.Entry<QuotaRoot, Integer> entry : messageCountByQuotaRoot.entrySet()) {
            Integer additionalCopyCount = entry.getValue();
            if (additionalCopyCount > 0) {
                long additionalOccupiedSpace = additionalCopyCount * mailboxMessage.getFullContentOctets();
                new QuotaChecker(quotaManager.getMessageQuota(entry.getKey()), quotaManager.getStorageQuota(entry.getKey()), entry.getKey())
                    .tryAddition(additionalCopyCount, additionalOccupiedSpace);
            }
        }
    }

    private Map<QuotaRoot, Integer> buildMapQuotaRoot(MessageMoves messageMoves, MailboxMapper mailboxMapper) throws MailboxException {
        Map<QuotaRoot, Integer> messageCountByQuotaRoot = new HashMap<>();
        for (MailboxId mailboxId : messageMoves.addedMailboxIds()) {
            QuotaRoot quotaRoot = retrieveQuotaRoot(mailboxMapper, mailboxId);
            int currentCount = Optional.ofNullable(messageCountByQuotaRoot.get(quotaRoot))
                .orElse(0);
            messageCountByQuotaRoot.put(quotaRoot, currentCount + 1);
        }
        for (MailboxId mailboxId : messageMoves.removedMailboxIds()) {
            QuotaRoot quotaRoot = retrieveQuotaRoot(mailboxMapper, mailboxId);
            int currentCount = Optional.ofNullable(messageCountByQuotaRoot.get(quotaRoot))
                .orElse(0);
            messageCountByQuotaRoot.put(quotaRoot, currentCount - 1);
        }
        return messageCountByQuotaRoot;
    }

    private QuotaRoot retrieveQuotaRoot(MailboxMapper mailboxMapper, MailboxId mailboxId) throws MailboxException {
        Mailbox mailbox = mailboxMapper.findMailboxById(mailboxId);
        return quotaRootResolver.getQuotaRoot(mailbox.generateAssociatedPath());
    }

    private void addMessageToMailboxes(MailboxMessage mailboxMessage, Set<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(mailboxSession);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        for (MailboxId mailboxId : mailboxIds) {
            SimpleMailboxMessage copy = SimpleMailboxMessage.copy(mailboxId, mailboxMessage);
            save(mailboxSession, messageIdMapper, copy);
            dispatcher.added(mailboxSession, mailboxMapper.findMailboxById(mailboxId), copy);
        }
    }

    private void save(MailboxSession mailboxSession, MessageIdMapper messageIdMapper, MailboxMessage mailboxMessage) throws MailboxException {
        long modSeq = mailboxSessionMapperFactory.getModSeqProvider().nextModSeq(mailboxSession, mailboxMessage.getMailboxId());
        MessageUid uid = mailboxSessionMapperFactory.getUidProvider().nextUid(mailboxSession, mailboxMessage.getMailboxId());
        mailboxMessage.setModSeq(modSeq);
        mailboxMessage.setUid(uid);
        messageIdMapper.copyInMailbox(mailboxMessage);
    }

    private Function<MailboxMessage, MessageResult> messageResultConverter(MessageResult.FetchGroup fetchGroup) {
        return input -> {
            try {
                return ResultUtils.loadMessageResult(input, fetchGroup);
            } catch (MailboxException e) {
                throw new WrappedException(e);
            }
        };
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
            LOGGER.info("Mailbox with Id " + mailboxForbidden.get() + " does not belong to " + mailboxSession.getUser().getUserName());
            throw new MailboxNotFoundException(mailboxForbidden.get());
        }
    }
}
