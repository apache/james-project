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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.SessionType;
import org.apache.james.mailbox.MailboxSessionIdGenerator;
import org.apache.james.mailbox.RequestAware;
import org.apache.james.mailbox.StandardMailboxMetaDataComparator;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxMetaData.Selectability;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mailbox.store.quota.QuotaUpdater;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.transaction.TransactionalMapper;
import org.slf4j.Logger;

/**
 * This base class of an {@link MailboxManager} implementation provides a high-level api for writing your own
 * {@link MailboxManager} implementation. If you plan to write your own {@link MailboxManager} its most times so easiest
 * to extend just this class or use it directly.
 * <p/>
 * If you need a more low-level api just implement {@link MailboxManager} directly
 *
 * @param <Id>
 */
public class StoreMailboxManager<Id extends MailboxId> implements MailboxManager {

    public static final char SQL_WILDCARD_CHAR = '%';
    public static final int DEFAULT_FETCH_BATCH_SIZE = 200;

    private MailboxEventDispatcher<Id> dispatcher;
    private AbstractDelegatingMailboxListener delegatingListener = null;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;

    private final Authenticator authenticator;

    private final MailboxACLResolver aclResolver;

    private final GroupMembershipResolver groupMembershipResolver;

    private final static Random RANDOM = new Random();

    private int copyBatchSize = 0;

    private int moveBatchSize = 0;

    private MailboxPathLocker locker;

    private MessageSearchIndex<Id> index;

    private MailboxSessionIdGenerator idGenerator;

    private QuotaManager quotaManager;

    private QuotaRootResolver quotaRootResolver;

    private QuotaUpdater quotaUpdater;

    private int fetchBatchSize = DEFAULT_FETCH_BATCH_SIZE;


    public StoreMailboxManager(MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory, final Authenticator authenticator, final MailboxPathLocker locker, final MailboxACLResolver aclResolver, final GroupMembershipResolver groupMembershipResolver) {
        this.authenticator = authenticator;
        this.locker = locker;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.aclResolver = aclResolver;
        this.groupMembershipResolver = groupMembershipResolver;
    }

    public StoreMailboxManager(MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory, final Authenticator authenticator, final MailboxACLResolver aclResolver, final GroupMembershipResolver groupMembershipResolver) {
        this(mailboxSessionMapperFactory, authenticator, new JVMMailboxPathLocker(), aclResolver, groupMembershipResolver);
    }

