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

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jcr.JCRId;
import org.apache.james.mailbox.jcr.JCRImapConstants;
import org.apache.james.mailbox.jcr.MailboxSessionJCRRepository;
import org.apache.james.mailbox.jcr.mail.model.JCRMessage;
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

/**
 * JCR implementation of a {@link MessageMapper}. The implementation store each
 * message as a seperate child node under the mailbox
 * 
 */
public class JCRMessageMapper extends AbstractMessageMapper<JCRId> implements JCRImapConstants {

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
    public JCRMessageMapper(final MailboxSessionJCRRepository repository, MailboxSession mSession,
            UidProvider<JCRId> uidProvider, ModSeqProvider<JCRId> modSeqProvider, int scaleType) {
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
    public JCRMessageMapper(final MailboxSessionJCRRepository repos, MailboxSession session,
            UidProvider<JCRId> uidProvider, ModSeqProvider<JCRId> modSeqProvider) {
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
    public long countMessagesInMailbox(Mailbox<JCRId> mailbox) throws MailboxException {
        try {
            // we use order by because without it count will always be 0 in
            // jackrabbit
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message) order by @"
                    + JCRMessage.UID_PROPERTY;
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
    public long countUnseenMessagesInMailbox(Mailbox<JCRId> mailbox) throws MailboxException {

        try {
            // we use order by because without it count will always be 0 in
            // jackrabbit
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                    + JCRMessage.SEEN_PROPERTY + "='false'] order by @" + JCRMessage.UID_PROPERTY;
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
     * org.apache.james.mailbox.store.mail.model.Message)
     */
    public void delete(Mailbox<JCRId> mailbox, Message<JCRId> message) throws MailboxException {
        JCRMessage membership = (JCRMessage) message;
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
    public Iterator<Message<JCRId>> findInMailbox(Mailbox<JCRId> mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {
        try {
            List<Message<JCRId>> results;
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
    public List<Long> findRecentMessageUidsInMailbox(Mailbox<JCRId> mailbox) throws MailboxException {

        try {

            List<Long> list = new ArrayList<Long>();
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                    + JCRMessage.RECENT_PROPERTY + "='true'] order by @" + JCRMessage.UID_PROPERTY;

            QueryManager manager = getSession().getWorkspace().getQueryManager();
            Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
            QueryResult result = query.execute();

            NodeIterator iterator = result.getNodes();
            while (iterator.hasNext()) {
                list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()).getUid());
            }
            return list;

        } catch (RepositoryException e) {
            throw new MailboxException("Unable to search recent messages in mailbox " + mailbox, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.MessageMapper#findFirstUnseenMessageUid
     * (org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public Long findFirstUnseenMessageUid(Mailbox<JCRId> mailbox) throws MailboxException {
        try {
            String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                    + JCRMessage.SEEN_PROPERTY + "='false'] order by @" + JCRMessage.UID_PROPERTY;

            QueryManager manager = getSession().getWorkspace().getQueryManager();

            Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
            query.setLimit(1);
            QueryResult result = query.execute();

            NodeIterator iterator = result.getNodes();
            if (iterator.hasNext()) {
                return new JCRMessage(iterator.nextNode(), mailboxSession.getLog()).getUid();
            } else {
                return null;
            }
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to find first unseen message in mailbox " + mailbox, e);
        }
    }

    @Override
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox<JCRId> mailbox, MessageRange set)
            throws MailboxException {
        try {
            final List<Message<JCRId>> results;
            final long from = set.getUidFrom();
            final long to = set.getUidTo();
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
            Map<Long, MessageMetaData> uids = new HashMap<Long, MessageMetaData>();
            for (int i = 0; i < results.size(); i++) {
                Message<JCRId> m = results.get(i);
                long uid = m.getUid();
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
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData move(Mailbox<JCRId> mailbox, Message<JCRId> original) throws MailboxException {
        throw new UnsupportedOperationException("Not implemented - see https://issues.apache.org/jira/browse/IMAP-370");
    }

    @Override
    protected MessageMetaData copy(Mailbox<JCRId> mailbox, long uid, long modSeq, Message<JCRId> original)
            throws MailboxException {
        try {
            String newMessagePath = getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize()).getPath() + NODE_DELIMITER
                    + String.valueOf(uid);
            getSession().getWorkspace().copy(
                    ((JCRMessage) original).getNode().getPath(),
                    getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize()).getPath() + NODE_DELIMITER
                            + String.valueOf(uid));
            Node node = getSession().getNode(newMessagePath);
            node.setProperty(JCRMessage.MAILBOX_UUID_PROPERTY, mailbox.getMailboxId().serialize());
            node.setProperty(JCRMessage.UID_PROPERTY, uid);
            node.setProperty(JCRMessage.MODSEQ_PROPERTY, modSeq);
            // A copy of a message is recent
            // See MAILBOX-85
            node.setProperty(JCRMessage.RECENT_PROPERTY, true);
            return new SimpleMessageMetaData(new JCRMessage(node, mailboxSession.getLog()));
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to copy message " + original + " in mailbox " + mailbox, e);
        }
    }

    @Override
    protected MessageMetaData save(Mailbox<JCRId> mailbox, Message<JCRId> message) throws MailboxException {
        final JCRMessage membership = (JCRMessage) message;
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
    
                long uid = membership.getUid();
                messageNode = mailboxNode.addNode(String.valueOf(uid), "nt:file");
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
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to save message " + message + " in mailbox " + mailbox, e);
        } catch (IOException e) {
            throw new MailboxException("Unable to save message " + message + " in mailbox " + mailbox, e);
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
    private String getMailboxPath(Mailbox<JCRId> mailbox) throws ItemNotFoundException, RepositoryException {
        return ISO9075.encodePath(getSession().getNodeByIdentifier(mailbox.getMailboxId().serialize()).getPath());
    }

    private List<Message<JCRId>> findMessagesInMailboxAfterUID(Mailbox<JCRId> mailbox, long uid, int batchSize)
            throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.UID_PROPERTY + ">=" + uid + "] order by @" + JCRMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        if (batchSize > 0)
            query.setLimit(batchSize);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()));
        }
        return list;
    }

    private List<Message<JCRId>> findMessageInMailboxWithUID(Mailbox<JCRId> mailbox, long uid)
            throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.UID_PROPERTY + "=" + uid + "]";

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        query.setLimit(1);
        QueryResult result = query.execute();
        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()));
        }
        return list;
    }

    private List<Message<JCRId>> findMessagesInMailboxBetweenUIDs(Mailbox<JCRId> mailbox, long from, long to,
            int batchSize) throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.UID_PROPERTY + ">=" + from + " and @" + JCRMessage.UID_PROPERTY + "<=" + to
                + "] order by @" + JCRMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        if (batchSize > 0)
            query.setLimit(batchSize);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()));
        }
        return list;
    }

    private List<Message<JCRId>> findMessagesInMailbox(Mailbox<JCRId> mailbox, int batchSize)
            throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();

        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message) order by @"
                + JCRMessage.UID_PROPERTY;
        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        if (batchSize > 0)
            query.setLimit(batchSize);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()));
        }
        return list;
    }

    private List<Message<JCRId>> findDeletedMessagesInMailboxAfterUID(Mailbox<JCRId> mailbox, long uid)
            throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.UID_PROPERTY + ">=" + uid + " and @" + JCRMessage.DELETED_PROPERTY + "='true'] order by @"
                + JCRMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()));
        }
        return list;
    }

    private List<Message<JCRId>> findDeletedMessageInMailboxWithUID(Mailbox<JCRId> mailbox, long uid)
            throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.UID_PROPERTY + "=" + uid + " and @" + JCRMessage.DELETED_PROPERTY + "='true']";
        QueryManager manager = getSession().getWorkspace().getQueryManager();
        Query query = manager.createQuery(queryString, XPATH_LANGUAGE);
        query.setLimit(1);
        QueryResult result = query.execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            JCRMessage member = new JCRMessage(iterator.nextNode(), mailboxSession.getLog());
            list.add(member);
        }
        return list;
    }

    private List<Message<JCRId>> findDeletedMessagesInMailboxBetweenUIDs(Mailbox<JCRId> mailbox, long from, long to)
            throws RepositoryException {
        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.UID_PROPERTY + ">=" + from + " and @" + JCRMessage.UID_PROPERTY + "<=" + to + " and @"
                + JCRMessage.DELETED_PROPERTY + "='true'] order by @" + JCRMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            list.add(new JCRMessage(iterator.nextNode(), mailboxSession.getLog()));
        }
        return list;
    }

    private List<Message<JCRId>> findDeletedMessagesInMailbox(Mailbox<JCRId> mailbox) throws RepositoryException {

        List<Message<JCRId>> list = new ArrayList<Message<JCRId>>();
        String queryString = "/jcr:root" + getMailboxPath(mailbox) + "//element(*,jamesMailbox:message)[@"
                + JCRMessage.DELETED_PROPERTY + "='true'] order by @" + JCRMessage.UID_PROPERTY;

        QueryManager manager = getSession().getWorkspace().getQueryManager();
        QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();

        NodeIterator iterator = result.getNodes();
        while (iterator.hasNext()) {
            JCRMessage member = new JCRMessage(iterator.nextNode(), mailboxSession.getLog());
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
