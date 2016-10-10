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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAEncryptedMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAStreamingMailboxMessage;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.openjpa.persistence.ArgumentException;

import com.google.common.collect.ImmutableList;

/**
 * JPA implementation of a {@link MessageMapper}. This class is not thread-safe!
 */
public class JPAMessageMapper extends AbstractMessageMapper implements MessageMapper {
    protected EntityManagerFactory entityManagerFactory;
    protected EntityManager entityManager;

    public JPAMessageMapper(MailboxSession session, UidProvider uidProvider,
            ModSeqProvider modSeqProvider, EntityManagerFactory entityManagerFactory) {
        super(session, uidProvider, modSeqProvider);
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Return the currently used {@link EntityManager} or a new one if none
     * exists.
     * 
     * @return entitymanger
     */
    public EntityManager getEntityManager() {
        if (entityManager != null)
            return entityManager;
        entityManager = entityManagerFactory.createEntityManager();
        return entityManager;
    }

    /**
     * @see org.apache.james.mailbox.store.transaction.TransactionalMapper#begin()
     */
    protected void begin() throws MailboxException {
        try {
            getEntityManager().getTransaction().begin();
        } catch (PersistenceException e) {
            throw new MailboxException("Begin of transaction failed", e);
        }
    }

    /**
     * Commit the Transaction and close the EntityManager
     */
    protected void commit() throws MailboxException {
        try {
            getEntityManager().getTransaction().commit();
        } catch (PersistenceException e) {
            throw new MailboxException("Commit of transaction failed", e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.transaction.TransactionalMapper#rollback()
     */
    protected void rollback() throws MailboxException {
        EntityTransaction transaction = entityManager.getTransaction();
        // check if we have a transaction to rollback
        if (transaction.isActive()) {
            getEntityManager().getTransaction().rollback();
        }
    }

    /**
     * Close open {@link EntityManager}
     */
    public void endRequest() {
        if (entityManager != null) {
            if (entityManager.isOpen())
                entityManager.close();
            entityManager = null;
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.store.mail.MessageMapper.FetchType, int)
     */
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

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countMessagesInMailbox(Mailbox)
     */
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            return (Long) getEntityManager().createNamedQuery("countMessagesInMailbox")
                    .setParameter("idParam", mailboxId.getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of messages failed in mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countUnseenMessagesInMailbox(Mailbox)
     */
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            return (Long) getEntityManager().createNamedQuery("countUnseenMessagesInMailbox")
                    .setParameter("idParam", mailboxId.getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of useen messages failed in mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#delete(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      MailboxMessage)
     */
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            getEntityManager().remove(message);
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of message " + message + " failed in mailbox " + mailbox, e);
        }
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
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set)
            throws MailboxException {
        try {
            final Map<MessageUid, MessageMetaData> data;
            final List<MailboxMessage> results;
            final MessageUid from = set.getUidFrom();
            final MessageUid to = set.getUidTo();
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();

            switch (set.getType()) {
            case ONE:
                results = findDeletedMessagesInMailboxWithUID(mailboxId, from);
                data = createMetaData(results);
                deleteDeletedMessagesInMailboxWithUID(mailboxId, from);
                break;
            case RANGE:
                results = findDeletedMessagesInMailboxBetweenUIDs(mailboxId, from, to);
                data = createMetaData(results);
                deleteDeletedMessagesInMailboxBetweenUIDs(mailboxId, from, to);
                break;
            case FROM:
                results = findDeletedMessagesInMailboxAfterUID(mailboxId, from);
                data = createMetaData(results);
                deleteDeletedMessagesInMailboxAfterUID(mailboxId, from);
                break;
            default:
            case ALL:
                results = findDeletedMessagesInMailbox(mailboxId);
                data = createMetaData(results);
                deleteDeletedMessagesInMailbox(mailboxId);
                break;
            }

            return data;
        } catch (PersistenceException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailbox, e);
        }
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.MessageMapper#move(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      MailboxMessage)
     */
    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        throw new UnsupportedOperationException("Not implemented - see https://issues.apache.org/jira/browse/IMAP-370");
    }

    @Override
    protected MessageMetaData copy(Mailbox mailbox, MessageUid uid, long modSeq, MailboxMessage original)
            throws MailboxException {
        MailboxMessage copy;
        if (original instanceof JPAStreamingMailboxMessage) {
            copy = new JPAStreamingMailboxMessage((JPAMailbox) mailbox, uid, modSeq, original);
        } else if (original instanceof JPAEncryptedMailboxMessage) {
            copy = new JPAEncryptedMailboxMessage((JPAMailbox) mailbox, uid, modSeq, original);
        } else {
            copy = new JPAMailboxMessage((JPAMailbox) mailbox, uid, modSeq, original);
        }
        return save(mailbox, copy);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#save(Mailbox,
     *      MailboxMessage)
     */
    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {

        try {

            // We need to reload a "JPA attached" mailbox, because the provide
            // mailbox is already "JPA detached"
            // If we don't this, we will get an
            // org.apache.openjpa.persistence.ArgumentException.
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            ((AbstractJPAMailboxMessage) message)
                    .setMailbox(getEntityManager().find(JPAMailbox.class, mailboxId.getRawId()));

            getEntityManager().persist(message);
            return new SimpleMessageMetaData(message);
        } catch (PersistenceException e) {
            throw new MailboxException("Save of message " + message + " failed in mailbox " + mailbox, e);
        } catch (ArgumentException e) {
            throw new MailboxException("Save of message " + message + " failed in mailbox " + mailbox, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findMessagesInMailboxAfterUID(JPAId mailboxId, MessageUid from, int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailboxAfterUID")
                .setParameter("idParam", mailboxId.getRawId()).setParameter("uidParam", from.asLong());

        if (batchSize > 0)
            query.setMaxResults(batchSize);

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

        if (batchSize > 0)
            query.setMaxResults(batchSize);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MailboxMessage> findMessagesInMailbox(JPAId mailboxId, int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailbox").setParameter("idParam",
                mailboxId.getRawId());
        if (batchSize > 0)
            query.setMaxResults(batchSize);
        return query.getResultList();
    }

    private Map<MessageUid, MessageMetaData> createMetaData(List<MailboxMessage> uids) {
        final Map<MessageUid, MessageMetaData> data = new HashMap<MessageUid, MessageMetaData>();
        for (MailboxMessage m : uids) {
            data.put(m.getUid(), new SimpleMessageMetaData(m));
        }
        return data;
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
