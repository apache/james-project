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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAEncryptedMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAStreamingMessage;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.openjpa.persistence.ArgumentException;

/**
 * JPA implementation of a {@link MessageMapper}. This class is not thread-safe!
 */
public class JPAMessageMapper extends AbstractMessageMapper<JPAId> implements MessageMapper<JPAId> {
    protected EntityManagerFactory entityManagerFactory;
    protected EntityManager entityManager;

    public JPAMessageMapper(final MailboxSession session, final UidProvider<JPAId> uidProvider,
            ModSeqProvider<JPAId> modSeqProvider, final EntityManagerFactory entityManagerFactory) {
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
    public Iterator<Message<JPAId>> findInMailbox(Mailbox<JPAId> mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {
        try {
            List<Message<JPAId>> results;
            long from = set.getUidFrom();
            final long to = set.getUidTo();
            final Type type = set.getType();

            switch (type) {
            default:
            case ALL:
                results = findMessagesInMailbox(mailbox, max);
                break;
            case FROM:
                results = findMessagesInMailboxAfterUID(mailbox, from, max);
                break;
            case ONE:
                results = findMessagesInMailboxWithUID(mailbox, from);
                break;
            case RANGE:
                results = findMessagesInMailboxBetweenUIDs(mailbox, from, to, max);
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
    public long countMessagesInMailbox(Mailbox<JPAId> mailbox) throws MailboxException {
        try {
            return (Long) getEntityManager().createNamedQuery("countMessagesInMailbox")
                    .setParameter("idParam", mailbox.getMailboxId().getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of messages failed in mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countUnseenMessagesInMailbox(Mailbox)
     */
    public long countUnseenMessagesInMailbox(Mailbox<JPAId> mailbox) throws MailboxException {
        try {
            return (Long) getEntityManager().createNamedQuery("countUnseenMessagesInMailbox")
                    .setParameter("idParam", mailbox.getMailboxId().getRawId()).getSingleResult();
        } catch (PersistenceException e) {
            throw new MailboxException("Count of useen messages failed in mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#delete(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    public void delete(Mailbox<JPAId> mailbox, Message<JPAId> message) throws MailboxException {
        try {
            getEntityManager().remove(message);
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of message " + message + " failed in mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findFirstUnseenMessageUid(Mailbox)
     */
    @SuppressWarnings("unchecked")
    public Long findFirstUnseenMessageUid(Mailbox<JPAId> mailbox) throws MailboxException {
        try {
            Query query = getEntityManager().createNamedQuery("findUnseenMessagesInMailboxOrderByUid").setParameter(
                    "idParam", mailbox.getMailboxId().getRawId());
            query.setMaxResults(1);
            List<Message<JPAId>> result = query.getResultList();
            if (result.isEmpty()) {
                return null;
            } else {
                return result.get(0).getUid();
            }
        } catch (PersistenceException e) {
            throw new MailboxException("Search of first unseen message failed in mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findRecentMessageUidsInMailbox(Mailbox)
     */
    @SuppressWarnings("unchecked")
    public List<Long> findRecentMessageUidsInMailbox(Mailbox<JPAId> mailbox) throws MailboxException {
        try {
            Query query = getEntityManager().createNamedQuery("findRecentMessageUidsInMailbox").setParameter("idParam",
                    mailbox.getMailboxId().getRawId());
            return query.getResultList();
        } catch (PersistenceException e) {
            throw new MailboxException("Search of recent messages failed in mailbox " + mailbox, e);
        }
    }

    @Override
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox<JPAId> mailbox, MessageRange set)
            throws MailboxException {
        try {
            final Map<Long, MessageMetaData> data;
            final List<Message<JPAId>> results;
            final long from = set.getUidFrom();
            final long to = set.getUidTo();

            switch (set.getType()) {
            case ONE:
                results = findDeletedMessagesInMailboxWithUID(mailbox, from);
                data = createMetaData(results);
                deleteDeletedMessagesInMailboxWithUID(mailbox, from);
                break;
            case RANGE:
                results = findDeletedMessagesInMailboxBetweenUIDs(mailbox, from, to);
                data = createMetaData(results);
                deleteDeletedMessagesInMailboxBetweenUIDs(mailbox, from, to);
                break;
            case FROM:
                results = findDeletedMessagesInMailboxAfterUID(mailbox, from);
                data = createMetaData(results);
                deleteDeletedMessagesInMailboxAfterUID(mailbox, from);
                break;
            default:
            case ALL:
                results = findDeletedMessagesInMailbox(mailbox);
                data = createMetaData(results);
                deleteDeletedMessagesInMailbox(mailbox);
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
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData move(Mailbox<JPAId> mailbox, Message<JPAId> original) throws MailboxException {
        throw new UnsupportedOperationException("Not implemented - see https://issues.apache.org/jira/browse/IMAP-370");
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#copy(Mailbox,
     *      long, long, Message)
     */
    protected MessageMetaData copy(Mailbox<JPAId> mailbox, long uid, long modSeq, Message<JPAId> original)
            throws MailboxException {
        Message<JPAId> copy;
        if (original instanceof JPAStreamingMessage) {
            copy = new JPAStreamingMessage((JPAMailbox) mailbox, uid, modSeq, original);
        } else if (original instanceof JPAEncryptedMessage) {
            copy = new JPAEncryptedMessage((JPAMailbox) mailbox, uid, modSeq, original);
        } else {
            copy = new JPAMessage((JPAMailbox) mailbox, uid, modSeq, original);
        }
        return save(mailbox, copy);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#save(Mailbox,
     *      Message)
     */
    protected MessageMetaData save(Mailbox<JPAId> mailbox, Message<JPAId> message) throws MailboxException {

        try {

            // We need to reload a "JPA attached" mailbox, because the provide
            // mailbox is already "JPA detached"
            // If we don't this, we will get an
            // org.apache.openjpa.persistence.ArgumentException.
            ((AbstractJPAMessage) message)
                    .setMailbox(getEntityManager().find(JPAMailbox.class, mailbox.getMailboxId().getRawId()));

            getEntityManager().persist(message);
            return new SimpleMessageMetaData(message);
        } catch (PersistenceException e) {
            throw new MailboxException("Save of message " + message + " failed in mailbox " + mailbox, e);
        } catch (ArgumentException e) {
            throw new MailboxException("Save of message " + message + " failed in mailbox " + mailbox, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findMessagesInMailboxAfterUID(Mailbox<JPAId> mailbox, long uid, int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailboxAfterUID")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("uidParam", uid);

        if (batchSize > 0)
            query.setMaxResults(batchSize);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findMessagesInMailboxWithUID(Mailbox<JPAId> mailbox, long uid) {
        return getEntityManager().createNamedQuery("findMessagesInMailboxWithUID")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("uidParam", uid).setMaxResults(1)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findMessagesInMailboxBetweenUIDs(Mailbox<JPAId> mailbox, long from, long to,
            int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailboxBetweenUIDs")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("fromParam", from)
                .setParameter("toParam", to);

        if (batchSize > 0)
            query.setMaxResults(batchSize);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findMessagesInMailbox(Mailbox<JPAId> mailbox, int batchSize) {
        Query query = getEntityManager().createNamedQuery("findMessagesInMailbox").setParameter("idParam",
                mailbox.getMailboxId().getRawId());
        if (batchSize > 0)
            query.setMaxResults(batchSize);
        return query.getResultList();
    }

    private Map<Long, MessageMetaData> createMetaData(List<Message<JPAId>> uids) {
        final Map<Long, MessageMetaData> data = new HashMap<Long, MessageMetaData>();
        for (int i = 0; i < uids.size(); i++) {
            Message<JPAId> m = uids.get(i);
            data.put(m.getUid(), new SimpleMessageMetaData(m));
        }
        return data;
    }

    private int deleteDeletedMessagesInMailbox(Mailbox<JPAId> mailbox) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailbox")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).executeUpdate();
    }

    private int deleteDeletedMessagesInMailboxAfterUID(Mailbox<JPAId> mailbox, long uid) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailboxAfterUID")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("uidParam", uid).executeUpdate();
    }

    private int deleteDeletedMessagesInMailboxWithUID(Mailbox<JPAId> mailbox, long uid) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailboxWithUID")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("uidParam", uid).executeUpdate();
    }

    private int deleteDeletedMessagesInMailboxBetweenUIDs(Mailbox<JPAId> mailbox, long from, long to) {
        return getEntityManager().createNamedQuery("deleteDeletedMessagesInMailboxBetweenUIDs")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("fromParam", from)
                .setParameter("toParam", to).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findDeletedMessagesInMailbox(Mailbox<JPAId> mailbox) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailbox")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findDeletedMessagesInMailboxAfterUID(Mailbox<JPAId> mailbox, long uid) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailboxAfterUID")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("uidParam", uid).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findDeletedMessagesInMailboxWithUID(Mailbox<JPAId> mailbox, long uid) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailboxWithUID")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("uidParam", uid).setMaxResults(1)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Message<JPAId>> findDeletedMessagesInMailboxBetweenUIDs(Mailbox<JPAId> mailbox, long from, long to) {
        return getEntityManager().createNamedQuery("findDeletedMessagesInMailboxBetweenUIDs")
                .setParameter("idParam", mailbox.getMailboxId().getRawId()).setParameter("fromParam", from)
                .setParameter("toParam", to).getResultList();
    }
}