    public void setMailboxSessionIdGenerator(MailboxSessionIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public void setQuotaManager(QuotaManager quotaManager) {
        this.quotaManager = quotaManager;
    }

    public void setQuotaRootResolver(QuotaRootResolver quotaRootResolver) {
        this.quotaRootResolver = quotaRootResolver;
    }

    public void setQuotaUpdater(QuotaUpdater quotaUpdater) {
        this.quotaUpdater = quotaUpdater;
    }

    public void setCopyBatchSize(int copyBatchSize) {
        this.copyBatchSize = copyBatchSize;
    }

    public void setMoveBatchSize(int moveBatchSize) {
        this.moveBatchSize = moveBatchSize;
    }

    public void setFetchBatchSize(int fetchBatchSize) {
        this.fetchBatchSize = fetchBatchSize;
    }


    /**
     * Init the {@link MailboxManager}
     *
     * @throws MailboxException
     */
    @SuppressWarnings("rawtypes")
    public void init() throws MailboxException {
        // The dispatcher need to have the delegating listener added
        dispatcher = new MailboxEventDispatcher<Id>(getDelegationListener());

        if (index == null) {
            index = new SimpleMessageSearchIndex<Id>(mailboxSessionMapperFactory);
        }
        if (index instanceof ListeningMessageSearchIndex) {
            this.addGlobalListener((ListeningMessageSearchIndex) index, null);
        }

        if (idGenerator == null) {
            idGenerator = new RandomMailboxSessionIdGenerator();
        }
        if (quotaManager == null) {
            quotaManager = new NoQuotaManager();
        }
        if (quotaRootResolver == null) {
            quotaRootResolver = new DefaultQuotaRootResolver(mailboxSessionMapperFactory);
        }
        if (quotaUpdater != null && quotaUpdater instanceof MailboxListener) {
            this.addGlobalListener((MailboxListener) quotaUpdater, null);
        }
    }

    /**
     * Return the {@link AbstractDelegatingMailboxListener} which is used by this {@link MailboxManager}
     *
     * @return delegatingListener
     */
    public AbstractDelegatingMailboxListener getDelegationListener() {
        if (delegatingListener == null) {
            delegatingListener = new HashMapDelegatingMailboxListener();
        }
        return delegatingListener;
    }


    /**
     * Return the {@link MessageSearchIndex} used by this {@link MailboxManager}
     *
     * @return index
     */
    public MessageSearchIndex<Id> getMessageSearchIndex() {
        return index;
    }

    public QuotaRootResolver getQuotaRootResolver() {
        return quotaRootResolver;
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    /**
     * Return the {@link MailboxEventDispatcher} used by thei {@link MailboxManager}
     *
     * @return dispatcher
     */
    public MailboxEventDispatcher<Id> getEventDispatcher() {
        return dispatcher;
    }

    /**
     * Return the {@link MailboxSessionMapperFactory} used by this {@link MailboxManager}
     *
     * @return mailboxSessionMapperFactory
     */
    public MailboxSessionMapperFactory<Id> getMapperFactory() {
        return mailboxSessionMapperFactory;
    }

    public MailboxPathLocker getLocker() {
        return locker;
    }

    public MailboxACLResolver getAclResolver() {
        return aclResolver;
    }

    public GroupMembershipResolver getGroupMembershipResolver() {
        return groupMembershipResolver;
    }

    /**
     * Set the {@link AbstractDelegatingMailboxListener} to use with this {@link MailboxManager} instance. If none is set here a {@link HashMapDelegatingMailboxListener} instance will
     * be created lazy
     *
     * @param delegatingListener
     */
    public void setDelegatingMailboxListener(AbstractDelegatingMailboxListener delegatingListener) {
        this.delegatingListener = delegatingListener;
        dispatcher = new MailboxEventDispatcher<Id>(getDelegationListener());
    }

    /**
     * Set the {@link MessageSearchIndex} which should be used by this {@link MailboxManager}. If none is given this implementation will use a {@link SimpleMessageSearchIndex}
     * by default
     *
     * @param index
     */
    public void setMessageSearchIndex(MessageSearchIndex<Id> index) {
        this.index = index;
    }

    /**
     * Generate an return the next uid validity
     *
     * @return uidValidity
     */
    protected int randomUidValidity() {
        return Math.abs(RANDOM.nextInt());
    }

    @Override
    public MailboxSession createSystemSession(String userName, Logger log) {
        return createSession(userName, null, log, SessionType.System);
    }

    /**
     * Create Session
     *
     * @param userName
     * @param log
     * @return session
     */
    protected MailboxSession createSession(String userName, String password, Logger log, SessionType type) {
        return new SimpleMailboxSession(randomId(), userName, password, log, new ArrayList<Locale>(), getDelimiter(), type);
    }

    /**
     * Generate and return the next id to use
     *
     * @return id
     */
    protected long randomId() {
        return idGenerator.nextId();
    }

    @Override
    public char getDelimiter() {
        return MailboxConstants.DEFAULT_DELIMITER;
    }

    /**
     * Log in the user with the given userid and password
     *
     * @param userid the username
     * @param passwd the password
     * @return success true if login success false otherwise
     */
    private boolean login(String userid, String passwd) {
        return authenticator.isAuthentic(userid, passwd);
    }

    @Override
    public MailboxSession login(String userid, String passwd, Logger log) throws BadCredentialsException, MailboxException {
        if (login(userid, passwd)) {
            return createSession(userid, passwd, log, SessionType.User);
        } else {
            throw new BadCredentialsException();
        }
    }

    /**
     * Close the {@link MailboxSession} if not null
     */
    public void logout(MailboxSession session, boolean force) throws MailboxException {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Create a {@link MailboxManager} for the given Mailbox. By default this will return a {@link StoreMessageManager}. If
     * your implementation needs something different, just override this method
     *
     * @param mailbox
     * @param session
     * @return storeMailbox
     */
    protected StoreMessageManager<Id> createMessageManager(Mailbox<Id> mailbox, MailboxSession session) throws MailboxException {
        return new StoreMessageManager<Id>(getMapperFactory(), getMessageSearchIndex(), getEventDispatcher(), getLocker(), mailbox, getAclResolver(), getGroupMembershipResolver(), getQuotaManager(), getQuotaRootResolver());
    }

    /**
     * Create a Mailbox for the given mailbox path. This will by default return a {@link SimpleMailbox}.
     * <p/>
     * If you need to return something more special just override this method
     *
     * @param mailboxPath
     * @param session
     * @throws MailboxException
     */
    protected org.apache.james.mailbox.store.mail.model.Mailbox<Id> doCreateMailbox(MailboxPath mailboxPath, final MailboxSession session) throws MailboxException {
        return new SimpleMailbox<Id>(mailboxPath, randomUidValidity());
    }

    @Override
    public org.apache.james.mailbox.MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session)
            throws MailboxException {
        final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox<Id> mailboxRow = mapper.findMailboxByPath(mailboxPath);

        if (mailboxRow == null) {
            session.getLog().info("Mailbox '" + mailboxPath + "' not found.");
            throw new MailboxNotFoundException(mailboxPath);

        } else {
            session.getLog().debug("Loaded mailbox " + mailboxPath);

            StoreMessageManager<Id> m = createMessageManager(mailboxRow, session);
            m.setFetchBatchSize(fetchBatchSize);
            return m;
        }
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath, final MailboxSession mailboxSession)
            throws MailboxException {
        mailboxSession.getLog().debug("createMailbox " + mailboxPath);
        final int length = mailboxPath.getName().length();
        if (length == 0) {
            mailboxSession.getLog().warn("Ignoring mailbox with empty name");
        } else {
            if (mailboxPath.getName().charAt(length - 1) == getDelimiter())
                mailboxPath.setName(mailboxPath.getName().substring(0, length - 1));
            if (mailboxExists(mailboxPath, mailboxSession))
                throw new MailboxExistsException(mailboxPath.toString());
            // Create parents first
            // If any creation fails then the mailbox will not be created
            // TODO: transaction
            for (final MailboxPath mailbox : mailboxPath.getHierarchyLevels(getDelimiter()))

                locker.executeWithLock(mailboxSession, mailbox, new LockAwareExecution<Void>() {

                    public Void execute() throws MailboxException {
                        if (!mailboxExists(mailbox, mailboxSession)) {
                            final org.apache.james.mailbox.store.mail.model.Mailbox<Id> m = doCreateMailbox(mailbox, mailboxSession);
                            final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
                            mapper.execute(new TransactionalMapper.VoidTransaction() {

                                public void runVoid() throws MailboxException {
                                    mapper.save(m);
                                }

                            });

                            // notify listeners
                            dispatcher.mailboxAdded(mailboxSession, m);
                        }
                        return null;

                    }
                }, true);

        }
    }

    @Override
    public void deleteMailbox(final MailboxPath mailboxPath, final MailboxSession session) throws MailboxException {
        session.getLog().info("deleteMailbox " + mailboxPath);
        final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        Mailbox<Id> mailbox = mapper.execute(new Mapper.Transaction<Mailbox<Id>>() {

            public Mailbox<Id> run() throws MailboxException {
                final Mailbox<Id> mailbox = mapper.findMailboxByPath(mailboxPath);
                if (mailbox == null) {
                    throw new MailboxNotFoundException("Mailbox not found");
                }

                // We need to create a copy of the mailbox as maybe we can not refer to the real
                // mailbox once we remove it 
                SimpleMailbox<Id> m = new SimpleMailbox<Id>(mailbox);
                mapper.delete(mailbox);
                return m;
            }

        });

        dispatcher.mailboxDeleted(session, mailbox);

    }

    @Override
    public void renameMailbox(final MailboxPath from, final MailboxPath to, final MailboxSession session) throws MailboxException {
        final Logger log = session.getLog();
        if (log.isDebugEnabled())
            log.debug("renameMailbox " + from + " to " + to);
        if (mailboxExists(to, session)) {
            throw new MailboxExistsException(to.toString());
        }

        final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        mapper.execute(new Mapper.VoidTransaction() {

            public void runVoid() throws MailboxException {
                // TODO put this into a serilizable transaction
                final Mailbox<Id> mailbox = mapper.findMailboxByPath(from);
                if (mailbox == null) {
                    throw new MailboxNotFoundException(from);
                }
                mailbox.setNamespace(to.getNamespace());
                mailbox.setUser(to.getUser());
                mailbox.setName(to.getName());
                mapper.save(mailbox);

                dispatcher.mailboxRenamed(session, from, mailbox);

                // rename submailboxes
                final MailboxPath children = new MailboxPath(MailboxConstants.USER_NAMESPACE, from.getUser(), from.getName() + getDelimiter() + "%");
                locker.executeWithLock(session, children, new LockAwareExecution<Void>() {

                    public Void execute() throws MailboxException {
                        final List<Mailbox<Id>> subMailboxes = mapper.findMailboxWithPathLike(children);
                        for (Mailbox<Id> sub : subMailboxes) {
                            final String subOriginalName = sub.getName();
                            final String subNewName = to.getName() + subOriginalName.substring(from.getName().length());
                            final MailboxPath fromPath = new MailboxPath(children, subOriginalName);
                            sub.setName(subNewName);
                            mapper.save(sub);
                            dispatcher.mailboxRenamed(session, fromPath, sub);

                            if (log.isDebugEnabled())
                                log.debug("Rename mailbox sub-mailbox " + subOriginalName + " to " + subNewName);
                        }
                        return null;

                    }
                }, true);
            }
        });
    }


    @Override
    @SuppressWarnings("unchecked")
    public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        StoreMessageManager<Id> toMailbox = (StoreMessageManager<Id>) getMailbox(to, session);
        StoreMessageManager<Id> fromMailbox = (StoreMessageManager<Id>) getMailbox(from, session);

        if (copyBatchSize > 0) {
            List<MessageRange> copiedRanges = new ArrayList<MessageRange>();
            Iterator<MessageRange> ranges = set.split(copyBatchSize).iterator();
            while (ranges.hasNext()) {
                copiedRanges.addAll(fromMailbox.copyTo(ranges.next(), toMailbox, session));
            }
            return copiedRanges;
        } else {
            return fromMailbox.copyTo(set, toMailbox, session);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        StoreMessageManager<Id> toMailbox = (StoreMessageManager<Id>) getMailbox(to, session);
        StoreMessageManager<Id> fromMailbox = (StoreMessageManager<Id>) getMailbox(from, session);

        if (moveBatchSize > 0) {
            List<MessageRange> movedRanges = new ArrayList<MessageRange>();
            Iterator<MessageRange> ranges = set.split(moveBatchSize).iterator();
            while (ranges.hasNext()) {
                movedRanges.addAll(fromMailbox.moveTo(ranges.next(), toMailbox, session));
            }
            return movedRanges;
        } else {
            return fromMailbox.moveTo(set, toMailbox, session);
        }
    }

    @Override
    public List<MailboxMetaData> search(final MailboxQuery mailboxExpression, MailboxSession session)
            throws MailboxException {
        final char localWildcard = mailboxExpression.getLocalWildcard();
        final char freeWildcard = mailboxExpression.getFreeWildcard();
        final String baseName = mailboxExpression.getBase().getName();
        final int baseLength;
        if (baseName == null) {
            baseLength = 0;
        } else {
            baseLength = baseName.length();
        }
        final String combinedName = mailboxExpression.getCombinedName()
                .replace(freeWildcard, SQL_WILDCARD_CHAR)
                .replace(localWildcard, SQL_WILDCARD_CHAR);
        final MailboxPath search = new MailboxPath(mailboxExpression.getBase(), combinedName);

        final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        final List<Mailbox<Id>> mailboxes = mapper.findMailboxWithPathLike(search);
        final List<MailboxMetaData> results = new ArrayList<MailboxMetaData>(mailboxes.size());
        for (Mailbox<Id> mailbox : mailboxes) {
            final String name = mailbox.getName();
            if(belongsToNamespaceAndUser(mailboxExpression.getBase(), mailbox)) {
                if (name.startsWith(baseName)) {
                    final String match = name.substring(baseLength);
                    if (mailboxExpression.isExpressionMatch(match)) {
                        final MailboxMetaData.Children inferiors;
                        if (mapper.hasChildren(mailbox, session.getPathDelimiter())) {
                            inferiors = MailboxMetaData.Children.HAS_CHILDREN;
                        } else {
                            inferiors = MailboxMetaData.Children.HAS_NO_CHILDREN;
                        }
                        MailboxPath mailboxPath = new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), name);
                        results.add(new SimpleMailboxMetaData(mailboxPath, getDelimiter(), inferiors, Selectability.NONE));
                    }
                }
            }
        }
        Collections.sort(results, new StandardMailboxMetaDataComparator());
        return results;
    }

