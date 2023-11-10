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
package org.apache.james.mailbox.postgres.mail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.Flags;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.JPAId;
import org.apache.james.mailbox.postgres.JPATransactionalMapper;
import org.apache.james.mailbox.postgres.mail.MessageUtils.MessageChangedFlags;
import org.apache.james.mailbox.postgres.mail.model.JPAAttachment;
import org.apache.james.mailbox.postgres.mail.model.JPAMailbox;
import org.apache.james.mailbox.postgres.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.postgres.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.openjpa.persistence.ArgumentException;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * JPA implementation of a {@link MessageMapper}. This class is not thread-safe!
 */
public class JPAMessageMapper extends JPATransactionalMapper implements MessageMapper {
    private static final int UNLIMIT_MAX_SIZE = -1;
    private static final int UNLIMITED = -1;

    private final MessageUtils messageMetadataMapper;
    private final JPAUidProvider uidProvider;
    private final JPAModSeqProvider modSeqProvider;
    private final JPAConfiguration jpaConfiguration;

    public JPAMessageMapper(JPAUidProvider uidProvider, JPAModSeqProvider modSeqProvider, EntityManagerFactory entityManagerFactory,
                            JPAConfiguration jpaConfiguration) {
        super(entityManagerFactory);
        this.messageMetadataMapper = new MessageUtils(uidProvider, modSeqProvider);
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.jpaConfiguration = jpaConfiguration;
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return MailboxCounters.builder()
            .mailboxId(mailbox.getMailboxId())
            .count(countMessagesInMailbox(mailbox))
            .unseen(countUnseenMessagesInMailbox(mailbox))
            .build();
    }

