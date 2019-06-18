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
package org.apache.james.mailbox.jpa.mail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.Flags;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAEncryptedMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAStreamingMailboxMessage;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageUtils;
import org.apache.james.mailbox.store.mail.MessageUtils.MessageChangedFlags;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;
import org.apache.openjpa.persistence.ArgumentException;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

/**
 * JPA implementation of a {@link MessageMapper}. This class is not thread-safe!
 */
public class JPAMessageMapper extends JPATransactionalMapper implements MessageMapper {
    private static final int UNLIMIT_MAX_SIZE = -1;
    private static final int UNLIMITED = -1;

    private final MessageUtils messageMetadataMapper;

    public JPAMessageMapper(MailboxSession mailboxSession, UidProvider uidProvider, ModSeqProvider modSeqProvider, EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
        this.messageMetadataMapper = new MessageUtils(mailboxSession, uidProvider, modSeqProvider);
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return MailboxCounters.builder()
            .count(countMessagesInMailbox(mailbox))
            .unseen(countUnseenMessagesInMailbox(mailbox))
            .build();
    }

    @Override
    public Iterator<MessageUid> listAllMessageUids(final Mailbox mailbox) throws MailboxException {
        return Iterators.transform(findInMailbox(mailbox, MessageRange.all(), FetchType.Full, UNLIMITED), MailboxMessage::getUid);
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {
        try {
            List<MailboxMessage> results;
            MessageUid from = set.getUidFrom();
            final MessageUid to = set.getUidTo();
            final Type type = set.getType();
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();

            switch (type) {
            default:
            case ALL:
                results = findMessagesInMailbox(mailboxId, max);
                break;
            case FROM:
                results = findMessagesInMailboxAfterUID(mailboxId, from, max);
                break;
            case ONE:
                results = findMessagesInMailboxWithUID(mailboxId, from);
                break;
            case RANGE:
                results = findMessagesInMailboxBetweenUIDs(mailboxId, from, to, max);
                break;
            }

            return results.iterator();

        } catch (PersistenceException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailbox, e);
        }
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            return (Long) getEntityManager().createNamedQuery("countMessagesInMailbox")
                    .setParameter("idParam", mailboxId.getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of messages failed in mailbox " + mailbox, e);
        }
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            return (Long) getEntityManager().createNamedQuery("countUnseenMessagesInMailbox")
                    .setParameter("idParam", mailboxId.getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of useen messages failed in mailbox " + mailbox, e);
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

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException {
        JPAId mailboxId = (JPAId) mailbox.getMailboxId();
        Map<MessageUid, MessageMetaData> data = new HashMap<>();
        List<MessageRange> ranges = MessageRange.toRanges(uids);

        ranges.forEach(range -> {
            List<MailboxMessage> messages = findDeletedMessages(range, mailboxId);
            data.putAll(createMetaData(messages));
            deleteDeletedMessages(range, mailboxId);
        });

        return data;
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

    private void deleteDeletedMessages(MessageRange messageRange, JPAId mailboxId) {
        MessageUid from = messageRange.getUidFrom();
        MessageUid to = messageRange.getUidTo();

        switch (messageRange.getType()) {
            case ONE:
                deleteDeletedMessagesInMailboxWithUID(mailboxId, from);
                break;
            case RANGE:
                deleteDeletedMessagesInMailboxBetweenUIDs(mailboxId, from, to);
                break;
            case FROM:
                deleteDeletedMessagesInMailboxAfterUID(mailboxId, from);
                break;
            case ALL:
                deleteDeletedMessagesInMailbox(mailboxId);
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
        Iterator<MailboxMessage> messages = findInMailbox(mailbox, set, FetchType.Metadata, UNLIMIT_MAX_SIZE);

        MessageChangedFlags messageChangedFlags = messageMetadataMapper.updateFlags(mailbox, flagsUpdateCalculator, messages);

        for (MailboxMessage mailboxMessage : messageChangedFlags.getChangedFlags()) {
            save(mailbox, mailboxMessage);
        }

        return messageChangedFlags.getUpdatedFlags();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return copy(mailbox, messageMetadataMapper.nextUid(mailbox), messageMetadataMapper.nextModSeq(mailbox), original);  
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return messageMetadataMapper.getLastUid(mailbox);
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return messageMetadataMapper.getHighestModSeq(mailbox);
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        int maxBatchSize = -1;
        return new ApplicableFlagCalculator(findMessagesInMailbox((JPAId) mailbox.getMailboxId(), maxBatchSize))
            .computeApplicableFlags();
    }

    private MessageMetaData copy(Mailbox mailbox, MessageUid uid, long modSeq, MailboxMessage original)
            throws MailboxException {
        MailboxMessage copy;
        JPAMailbox currentMailbox = JPAMailbox.from(mailbox);

        if (original instanceof JPAStreamingMailboxMessage) {
            copy = new JPAStreamingMailboxMessage(currentMailbox, uid, modSeq, original);
        } else if (original instanceof JPAEncryptedMailboxMessage) {
            copy = new JPAEncryptedMailboxMessage(currentMailbox, uid, modSeq, original);
        } else {
            copy = new JPAMailboxMessage(currentMailbox, uid, modSeq, original);
        }
        return save(mailbox, copy);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#save(Mailbox, MailboxMessage)
     */
    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            // We need to reload a "JPA attached" mailbox, because the provide
            // mailbox is already "JPA detached"
            // If we don't this, we will get an
            // org.apache.openjpa.persistence.ArgumentException.
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            JPAMailbox currentMailbox = getEntityManager().find(JPAMailbox.class, mailboxId.getRawId());
            if (message instanceof AbstractJPAMailboxMessage) {
                ((AbstractJPAMailboxMessage) message).setMailbox(currentMailbox);

                getEntityManager().persist(message);
                return message.metaData();
            } else {
                JPAMailboxMessage persistData = new JPAMailboxMessage(currentMailbox, message.getUid(), message.getModSeq(), message);
                persistData.setFlags(message.createFlags());
                getEntityManager().persist(persistData);
                return persistData.metaData();
            }

        } catch (PersistenceException | ArgumentException e) {
            throw new MailboxException("Save of message " + message + " failed in mailbox " + mailbox, e);
        }
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
            .map(message -> message.getUid())
            .collect(Guavate.toImmutableList());
    }

    private int deleteDeletedMessagesInMailbox(JPAId mailboxId) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailbox")
                .setParameter("idParam", mailboxId.getRawId()).executeUpdate();
    }

    private int deleteDeletedMessagesInMailboxAfterUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailboxAfterUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).executeUpdate();
    }

    private int deleteDeletedMessagesInMailboxWithUID(JPAId mailboxId, MessageUid from) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailboxWithUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong()).executeUpdate();
    }

    private int deleteDeletedMessagesInMailboxBetweenUIDs(JPAId mailboxId, MessageUid from, MessageUid to) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailboxBetweenUIDs")
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