    public boolean belongsToNamespaceAndUser(MailboxPath base, Mailbox<Id> mailbox) {
        if (mailbox.getUser() == null) {
            return  base.getUser() == null
                && mailbox.getNamespace().equals(base.getNamespace());
        }
        return mailbox.getNamespace().equals(base.getNamespace())
            && mailbox.getUser().equals(base.getUser());
    }

    @Override
    public boolean mailboxExists(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        try {
            final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
            mapper.findMailboxByPath(mailboxPath);
            return true;
        } catch (MailboxNotFoundException e) {
            return false;
        }

    }

    @Override
    public void addListener(MailboxPath path, MailboxListener listener, MailboxSession session) throws MailboxException {
        delegatingListener.addListener(path, listener, session);
    }

    /**
     * End processing of Request for session
     */
    @Override
    public void endProcessingRequest(MailboxSession session) {
        if (mailboxSessionMapperFactory instanceof RequestAware) {
            ((RequestAware) mailboxSessionMapperFactory).endProcessingRequest(session);
        }
    }

    /**
     * Do nothing. Sub classes should override this if needed
     */
    @Override
    public void startProcessingRequest(MailboxSession session) {
        // do nothing
    }

    @Override
    public List<MailboxPath> list(MailboxSession session) throws MailboxException {
        List<MailboxPath> mList = new ArrayList<MailboxPath>();
        List<Mailbox<Id>> mailboxes = mailboxSessionMapperFactory.getMailboxMapper(session).list();
        for (int i = 0; i < mailboxes.size(); i++) {
            Mailbox<Id> m = mailboxes.get(i);
            mList.add(new MailboxPath(m.getNamespace(), m.getUser(), m.getName()));
        }
        return Collections.unmodifiableList(mList);

    }