    @Override
    public Flux<MessageUid> listAllMessageUids(Mailbox mailbox) {
        return Mono.fromCallable(() -> {
            try {
                JPAId mailboxId = (JPAId) mailbox.getMailboxId();
                Query query = getEntityManager().createNamedQuery("listUidsInMailbox")
                    .setParameter("idParam", mailboxId.getRawId());
                return query.getResultStream().map(result -> MessageUid.of((Long) result));
            } catch (PersistenceException e) {
                throw new MailboxException("Search of recent messages failed in mailbox " + mailbox, e);
            }
        }).flatMapMany(Flux::fromStream)
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<MailboxMessage> findInMailboxReactive(Mailbox mailbox, MessageRange messageRange, FetchType ftype, int limitAsInt) {
        return Flux.defer(Throwing.supplier(() -> Flux.fromIterable(findAsList(mailbox.getMailboxId(), messageRange, limitAsInt))).sneakyThrow())
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {

        return findAsList(mailbox.getMailboxId(), set, max).iterator();
    }

    private List<MailboxMessage> findAsList(MailboxId mailboxId, MessageRange set, int max) throws MailboxException {
        try {
            MessageUid from = set.getUidFrom();
            MessageUid to = set.getUidTo();
            Type type = set.getType();
            JPAId jpaId = (JPAId) mailboxId;

            switch (type) {
                default:
                case ALL:
                    return findMessagesInMailbox(jpaId, max);
                case FROM:
                    return findMessagesInMailboxAfterUID(jpaId, from, max);
                case ONE:
                    return findMessagesInMailboxWithUID(jpaId, from);
                case RANGE:
                    return findMessagesInMailboxBetweenUIDs(jpaId, from, to, max);
            }
        } catch (PersistenceException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailboxId.serialize(), e);
        }
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        JPAId mailboxId = (JPAId) mailbox.getMailboxId();
        return countMessagesInMailbox(mailboxId);
    }

    private long countMessagesInMailbox(JPAId mailboxId) throws MailboxException {
        try {
            return (Long) getEntityManager().createNamedQuery("countMessagesInMailbox")
                    .setParameter("idParam", mailboxId.getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of messages failed in mailbox " + mailboxId, e);
        }
    }

    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        JPAId mailboxId = (JPAId) mailbox.getMailboxId();
        return countUnseenMessagesInMailbox(mailboxId);
    }

    private long countUnseenMessagesInMailbox(JPAId mailboxId) throws MailboxException {
        try {
            return (Long) getEntityManager().createNamedQuery("countUnseenMessagesInMailbox")
                    .setParameter("idParam", mailboxId.getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of useen messages failed in mailbox " + mailboxId, e);
        }
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            AbstractJPAMailboxMessage jpaMessage = getEntityManager().find(AbstractJPAMailboxMessage.class, buildKey(mailbox, message));
            getEntityManager().remove(jpaMessage);

        } catch (PersistenceException e) {
            throw new MailboxException("Delete of message " + message + " failed in mailbox " + mailbox, e);
        }
    }

    private AbstractJPAMailboxMessage.MailboxIdUidKey buildKey(Mailbox mailbox, MailboxMessage message) {
        JPAId mailboxId = (JPAId) mailbox.getMailboxId();
        AbstractJPAMailboxMessage.MailboxIdUidKey key = new AbstractJPAMailboxMessage.MailboxIdUidKey();
        key.mailbox = mailboxId.getRawId();
        key.uid = message.getUid().asLong();
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            Query query = getEntityManager().createNamedQuery("findUnseenMessagesInMailboxOrderByUid").setParameter(
                    "idParam", mailboxId.getRawId());
            query.setMaxResults(1);
            List<MailboxMessage> result = query.getResultList();
            if (result.isEmpty()) {
                return null;
            } else {
                return result.get(0).getUid();
            }
        } catch (PersistenceException e) {
            throw new MailboxException("Search of first unseen message failed in mailbox " + mailbox, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            Query query = getEntityManager().createNamedQuery("findRecentMessageUidsInMailbox").setParameter("idParam",
                    mailboxId.getRawId());
            List<Long> resultList = query.getResultList();
            ImmutableList.Builder<MessageUid> results = ImmutableList.builder();
            for (long result: resultList) {
                results.add(MessageUid.of(result));
            }
            return results.build();
        } catch (PersistenceException e) {
            throw new MailboxException("Search of recent messages failed in mailbox " + mailbox, e);
        }
    }



    @Override
    public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            List<MailboxMessage> messages = findDeletedMessages(messageRange, mailboxId);
            return getUidList(messages);
        } catch (PersistenceException e) {
            throw new MailboxException("Search of MessageRange " + messageRange + " failed in mailbox " + mailbox, e);
        }
    }

    private List<MailboxMessage> findDeletedMessages(MessageRange messageRange, JPAId mailboxId) {
        MessageUid from = messageRange.getUidFrom();
        MessageUid to = messageRange.getUidTo();

        switch (messageRange.getType()) {
            case ONE:
                return findDeletedMessagesInMailboxWithUID(mailboxId, from);
            case RANGE:
                return findDeletedMessagesInMailboxBetweenUIDs(mailboxId, from, to);
            case FROM:
                return findDeletedMessagesInMailboxAfterUID(mailboxId, from);
            case ALL:
                return findDeletedMessagesInMailbox(mailboxId);
            default:
                throw new RuntimeException("Cannot find deleted messages, range type " + messageRange.getType() + " doesn't exist");
        }
    }

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException {
        JPAId mailboxId = (JPAId) mailbox.getMailboxId();
        Map<MessageUid, MessageMetaData> data = new HashMap<>();
        List<MessageRange> ranges = MessageRange.toRanges(uids);

        ranges.forEach(Throwing.<MessageRange>consumer(range -> {
            List<MailboxMessage> messages = findAsList(mailboxId, range, JPAMessageMapper.UNLIMITED);
            data.putAll(createMetaData(messages));
            deleteMessages(range, mailboxId);
        }).sneakyThrow());

        return data;
    }

    private void deleteMessages(MessageRange messageRange, JPAId mailboxId) {
        MessageUid from = messageRange.getUidFrom();
        MessageUid to = messageRange.getUidTo();

        switch (messageRange.getType()) {
            case ONE:
                deleteMessagesInMailboxWithUID(mailboxId, from);
                break;
            case RANGE:
                deleteMessagesInMailboxBetweenUIDs(mailboxId, from, to);
                break;
            case FROM:
                deleteMessagesInMailboxAfterUID(mailboxId, from);
                break;
            case ALL:
                deleteMessagesInMailbox(mailboxId);
                break;
            default:
                throw new RuntimeException("Cannot delete messages, range type " + messageRange.getType() + " doesn't exist");
        }
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        JPAId originalMailboxId = (JPAId) original.getMailboxId();
        JPAMailbox originalMailbox = getEntityManager().find(JPAMailbox.class, originalMailboxId.getRawId());

        MessageMetaData messageMetaData = copy(mailbox, original);
        delete(originalMailbox.toMailbox(), original);

        return messageMetaData;
    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        messageMetadataMapper.enrichMessage(mailbox, message);

        return save(mailbox, message);
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator,
            MessageRange set) throws MailboxException {
        Iterator<MailboxMessage> messages = findInMailbox(mailbox, set, FetchType.METADATA, UNLIMIT_MAX_SIZE);

        MessageChangedFlags messageChangedFlags = messageMetadataMapper.updateFlags(mailbox, flagsUpdateCalculator, messages);

        for (MailboxMessage mailboxMessage : messageChangedFlags.getChangedFlags()) {
            save(mailbox, mailboxMessage);
        }

        return messageChangedFlags.getUpdatedFlags();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return copy(mailbox, uidProvider.nextUid(mailbox), modSeqProvider.nextModSeq(mailbox), original);
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailbox, getEntityManager());
    }

    @Override
    public ModSeq getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailbox.getMailboxId(), getEntityManager());
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        JPAId jpaId = (JPAId) mailbox.getMailboxId();
        ApplicableFlagBuilder builder = ApplicableFlagBuilder.builder();
        List<String> flags = getEntityManager().createNativeQuery("SELECT DISTINCT USERFLAG_NAME FROM JAMES_MAIL_USERFLAG WHERE MAILBOX_ID=?")
                .setParameter(1, jpaId.getRawId())
                .getResultList();
        flags.forEach(builder::add);
        return builder.build();
    }

    private MessageMetaData copy(Mailbox mailbox, MessageUid uid, ModSeq modSeq, MailboxMessage original)
            throws MailboxException {
        MailboxMessage copy;
        JPAMailbox currentMailbox = JPAMailbox.from(mailbox);

        copy = new JPAMailboxMessage(currentMailbox, uid, modSeq, original);
        return save(mailbox, copy);
    }

    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            // We need to reload a "JPA attached" mailbox, because the provide
            // mailbox is already "JPA detached"
            // If we don't this, we will get an
            // org.apache.openjpa.persistence.ArgumentException.
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            JPAMailbox currentMailbox = getEntityManager().find(JPAMailbox.class, mailboxId.getRawId());

            boolean isAttachmentStorage = false;
            if (Objects.nonNull(jpaConfiguration)) {
                isAttachmentStorage = jpaConfiguration.isAttachmentStorageEnabled().orElse(false);
            }

            if (message instanceof AbstractJPAMailboxMessage) {
                ((AbstractJPAMailboxMessage) message).setMailbox(currentMailbox);

                getEntityManager().persist(message);
                return message.metaData();
            }  else {
                JPAMailboxMessage persistData = new JPAMailboxMessage(currentMailbox, message.getUid(), message.getModSeq(), message);
                persistData.setFlags(message.createFlags());
                getEntityManager().persist(persistData);
                return persistData.metaData();
            }

        } catch (PersistenceException | ArgumentException e) {
            throw new MailboxException("Save of message " + message + " failed in mailbox " + mailbox, e);
        }
    }

    private List<JPAAttachment> getAttachments(MailboxMessage message) {
        return message.getAttachments().stream()
            .map(MessageAttachmentMetadata::getAttachmentId)
            .map(attachmentId -> getEntityManager().createNamedQuery("findAttachmentById", JPAAttachment.class)
                .setParameter("idParam", attachmentId.getId())
                .getSingleResult())
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findMessagesInMailboxAfterUID(JPAId mailboxId, MessageUid from, int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailboxAfterUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong());

        if (batchSize > 0) {
            query.setMaxResults(batchSize);
        }

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findMessagesInMailboxWithUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("findMessagesInMailboxWithUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).setMaxResults(1)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findMessagesInMailboxBetweenUIDs(JPAId mailboxId, MessageUid from, MessageUid to,
                                                                         int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailboxBetweenUIDs")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("fromParam", from.asLong())
                .setParameter("toParam", to.asLong());

        if (batchSize > 0) {
            query.setMaxResults(batchSize);
        }

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findMessagesInMailbox(JPAId mailboxId, int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailbox").setParameter("idParam",
                mailboxId.getRawId());
        if (batchSize > 0) {
            query.setMaxResults(batchSize);
        }
        return query.getResultList();
    }

    private Map<MessageUid, MessageMetaData> createMetaData(List<MailboxMessage> uids) {
        final Map<MessageUid, MessageMetaData> data = new HashMap<>();
        for (MailboxMessage m : uids) {
            data.put(m.getUid(), m.metaData());
        }
        return data;
    }

    private List<MessageUid> getUidList(List<MailboxMessage> messages) {
        return messages.stream()
            .map(MailboxMessage::getUid)
            .collect(ImmutableList.toImmutableList());
    }

    private int deleteMessagesInMailbox(JPAId mailboxId) {
        return getEntityManager().createNamedQuery("deleteMessagesInMailbox")
                .setParameter("idParam", mailboxId.getRawId()).executeUpdate();
    }

    private int deleteMessagesInMailboxAfterUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("deleteMessagesInMailboxAfterUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).executeUpdate();
    }

    private int deleteMessagesInMailboxWithUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("deleteMessagesInMailboxWithUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).executeUpdate();
    }

    private int deleteMessagesInMailboxBetweenUIDs(JPAId mailboxId, MessageUid from, MessageUid to) {
        return getEntityManager().createNamedQuery("deleteMessagesInMailboxBetweenUIDs")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("fromParam", from.asLong())
                .setParameter("toParam", to.asLong()).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findDeletedMessagesInMailbox(JPAId mailboxId) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailbox")
                .setParameter("idParam", mailboxId.getRawId()).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findDeletedMessagesInMailboxAfterUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailboxAfterUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findDeletedMessagesInMailboxWithUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailboxWithUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).setMaxResults(1)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findDeletedMessagesInMailboxBetweenUIDs(JPAId mailboxId, MessageUid from, MessageUid to) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailboxBetweenUIDs")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("fromParam", from.asLong())
                .setParameter("toParam", to.asLong()).getResultList();
    }
}
