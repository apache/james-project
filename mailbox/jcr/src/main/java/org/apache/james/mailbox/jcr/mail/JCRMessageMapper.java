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
package org.apache.james.mailbox.jcr.mail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.mail.Flags;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jcr.JCRImapConstants;
import org.apache.james.mailbox.jcr.MailboxSessionJCRRepository;
import org.apache.james.mailbox.jcr.mail.model.JCRMailboxMessage;
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
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JCR implementation of a {@link MessageMapper}. The implementation store each
 * message as a seperate child node under the mailbox
 * 
 */
public class JCRMessageMapper extends AbstractMessageMapper implements JCRImapConstants {

    private static final Logger LOGGER = LoggerFactory.getLogger(JCRMessageMapper.class);

    @SuppressWarnings("deprecation")
    private static final String XPATH_LANGUAGE = Query.XPATH;

    /**
     * Store the messages directly in the mailbox: .../mailbox/
     */
    public final static int MESSAGE_SCALE_NONE = 0;

    /**
     * Store the messages under a year directory in the mailbox:
     * .../mailbox/2010/
     */
    public final static int MESSAGE_SCALE_YEAR = 1;

    /**
     * Store the messages under a year/month directory in the mailbox:
     * .../mailbox/2010/05/
     */
    public final static int MESSAGE_SCALE_MONTH = 2;

    /**
     * Store the messages under a year/month/day directory in the mailbox:
     * .../mailbox/2010/05/01/
     */
    public final static int MESSAGE_SCALE_DAY = 3;

    /**
     * Store the messages under a year/month/day/hour directory in the mailbox:
     * .../mailbox/2010/05/02/11
     */
    public final static int MESSAGE_SCALE_HOUR = 4;

    /**
     * Store the messages under a year/month/day/hour/min directory in the
     * mailbox: .../mailbox/2010/05/02/11/59
     */
    public final static int MESSAGE_SCALE_MINUTE = 5;

    private final int scaleType;

    private final MailboxSessionJCRRepository repository;

    /**
     * Construct a new {@link JCRMessageMapper} instance
     * 
     * @param repository
     *            {@link MailboxSessionJCRRepository} to use
     * @param mSession
     *            {@link MailboxSession} to which the mapper is bound
     * @param uidProvider
     *            {@link UidProvider} to use
     * @param modSeqProvider
     *            {@link ModSeqProvider} to use
     * @param scaleType
     *            message scale type either {@link #MESSAGE_SCALE_DAY},
     *            {@link #MESSAGE_SCALE_HOUR}, {@link #MESSAGE_SCALE_MINUTE},
     *            {@link #MESSAGE_SCALE_MONTH}, {@link #MESSAGE_SCALE_NONE} or
     *            {@link #MESSAGE_SCALE_YEAR}
     */
    public JCRMessageMapper(MailboxSessionJCRRepository repository, MailboxSession mSession,
            UidProvider uidProvider, ModSeqProvider modSeqProvider, int scaleType) {
        super(mSession, uidProvider, modSeqProvider);
        this.repository = repository;
        this.scaleType = scaleType;
    }

    /**
     * Construct a new {@link JCRMessageMapper} instance using
     * {@link #MESSAGE_SCALE_DAY} as default
     * 
     * @param repos
     *            {@link MailboxSessionJCRRepository} to use
     * @param session
     *            {@link MailboxSession} to which the mapper is bound
     * @param uidProvider
     *            {@link UidProvider} to use
     * @param modSeqProvider
     *            {@link ModSeqProvider} to use
     */
    public JCRMessageMapper(MailboxSessionJCRRepository repos, MailboxSession session,
            UidProvider uidProvider, ModSeqProvider modSeqProvider) {
        this(repos, session, uidProvider, modSeqProvider, MESSAGE_SCALE_DAY);
    }

    /**
     * Return the JCR Session
     * 
     * @return session
     */
    protected Session getSession() throws RepositoryException {
        return repository.login(mailboxSession);
    }

    /**
     * Begin is not supported by level 1 JCR implementations, however we refresh
     * the session
     */
    protected void begin() throws MailboxException {
        try {
            getSession().refresh(true);
        } catch (RepositoryException e) {
            // do nothin on refresh
        }
        // Do nothing
    }

