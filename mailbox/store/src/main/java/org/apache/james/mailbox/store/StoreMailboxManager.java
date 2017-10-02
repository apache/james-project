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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.SessionType;
import org.apache.james.mailbox.MailboxSessionIdGenerator;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.RequestAware;
import org.apache.james.mailbox.StandardMailboxMetaDataComparator;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.NotAdminException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxMetaData.Selectability;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mailbox.store.quota.QuotaUpdater;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Iterables;

/**
 * This base class of an {@link MailboxManager} implementation provides a high-level api for writing your own
 * {@link MailboxManager} implementation. If you plan to write your own {@link MailboxManager} its most times so easiest
 * to extend just this class or use it directly.
 * <p/>
 * If you need a more low-level api just implement {@link MailboxManager} directly
 *
 */
public class StoreMailboxManager implements MailboxManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreMailboxManager.class);

    public static final char SQL_WILDCARD_CHAR = '%';

    private MailboxEventDispatcher dispatcher;
    private DelegatingMailboxListener delegatingListener;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    private final Authenticator authenticator;

    private Authorizator authorizator;

    private final MailboxACLResolver aclResolver;

    private final GroupMembershipResolver groupMembershipResolver;

    private final static Random RANDOM = new Random();

    private MessageBatcher copyBatcher;

    private MessageBatcher moveBatcher;

    private final MailboxPathLocker locker;

    private MessageSearchIndex index;

    private MailboxSessionIdGenerator idGenerator;

    private QuotaManager quotaManager;

    private QuotaRootResolver quotaRootResolver;

    private QuotaUpdater quotaUpdater;

    private BatchSizes batchSizes = BatchSizes.defaultValues();

    private final MessageParser messageParser;
    private final Factory messageIdFactory;

    private final int limitOfAnnotations;

    private final int limitAnnotationSize;

    private final ImmutableMailboxMessage.Factory immutableMailboxMessageFactory;

    @Inject
    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator, Authorizator authorizator, 
            MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, 
            MessageParser messageParser, MessageId.Factory messageIdFactory, MailboxEventDispatcher mailboxEventDispatcher,
            DelegatingMailboxListener delegatingListener) {
        this(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver, messageParser, messageIdFactory,
                MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE, mailboxEventDispatcher, delegatingListener);
    }

    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator, Authorizator authorizator,
                               MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver,
                               MessageParser messageParser, MessageId.Factory messageIdFactory) {
        this(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver, messageParser, messageIdFactory,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
    }

    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator, Authorizator authorizator,
            MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, MessageParser messageParser,
            MessageId.Factory messageIdFactory, int limitOfAnnotations, int limitAnnotationSize) {
        this(mailboxSessionMapperFactory, authenticator, authorizator, new JVMMailboxPathLocker(), aclResolver, groupMembershipResolver, messageParser, messageIdFactory,
                limitOfAnnotations, limitAnnotationSize);
    }

    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator, Authorizator authorizator,
            MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, MessageParser messageParser,
            MessageId.Factory messageIdFactory, int limitOfAnnotations, int limitAnnotationSize) {
        this(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver, messageParser, messageIdFactory,
            limitOfAnnotations, limitAnnotationSize, null, null);
    }

    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator, Authorizator authorizator,
                               MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, MessageParser messageParser,
                               MessageId.Factory messageIdFactory, int limitOfAnnotations, int limitAnnotationSize, MailboxEventDispatcher mailboxEventDispatcher, DelegatingMailboxListener delegatingListener) {
        this.authenticator = authenticator;
        this.authorizator = authorizator;
        this.locker = locker;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.aclResolver = aclResolver;
        this.groupMembershipResolver = groupMembershipResolver;
        this.messageParser = messageParser;
        this.messageIdFactory = messageIdFactory;
        this.limitOfAnnotations = limitOfAnnotations;
        this.limitAnnotationSize = limitAnnotationSize;
        this.delegatingListener = delegatingListener;
        this.dispatcher = mailboxEventDispatcher;
        this.immutableMailboxMessageFactory = new ImmutableMailboxMessage.Factory(this);
    }

    protected Factory getMessageIdFactory() {
        return messageIdFactory;
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

    public void setCopyBatchSize(BatchSizes batchSizes) {
        this.copyBatcher = new MessageBatcher(batchSizes.getCopyBatchSize());
    }

    public void setMoveBatchSize(BatchSizes batchSizes) {
        this.moveBatcher = new MessageBatcher(batchSizes.getMoveBatchSize());
    }

    public void setBatchSizes(BatchSizes batchSizes) {
        this.batchSizes = batchSizes;
    }

    public BatchSizes getBatchSizes() {
        return batchSizes;
    }

    public ImmutableMailboxMessage.Factory getImmutableMailboxMessageFactory() {
        return immutableMailboxMessageFactory;
    }

    /**
     * Init the {@link MailboxManager}
     *
     * @throws MailboxException
     */
    @PostConstruct
    public void init() throws MailboxException {

        if (dispatcher == null) {
            dispatcher = new MailboxEventDispatcher(getDelegationListener());
        }

        if (index == null) {
            index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());
        }
        if (index instanceof ListeningMessageSearchIndex) {
            this.addGlobalListener((MailboxListener) index, null);
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
        if (copyBatcher == null) {
            copyBatcher = new MessageBatcher(MessageBatcher.NO_BATCH_SIZE);
        }
        if (moveBatcher == null) {
            moveBatcher = new MessageBatcher(MessageBatcher.NO_BATCH_SIZE);
        }
        if (hasCapability(MailboxCapabilities.Annotation)) {
            MailboxSession session = null;
            this.addGlobalListener(new MailboxAnnotationListener(mailboxSessionMapperFactory), session);
        }
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.noneOf(MailboxCapabilities.class);
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return EnumSet.noneOf(MessageCapabilities.class);
    }
    
    @Override
    public EnumSet<SearchCapabilities> getSupportedSearchCapabilities() {
        return index.getSupportedCapabilities(getSupportedMessageCapabilities());
    }
    

    /**
     * Return the {@link DelegatingMailboxListener} which is used by this {@link MailboxManager}
     *
     * @return delegatingListener
     */
    public DelegatingMailboxListener getDelegationListener() {
        if (delegatingListener == null) {
            delegatingListener = new DefaultDelegatingMailboxListener();
        }
        return delegatingListener;
    }


    /**
     * Return the {@link MessageSearchIndex} used by this {@link MailboxManager}
     *
     * @return index
     */
    public MessageSearchIndex getMessageSearchIndex() {
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
    public MailboxEventDispatcher getEventDispatcher() {
        return dispatcher;
    }

    /**
     * Return the {@link MailboxSessionMapperFactory} used by this {@link MailboxManager}
     *
     * @return mailboxSessionMapperFactory
     */
    public MailboxSessionMapperFactory getMapperFactory() {
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

    public MessageParser getMessageParser() {
        return messageParser;
    }

    /**
     * Set the {@link DelegatingMailboxListener} to use with this {@link MailboxManager} instance. If none is set here a {@link DefaultDelegatingMailboxListener} instance will
     * be created lazy
     *
     * @param delegatingListener
     */
    public void setDelegatingMailboxListener(DelegatingMailboxListener delegatingListener) {
        this.delegatingListener = delegatingListener;
        dispatcher = new MailboxEventDispatcher(getDelegationListener());
    }

    /**
     * Set the {@link MessageSearchIndex} which should be used by this {@link MailboxManager}. If none is given this implementation will use a {@link SimpleMessageSearchIndex}
     * by default
     *
     * @param index
     */
    public void setMessageSearchIndex(MessageSearchIndex index) {
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
    public MailboxSession createSystemSession(String userName) {
        return createSession(userName, null, SessionType.System);
    }

    /**
     * Create Session
     *
     * @param userName
     * @return session
     */

    protected MailboxSession createSession(String userName, String password, SessionType type) {
        return new SimpleMailboxSession(randomId(), userName, password, new ArrayList<>(), getDelimiter(), type);
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
    private boolean isValidLogin(String userid, String passwd) throws MailboxException {
        return authenticator.isAuthentic(userid, passwd);
    }

    @Override
    public MailboxSession login(String userid, String passwd) throws BadCredentialsException, MailboxException {
        if (isValidLogin(userid, passwd)) {
            return createSession(userid, passwd, SessionType.User);
        } else {
            throw new BadCredentialsException();
        }
    }

    @Override
    public MailboxSession loginAsOtherUser(String adminUserid, String passwd, String otherUserId) throws MailboxException {
        if (! isValidLogin(adminUserid, passwd)) {
            throw new BadCredentialsException();
        }
        Authorizator.AuthorizationState authorizationState = authorizator.canLoginAsOtherUser(adminUserid, otherUserId);
        switch (authorizationState) {
            case ALLOWED:
                return createSystemSession(otherUserId);
            case NOT_ADMIN:
                throw new NotAdminException();
            case UNKNOWN_USER:
                throw new UserDoesNotExistException(otherUserId);
            default:
                throw new RuntimeException("Unknown AuthorizationState " + authorizationState);
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
    protected StoreMessageManager createMessageManager(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return new StoreMessageManager(getMapperFactory(), getMessageSearchIndex(), getEventDispatcher(), 
                getLocker(), mailbox, getAclResolver(), getGroupMembershipResolver(), getQuotaManager(), 
                getQuotaRootResolver(), getMessageParser(), getMessageIdFactory(), getBatchSizes(), getImmutableMailboxMessageFactory());
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
    protected org.apache.james.mailbox.store.mail.model.Mailbox doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return new SimpleMailbox(mailboxPath, randomUidValidity());
    }

    @Override
    public MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session)
            throws MailboxException {
        final MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailboxRow = mapper.findMailboxByPath(mailboxPath);

        if (mailboxRow == null) {
            LOGGER.info("Mailbox '" + mailboxPath + "' not found.");
            throw new MailboxNotFoundException(mailboxPath);

        } else {
            LOGGER.debug("Loaded mailbox " + mailboxPath);

            return createMessageManager(mailboxRow, session);
        }
    }

    @Override
    public MessageManager getMailbox(MailboxId mailboxId, MailboxSession session)
            throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailboxRow = mapper.findMailboxById(mailboxId);

        if (mailboxRow == null) {
            LOGGER.info("Mailbox '" + mailboxId.serialize() + "' not found.");
            throw new MailboxNotFoundException(mailboxId.serialize());
        }

        if (! assertUserHasAccessTo(mailboxRow, session)) {
            LOGGER.info("Mailbox '" + mailboxId.serialize() + "' does not belong to user '" + session.getUser() + "' but to '" + mailboxRow.getUser());
            throw new MailboxNotFoundException(mailboxId.serialize());
        }

        LOGGER.debug("Loaded mailbox " + mailboxId.serialize());

        return createMessageManager(mailboxRow, session);
    }

    private boolean assertUserHasAccessTo(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return belongsToCurrentUser(mailbox, session) || userHasReadRightsOn(mailbox, session);
    }

    private boolean belongsToCurrentUser(Mailbox mailbox, MailboxSession session) {
        return session.getUser().isSameUser(mailbox.getUser());
    }

    private boolean userHasReadRightsOn(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return hasRight(mailbox.generateAssociatedPath(), Right.Read, session);
    }

    @Override
    public Optional<MailboxId> createMailbox(MailboxPath mailboxPath, final MailboxSession mailboxSession)
            throws MailboxException {
        LOGGER.debug("createMailbox " + mailboxPath);
        final int length = mailboxPath.getName().length();
        if (length == 0) {
            LOGGER.warn("Ignoring mailbox with empty name");
        } else {
            if (mailboxPath.getName().charAt(length - 1) == getDelimiter())
                mailboxPath.setName(mailboxPath.getName().substring(0, length - 1));
            if (mailboxExists(mailboxPath, mailboxSession))
                throw new MailboxExistsException(mailboxPath.toString());
            // Create parents first
            // If any creation fails then the mailbox will not be created
            // TODO: transaction
            final List<MailboxId> mailboxIds = new ArrayList<>();
            for (final MailboxPath mailbox : mailboxPath.getHierarchyLevels(getDelimiter()))

                locker.executeWithLock(mailboxSession, mailbox, (LockAwareExecution<Void>) () -> {
                    if (!mailboxExists(mailbox, mailboxSession)) {
                        final Mailbox m = doCreateMailbox(mailbox, mailboxSession);
                        final MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
                        mapper.execute(Mapper.toTransaction(() -> mailboxIds.add(mapper.save(m))));

                        // notify listeners
                        dispatcher.mailboxAdded(mailboxSession, m);
                    }
                    return null;

                }, true);

            if (!mailboxIds.isEmpty()) {
                return Optional.ofNullable(Iterables.getLast(mailboxIds));
            }
        }
        return Optional.empty();
    }

    @Override
    public void deleteMailbox(final MailboxPath mailboxPath, final MailboxSession session) throws MailboxException {
        LOGGER.info("deleteMailbox " + mailboxPath);
        final MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        Mailbox mailbox = mapper.execute((Mapper.Transaction<Mailbox>) () -> {
            final Mailbox mailbox1 = mapper.findMailboxByPath(mailboxPath);
            if (mailbox1 == null) {
                throw new MailboxNotFoundException("Mailbox not found");
            }

            // We need to create a copy of the mailbox as maybe we can not refer to the real
            // mailbox once we remove it
            SimpleMailbox m = new SimpleMailbox(mailbox1);
            mapper.delete(mailbox1);
            return m;
        });

        dispatcher.mailboxDeleted(session, mailbox);

    }

    @Override
    public void renameMailbox(MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        LOGGER.debug("renameMailbox " + from + " to " + to);
        if (mailboxExists(to, session)) {
            throw new MailboxExistsException(to.toString());
        }

        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        mapper.execute(Mapper.toTransaction(() -> doRenameMailbox(from, to, session, mapper)));
    }

    private void doRenameMailbox(MailboxPath from, MailboxPath to, MailboxSession session, MailboxMapper mapper) throws MailboxException {
        // TODO put this into a serilizable transaction
        Mailbox mailbox = Optional.ofNullable(mapper.findMailboxByPath(from))
            .orElseThrow(() -> new MailboxNotFoundException(from));

        mailbox.setNamespace(to.getNamespace());
        mailbox.setUser(to.getUser());
        mailbox.setName(to.getName());
        mapper.save(mailbox);

        dispatcher.mailboxRenamed(session, from, mailbox);

        // rename submailboxes
        MailboxPath children = new MailboxPath(from.getNamespace(), from.getUser(), from.getName() + getDelimiter() + "%");
        locker.executeWithLock(session, children, (LockAwareExecution<Void>) () -> {
            List<Mailbox> subMailboxes = mapper.findMailboxWithPathLike(children);
            for (Mailbox sub : subMailboxes) {
                String subOriginalName = sub.getName();
                String subNewName = to.getName() + subOriginalName.substring(from.getName().length());
                MailboxPath fromPath = new MailboxPath(children, subOriginalName);
                sub.setName(subNewName);
                mapper.save(sub);
                dispatcher.mailboxRenamed(session, fromPath, sub);

                LOGGER.debug("Rename mailbox sub-mailbox " + subOriginalName + " to " + subNewName);
            }
            return null;

        }, true);
    }

    @Override
    public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, final MailboxSession session) throws MailboxException {
        final StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        final StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return copyMessages(set, session, toMailbox, fromMailbox);
    }

    @Override
    public List<MessageRange> copyMessages(MessageRange set, MailboxId from, MailboxId to, final MailboxSession session) throws MailboxException {
        final StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        final StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return copyMessages(set, session, toMailbox, fromMailbox);
    }

    
    private List<MessageRange> copyMessages(MessageRange set, final MailboxSession session,
            final StoreMessageManager toMailbox, final StoreMessageManager fromMailbox) throws MailboxException {
        return copyBatcher.batchMessages(set,
            messageRange -> fromMailbox.copyTo(messageRange, toMailbox, session));
    }

    @Override
    public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, final MailboxSession session) throws MailboxException {
        final StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        final StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return moveBatcher.batchMessages(set,
            messageRange -> fromMailbox.moveTo(messageRange, toMailbox, session));
    }

    @Override
    public List<MailboxMetaData> search(MailboxQuery mailboxExpression, MailboxSession session)
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
        String combinedName = mailboxExpression.getCombinedName()
                .replace(freeWildcard, SQL_WILDCARD_CHAR)
                .replace(localWildcard, SQL_WILDCARD_CHAR)
            + SQL_WILDCARD_CHAR;
        MailboxPath search = new MailboxPath(mailboxExpression.getBase(), combinedName);

        List<Mailbox> mailboxes = mailboxSessionMapperFactory.getMailboxMapper(session)
            .findMailboxWithPathLike(search);
        List<MailboxMetaData> results = new ArrayList<>(mailboxes.size());
        for (Mailbox mailbox : mailboxes) {
            final String name = mailbox.getName();
            if(belongsToNamespaceAndUser(mailboxExpression.getBase(), mailbox)) {
                if (name.startsWith(baseName)) {
                    final String match = name.substring(baseLength);
                    if (mailboxExpression.isExpressionMatch(match)) {
                        final MailboxMetaData.Children inferiors;
                        List<Mailbox> potentialChildren = mailboxes;
                        if (hasChildIn(mailbox, potentialChildren, session)) {
                            inferiors = MailboxMetaData.Children.HAS_CHILDREN;
                        } else {
                            inferiors = MailboxMetaData.Children.HAS_NO_CHILDREN;
                        }
                        MailboxPath mailboxPath = new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), name);
                        results.add(new SimpleMailboxMetaData(mailboxPath, mailbox.getMailboxId(), getDelimiter(), inferiors, Selectability.NONE));
                    }
                }
            }
        }
        Collections.sort(results, new StandardMailboxMetaDataComparator());
        return results;
    }

    private boolean hasChildIn(Mailbox parentMailbox, List<Mailbox> mailboxesWithPathLike, MailboxSession mailboxSession) {
        return mailboxesWithPathLike.stream()
            .anyMatch(mailbox -> mailbox.isChildOf(parentMailbox, mailboxSession));
    }

    @Override
    public List<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException {
        return index.search(session, expression, limit);
    }

    public boolean belongsToNamespaceAndUser(MailboxPath base, Mailbox mailbox) {
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
            final MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
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
        return mailboxSessionMapperFactory.getMailboxMapper(session)
            .list()
            .stream()
            .map(Mailbox::generateAssociatedPath)
            .collect(Guavate.toImmutableList());
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
    public boolean hasRight(MailboxPath mailboxPath, Right right, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        MailboxSession.User user = session.getUser();
        String userName = user != null ? user.getUserName() : null;
        return aclResolver.hasRight(userName, groupMembershipResolver, right, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
    }

    @Override
    public MailboxACL.Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        MailboxSession.User user = session.getUser();
        if (user != null) {
            return aclResolver.resolveRights(user.getUserName(), groupMembershipResolver, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
        } else {
            return MailboxACL.NO_RIGHTS;
        }
    }

    public MailboxACL.Rfc4314Rights[] listRigths(MailboxPath mailboxPath, MailboxACL.EntryKey key, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return aclResolver.listRights(key, groupMembershipResolver, mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
    }

    @Override
    public void applyRightsCommand(MailboxPath mailboxPath, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        mapper.execute(Mapper.toTransaction(() -> mapper.updateACL(mailbox, mailboxACLCommand)));
    }
    
    @Override
    public void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        mapper.execute(Mapper.toTransaction(() -> mapper.setACL(mailbox, mailboxACL)));
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        final AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        final MailboxId mailboxId = getMailbox(mailboxPath, session).getId();

        return annotationMapper.execute(
            () -> annotationMapper.getAllAnnotations(mailboxId));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, final Set<MailboxAnnotationKey> keys)
            throws MailboxException {
        final AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        final MailboxId mailboxId = getMailbox(mailboxPath, session).getId();

        return annotationMapper.execute(
            () -> annotationMapper.getAnnotationsByKeys(mailboxId, keys));
    }

    @Override
    public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, final List<MailboxAnnotation> mailboxAnnotations)
            throws MailboxException {
        final AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        final MailboxId mailboxId = getMailbox(mailboxPath, session).getId();

        annotationMapper.execute(Mapper.toTransaction(() -> {
            for (MailboxAnnotation annotation : mailboxAnnotations) {
                if (annotation.isNil()) {
                    annotationMapper.deleteAnnotation(mailboxId, annotation.getKey());
                } else if (canInsertOrUpdate(mailboxId, annotation, annotationMapper)) {
                    annotationMapper.insertAnnotation(mailboxId, annotation);
                }
            }
        }));
    }

    private boolean canInsertOrUpdate(MailboxId mailboxId, MailboxAnnotation annotation, AnnotationMapper annotationMapper) throws AnnotationException {
        if (annotation.size() > limitAnnotationSize) {
            throw new AnnotationException("annotation too big.");
        }
        if (!annotationMapper.exist(mailboxId, annotation)
            && annotationMapper.countAnnotations(mailboxId) >= limitOfAnnotations) {
            throw new AnnotationException("too many annotations.");
        }
        return true;
    }

    @Override
    public boolean hasCapability(MailboxCapabilities capability) {
        return getSupportedMailboxCapabilities().contains(capability);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session,
            final Set<MailboxAnnotationKey> keys) throws MailboxException {
        final AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        final MailboxId mailboxId = getMailbox(mailboxPath, session).getId();

        return annotationMapper.execute(
            () -> annotationMapper.getAnnotationsByKeysWithOneDepth(mailboxId, keys));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session,
            final Set<MailboxAnnotationKey> keys) throws MailboxException {
        final AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        final MailboxId mailboxId = getMailbox(mailboxPath, session).getId();

        return annotationMapper.execute(
            () -> annotationMapper.getAnnotationsByKeysWithAllDepth(mailboxId, keys));
    }

    @Override
    public boolean hasChildren(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return mapper.hasChildren(mailbox, session.getPathDelimiter());
    }
}