    @Override
    public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        delegatingListener.addGlobalListener(listener, session);
    }

    @Override
    public void removeListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        delegatingListener.removeListener(mailboxPath, listener, session);

    }

    @Override
    public void removeGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        delegatingListener.removeGlobalListener(listener, session);
    }

    @Override
    public boolean hasRight(MailboxPath mailboxPath, MailboxACL.MailboxACLRight right, MailboxSession session) throws MailboxException {
        MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox<Id> mailbox = mapper.findMailboxByPath(mailboxPath);
        MailboxSession.User user = session.getUser();
        String userName = user != null ? user.getUserName() : null;
        return aclResolver.hasRight(userName, groupMembershipResolver, right, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
    }

    @Override
    public MailboxACL.MailboxACLRights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox<Id> mailbox = mapper.findMailboxByPath(mailboxPath);
        MailboxSession.User user = session.getUser();
        if (user != null) {
            return aclResolver.resolveRights(user.getUserName(), groupMembershipResolver, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
        } else {
            return SimpleMailboxACL.NO_RIGHTS;
        }
    }

    public MailboxACL.MailboxACLRights[] listRigths(MailboxPath mailboxPath, final MailboxACL.MailboxACLEntryKey key, MailboxSession session) throws MailboxException {
        final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox<Id> mailbox = mapper.findMailboxByPath(mailboxPath);
        return aclResolver.listRights(key, groupMembershipResolver, mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
    }

    @Override
    public void setRights(MailboxPath mailboxPath, final MailboxACL.MailboxACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        final MailboxMapper<Id> mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        final Mailbox<Id> mailbox = mapper.findMailboxByPath(mailboxPath);
        mapper.execute(
            new Mapper.VoidTransaction() {
                @Override
                public void runVoid() throws MailboxException {
                    mapper.updateACL(mailbox, mailboxACLCommand);
                }
            }
        );
    }

}