    /**
     * Just call save on the underlying JCR Session, because level 1 JCR
     * implementation does not offer Transactions
     */
    protected void commit() throws MailboxException {
        try {
            if (getSession().hasPendingChanges()) {
                getSession().save();
            }
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to commit", e);
        }
    }

    /**
     * Rollback is not supported by level 1 JCR implementations, so just do
     * nothing
     */
    protected void rollback() throws MailboxException {
        try {
            // just refresh session and discard all pending changes
            getSession().refresh(false);
        } catch (RepositoryException e) {
            // just catch on rollback by now
        }
    }

    /**
     * Logout from open JCR Session
     */
    public void endRequest() {
        repository.logout(mailboxSession);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.MessageMapper#countMessagesInMailbox
     * ()
     */
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        try {
            // we use order by because without it count will always be 0 in
            // jackrabbit
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message) order by @"
                    + JCRMailboxMessage.UID_PROPERTY;
            QueryManager manager = getSession().getWorkspace().getQueryManager();
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            NodeIterator nodes = result.getNodes();
            long count = nodes.getSize();
            if (count == -1) {
                count = 0;
                while (nodes.hasNext()) {
                    nodes.nextNode();
                    count++;
                }
            }
            return count;
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to count messages in mailbox " + mailbox, e);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.MessageMapper#
     * countUnseenMessagesInMailbox ()
     */
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {

        try {
            // we use order by because without it count will always be 0 in
            // jackrabbit
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                    + JCRMailboxMessage.SEEN_PROPERTY + "='false'] order by @" + JCRMailboxMessage.UID_PROPERTY;
            QueryManager manager = getSession().getWorkspace().getQueryManager();
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            NodeIterator nodes = result.getNodes();
            long count = nodes.getSize();

            if (count == -1) {
                count = 0;
                while (nodes.hasNext()) {
                    nodes.nextNode();

                    count++;
                }
            }
            return count;
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to count unseen messages in mailbox " + mailbox, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.MessageMapper#delete(org.apache.james
     * .mailbox.store.mail.model.Mailbox,
     * org.apache.james.mailbox.store.mail.model.MailboxMessage)
     */
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        JCRMailboxMessage membership = (JCRMailboxMessage) message;
        if (membership.isPersistent()) {
            try {

                getSession().getNodeByIdentifier(membership.getId()).remove();
            } catch (RepositoryException e) {
                throw new MailboxException("Unable to delete message " + message + " in mailbox " + mailbox, e);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.MessageMapper#findInMailbox(org.apache
     * .james.mailbox.store.mail.model.Mailbox,
     * org.apache.james.mailbox.MessageRange,
     * org.apache.james.mailbox.store.mail.MessageMapper.FetchType, int)
     */
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {
        try {
            List<MailboxMessage> results;
            MessageUid from = set.getUidFrom();
            final MessageUid to = set.getUidTo();
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
                results = findMessageInMailboxWithUID(mailbox, from);
                break;
            case RANGE:
                results = findMessagesInMailboxBetweenUIDs(mailbox, from, to, max);
                break;
            }
            return results.iterator();
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to search MessageRange " + set + " in mailbox " + mailbox, e);
        }
    }

    /*
     * 
     * TODO: Maybe we should better use an ItemVisitor and just traverse through
     * the child nodes. This could be a way faster
     * 
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.MessageMapper#
     * findRecentMessageUidsInMailbox ()
     */
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {

        try {

            List<MessageUid> list = new ArrayList<>();
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                    + JCRMailboxMessage.RECENT_PROPERTY + "='true'] order by @" + JCRMailboxMessage.UID_PROPERTY;

            QueryManager manager = getSession().getWorkspace().getQueryManager();
            Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
            QueryResult result = query.execute();

            NodeIterator iterator = result.getNodes();
            while (iterator.hasNext()) {
                list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER).getUid());
            }
            return list;

        } catch (RepositoryException e) {
            throw new MailboxException("Unable to search recent messages in mailbox " + mailbox, e);
        }
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        try {
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                    + JCRMailboxMessage.SEEN_PROPERTY + "='false'] order by @" + JCRMailboxMessage.UID_PROPERTY;

            QueryManager manager = getSession().getWorkspace().getQueryManager();

            Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
            query.setLimit(1);
            QueryResult result = query.execute();

            NodeIterator iterator = result.getNodes();
            if (iterator.hasNext()) {
                return new JCRMailboxMessage(iterator.nextNode(), LOGGER).getUid();
            } else {
                return null;
            }
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to find first unseen message in mailbox " + mailbox, e);
        }
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set)
            throws MailboxException {
        try {
            final List<MailboxMessage> results;
            final MessageUid from = set.getUidFrom();
            final MessageUid to = set.getUidTo();
            final Type type = set.getType();
            switch (type) {
            default:
            case ALL:
                results = findDeletedMessagesInMailbox(mailbox);
                break;
            case FROM:
                results = findDeletedMessagesInMailboxAfterUID(mailbox, from);
                break;
            case ONE:
                results = findDeletedMessageInMailboxWithUID(mailbox, from);
                break;
            case RANGE:
                results = findDeletedMessagesInMailboxBetweenUIDs(mailbox, from, to);
                break;
            }
            Map<MessageUid, MessageMetaData> uids = new HashMap<>();
            for (MailboxMessage m : results) {
                MessageUid uid = m.getUid();
                uids.put(uid, new SimpleMessageMetaData(m));
                delete(mailbox, m);
            }
            return uids;
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to search MessageRange " + set + " in mailbox " + mailbox, e);
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
        try {
            String newMessagePath = getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize()).getPath() + NODE_DELIMITER
                    + String.valueOf(uid.asLong());
            getSession().getWorkspace().copy(
                    ((JCRMailboxMessage) original).getNode().getPath(),
                    getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize()).getPath() + NODE_DELIMITER
                            + String.valueOf(uid.asLong()));
            Node node = getSession().getNode(newMessagePath);
            node.setProperty(JCRMailboxMessage.MAILBOX_UUID_PROPERTY, mailbox.getMailboxId().serialize());
            node.setProperty(JCRMailboxMessage.UID_PROPERTY, uid.asLong());
            node.setProperty(JCRMailboxMessage.MODSEQ_PROPERTY, modSeq);
            // A copy of a message is recent
            // See MAILBOX-85
            node.setProperty(JCRMailboxMessage.RECENT_PROPERTY, true);
            return new SimpleMessageMetaData(new JCRMailboxMessage(node, LOGGER));
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to copy message " + original + " in mailbox " + mailbox, e);
        }
    }

    @Override
    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        final JCRMailboxMessage membership = (JCRMailboxMessage) message;
        try {
    
            Node messageNode = null;
    
            if (membership.isPersistent()) {
                messageNode = getSession().getNodeByIdentifier(membership.getId());
            }
    
            if (messageNode == null) {
    
                Date date = message.getInternalDate();
                if (date == null) {
                    date = new Date();
                }
    
                // extracte the date from the message to create node structure
                // later
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                final String year = convertIntToString(cal.get(Calendar.YEAR));
                final String month = convertIntToString(cal.get(Calendar.MONTH) + 1);
                final String day = convertIntToString(cal.get(Calendar.DAY_OF_MONTH));
                final String hour = convertIntToString(cal.get(Calendar.HOUR_OF_DAY));
                final String min = convertIntToString(cal.get(Calendar.MINUTE));
    
                Node mailboxNode = getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize());
                Node node = mailboxNode;
    
                if (scaleType > MESSAGE_SCALE_NONE) {
                    // we lock the whole mailbox with all its childs while
                    // adding the folder structure for the date
    
                    if (scaleType >= MESSAGE_SCALE_YEAR) {
                        node = JcrUtils.getOrAddFolder(node, year);
    
                        if (scaleType >= MESSAGE_SCALE_MONTH) {
                            node = JcrUtils.getOrAddFolder(node, month);
    
                            if (scaleType >= MESSAGE_SCALE_DAY) {
                                node = JcrUtils.getOrAddFolder(node, day);
    
                                if (scaleType >= MESSAGE_SCALE_HOUR) {
                                    node = JcrUtils.getOrAddFolder(node, hour);
    
                                    if (scaleType >= MESSAGE_SCALE_MINUTE) {
                                        node = JcrUtils.getOrAddFolder(node, min);
                                    }
                                }
                            }
                        }
                    }
    
                }
    
                MessageUid uid = membership.getUid();
                messageNode = mailboxNode.addNode(String.valueOf(uid.asLong()), "nt:file");
                messageNode.addMixin("jamesMailbox:message");
                try {
                    membership.merge(messageNode);
    
                } catch (IOException e) {
                    throw new RepositoryException("Unable to merge message in to tree", e);
                }
            } else {
                membership.merge(messageNode);
            }
            return new SimpleMessageMetaData(membership);
        } catch (RepositoryException | IOException e) {
            throw new MailboxException("Unable to save message " + message + " in mailbox " + mailbox, e);
        }
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        int maxBatchSize = -1;
        try {
            return new ApplicableFlagCalculator(findMessagesInMailbox(mailbox, maxBatchSize))
                .computeApplicableFlags();
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to get message from in mailbox " + mailbox, e);
        }
    }

    /**
     * Return the path to the mailbox. This path is escaped to be able to use it
     * in xpath queries
     * 
     * See http://wiki.apache.org/jackrabbit/EncodingAndEscaping
     * 
     * @param mailbox
     * @return
     * @throws ItemNotFoundException
     * @throws RepositoryException
     */
    private String getMailboxPath(Mailbox mailbox) throws ItemNotFoundException, RepositoryException {
        return ISO9075.encodePath(getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize()).getPath());
    }

    private List<MailboxMessage> findMessagesInMailboxAfterUID(Mailbox mailbox, MessageUid from, int batchSize)
            throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.UID_PROPERTY + ">=" + from + "] order by @" + JCRMailboxMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        if (batchSize > 0)
            query.setLimit(batchSize);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER));
        }
        return list;
    }

    private List<MailboxMessage> findMessageInMailboxWithUID(Mailbox mailbox, MessageUid from)
            throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.UID_PROPERTY + "=" + from + "]";

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        query.setLimit(1);
        QueryResult result = query.execute();
        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER));
        }
        return list;
    }

    private List<MailboxMessage> findMessagesInMailboxBetweenUIDs(Mailbox mailbox, MessageUid from, MessageUid to,
                                                                         int batchSize) throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.UID_PROPERTY + ">=" + from + " and @" + JCRMailboxMessage.UID_PROPERTY + "<=" + to
                + "] order by @" + JCRMailboxMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        if (batchSize > 0)
            query.setLimit(batchSize);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER));
        }
        return list;
    }

    private List<MailboxMessage> findMessagesInMailbox(Mailbox mailbox, int batchSize)
            throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();

        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message) order by @"
                + JCRMailboxMessage.UID_PROPERTY;
        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        if (batchSize > 0)
            query.setLimit(batchSize);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER));
        }
        return list;
    }

    private List<MailboxMessage> findDeletedMessagesInMailboxAfterUID(Mailbox mailbox, MessageUid from)
            throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.UID_PROPERTY + ">=" + from + " and @" + JCRMailboxMessage.DELETED_PROPERTY + "='true'] order by @"
                + JCRMailboxMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER));
        }
        return list;
    }

    private List<MailboxMessage> findDeletedMessageInMailboxWithUID(Mailbox mailbox, MessageUid from)
            throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.UID_PROPERTY + "=" + from + " and @" + JCRMailboxMessage.DELETED_PROPERTY + "='true']";
        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        query.setLimit(1);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            JCRMailboxMessage member = new JCRMailboxMessage(iterator.nextNode(), LOGGER);
            list.add(member);
        }
        return list;
    }

    private List<MailboxMessage> findDeletedMessagesInMailboxBetweenUIDs(Mailbox mailbox, MessageUid from, MessageUid to)
            throws RepositoryException {
        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.UID_PROPERTY + ">=" + from + " and @" + JCRMailboxMessage.UID_PROPERTY + "<=" + to + " and @"
                + JCRMailboxMessage.DELETED_PROPERTY + "='true'] order by @" + JCRMailboxMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMailboxMessage(iterator.nextNode(), LOGGER));
        }
        return list;
    }

    private List<MailboxMessage> findDeletedMessagesInMailbox(Mailbox mailbox) throws RepositoryException {

        List<MailboxMessage> list = new ArrayList<>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMailboxMessage.DELETED_PROPERTY + "='true'] order by @" + JCRMailboxMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            JCRMailboxMessage member = new JCRMailboxMessage(iterator.nextNode(), LOGGER);
            list.add(member);
        }
        return list;
    }

    /**
     * Convert the given int value to a String. If the int value is smaller then
     * 9 it will prefix the String with 0.
     * 
     * @param value
     * @return stringValue
     */
    private String convertIntToString(int value) {
        if (value <= 9) {
            return "0" + String.valueOf(value);
        } else {
            return String.valueOf(value);
        }
    }

}
