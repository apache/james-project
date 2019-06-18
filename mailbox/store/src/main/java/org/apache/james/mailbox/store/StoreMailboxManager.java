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

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxAnnotationManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.StandardMailboxMetaDataComparator;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.InsufficientRightsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxMetaData.Selectability;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.search.MailboxNameExpression;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
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
    public static final EnumSet<MessageCapabilities> DEFAULT_NO_MESSAGE_CAPABILITIES = EnumSet.noneOf(MessageCapabilities.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreRightManager storeRightManager;
    private final EventBus eventBus;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MailboxAnnotationManager annotationManager;
    private final MailboxPathLocker locker;
    private final MessageParser messageParser;
    private final Factory messageIdFactory;
    private final SessionProvider sessionProvider;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final QuotaComponents quotaComponents;
    private final MessageSearchIndex index;
    private final PreDeletionHooks preDeletionHooks;
    protected final MailboxManagerConfiguration configuration;

    @Inject
    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, SessionProvider sessionProvider,
                               MailboxPathLocker locker, MessageParser messageParser,
                               MessageId.Factory messageIdFactory, MailboxAnnotationManager annotationManager,
                               EventBus eventBus, StoreRightManager storeRightManager,
                               QuotaComponents quotaComponents, MessageSearchIndex searchIndex, MailboxManagerConfiguration configuration,
                               PreDeletionHooks preDeletionHooks) {
        Preconditions.checkNotNull(eventBus);
        Preconditions.checkNotNull(mailboxSessionMapperFactory);

        this.annotationManager = annotationManager;
        this.sessionProvider = sessionProvider;
        this.locker = locker;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.messageParser = messageParser;
        this.messageIdFactory = messageIdFactory;
        this.eventBus = eventBus;
        this.storeRightManager = storeRightManager;
        this.quotaRootResolver = quotaComponents.getQuotaRootResolver();
        this.quotaManager = quotaComponents.getQuotaManager();
        this.quotaComponents = quotaComponents;
        this.index = searchIndex;
        this.configuration = configuration;
        this.preDeletionHooks = preDeletionHooks;
    }

    public QuotaComponents getQuotaComponents() {
        return quotaComponents;
    }

    public Factory getMessageIdFactory() {
        return messageIdFactory;
    }

    public SessionProvider getSessionProvider() {
        return sessionProvider;
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.noneOf(MailboxCapabilities.class);
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return DEFAULT_NO_MESSAGE_CAPABILITIES;
    }
    
    @Override
    public EnumSet<SearchCapabilities> getSupportedSearchCapabilities() {
        return index.getSupportedCapabilities(getSupportedMessageCapabilities());
    }

    /**
     * Return the {@link EventBus} which is used by this {@link MailboxManager}
     *
     * @return delegatingListener
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Return the {@link MessageSearchIndex} used by this {@link MailboxManager}
     *
     * @return index
     */
    public MessageSearchIndex getMessageSearchIndex() {
        return index;
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

    protected StoreRightManager getStoreRightManager() {
        return storeRightManager;
    }

    public MessageParser getMessageParser() {
        return messageParser;
    }

    public PreDeletionHooks getPreDeletionHooks() {
        return preDeletionHooks;
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
        return sessionProvider.createSystemSession(userName);
    }

    @Override
    public char getDelimiter() {
        return sessionProvider.getDelimiter();
    }

    @Override
    public MailboxSession login(String userid, String passwd) throws MailboxException {
        return sessionProvider.login(userid, passwd);
    }

    @Override
    public MailboxSession loginAsOtherUser(String adminUserid, String passwd, String otherUserId) throws MailboxException {
        return sessionProvider.loginAsOtherUser(adminUserid, passwd, otherUserId);
    }

    /**
     * Close the {@link MailboxSession} if not null
     */
    @Override
    public void logout(MailboxSession session, boolean force) {
        sessionProvider.logout(session);
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
        return new StoreMessageManager(DEFAULT_NO_MESSAGE_CAPABILITIES, getMapperFactory(), getMessageSearchIndex(), getEventBus(),
                getLocker(), mailbox, quotaManager,
            getQuotaComponents().getQuotaRootResolver(), getMessageParser(), getMessageIdFactory(), configuration.getBatchSizes(),
            getStoreRightManager(), preDeletionHooks);
    }

    private Mailbox doCreateMailbox(MailboxPath mailboxPath) {
        return new Mailbox(mailboxPath, randomUidValidity());
    }

    @Override
    public MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session)
            throws MailboxException {
        final MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailboxRow = mapper.findMailboxByPath(mailboxPath);

        if (mailboxRow == null) {
            LOGGER.info("Mailbox '{}' not found.", mailboxPath);
            throw new MailboxNotFoundException(mailboxPath);

        } else {
            LOGGER.debug("Loaded mailbox {}", mailboxPath);

            return createMessageManager(mailboxRow, session);
        }
    }

    @Override
    public MessageManager getMailbox(MailboxId mailboxId, MailboxSession session)
            throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailboxRow = mapper.findMailboxById(mailboxId);

        if (mailboxRow == null) {
            LOGGER.info("Mailbox '{}' not found.", mailboxId.serialize());
            throw new MailboxNotFoundException(mailboxId);
        }

        if (! assertUserHasAccessTo(mailboxRow, session)) {
            LOGGER.info("Mailbox '{}' does not belong to user '{}' but to '{}'", mailboxId.serialize(), session.getUser(), mailboxRow.getUser());
            throw new MailboxNotFoundException(mailboxId);
        }

        LOGGER.debug("Loaded mailbox {}", mailboxId.serialize());

        return createMessageManager(mailboxRow, session);
    }

    private boolean assertUserHasAccessTo(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return belongsToCurrentUser(mailbox, session) || userHasLookupRightsOn(mailbox, session);
    }

    private boolean belongsToCurrentUser(Mailbox mailbox, MailboxSession session) {
        return mailbox.generateAssociatedPath().belongsTo(session);
    }

    private boolean userHasLookupRightsOn(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return storeRightManager.hasRight(mailbox, Right.Lookup, session);
    }

    @Override
    public Optional<MailboxId> createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession)
            throws MailboxException {
        LOGGER.debug("createMailbox {}", mailboxPath);

        assertMailboxPathBelongToUser(mailboxSession, mailboxPath);

        if (mailboxPath.getName().isEmpty()) {
            LOGGER.warn("Ignoring mailbox with empty name");
        } else {
            MailboxPath sanitizedMailboxPath = mailboxPath.sanitize(mailboxSession.getPathDelimiter());
            if (isMailboxNameTooLong(mailboxPath)) {
                throw new TooLongMailboxNameException("Mailbox name exceed maximum size of " + MAX_MAILBOX_NAME_LENGTH + " characters");
            }
            if (mailboxExists(sanitizedMailboxPath, mailboxSession)) {
                throw new MailboxExistsException(sanitizedMailboxPath.asString());
            }
            // Create parents first
            // If any creation fails then the mailbox will not be created
            // TODO: transaction
            List<MailboxId> mailboxIds = new ArrayList<>();
            for (MailboxPath mailbox : sanitizedMailboxPath.getHierarchyLevels(getDelimiter())) {
                locker.executeWithLock(mailboxSession, mailbox, (LockAwareExecution<Void>) () -> {
                    if (!mailboxExists(mailbox, mailboxSession)) {
                        Mailbox m = doCreateMailbox(mailbox);
                        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
                        try {
                            mapper.execute(Mapper.toTransaction(() -> mailboxIds.add(mapper.save(m))));
                            // notify listeners
                            eventBus.dispatch(EventFactory.mailboxAdded()
                                .randomEventId()
                                .mailboxSession(mailboxSession)
                                .mailbox(m)
                                .build(),
                                new MailboxIdRegistrationKey(m.getMailboxId()))
                                .block();
                        } catch (MailboxExistsException e) {
                            LOGGER.info("{} mailbox was created concurrently", m.generateAssociatedPath());
                        }
                    }
                    return null;

                }, true);
            }

            if (!mailboxIds.isEmpty()) {
                return Optional.ofNullable(Iterables.getLast(mailboxIds));
            }
        }
        return Optional.empty();
    }

    private void assertMailboxPathBelongToUser(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxException {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            throw new InsufficientRightsException("mailboxPath '" + mailboxPath.asString() + "'"
                + " does not belong to user '" + mailboxSession.getUser().asString() + "'");
        }
    }

    public boolean isMailboxNameTooLong(MailboxPath mailboxPath) {
        return mailboxPath.getName().length() > MAX_MAILBOX_NAME_LENGTH;
    }

    @Override
    public void deleteMailbox(final MailboxPath mailboxPath, final MailboxSession session) throws MailboxException {
        LOGGER.info("deleteMailbox {}", mailboxPath);
        assertIsOwner(session, mailboxPath);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(session);

        mailboxMapper.execute((Mapper.Transaction<Mailbox>) () -> {
            Mailbox mailbox = mailboxMapper.findMailboxByPath(mailboxPath);
            if (mailbox == null) {
                throw new MailboxNotFoundException(mailboxPath);
            }

            QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxPath);
            long messageCount = messageMapper.countMessagesInMailbox(mailbox);

            List<MetadataWithMailboxId> metadata = Iterators.toStream(messageMapper.findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Metadata, UNLIMITED))
                .map(message -> MetadataWithMailboxId.from(message.metaData(), message.getMailboxId()))
                .collect(Guavate.toImmutableList());

            long totalSize = metadata.stream()
                .map(MetadataWithMailboxId::getMessageMetaData)
                .mapToLong(MessageMetaData::getSize)
                .sum();

            preDeletionHooks.runHooks(PreDeletionHook.DeleteOperation.from(metadata)).block();

            // We need to create a copy of the mailbox as maybe we can not refer to the real
            // mailbox once we remove it
            Mailbox m = new Mailbox(mailbox);
            mailboxMapper.delete(mailbox);
            eventBus.dispatch(EventFactory.mailboxDeleted()
                .randomEventId()
                .mailboxSession(session)
                .mailbox(mailbox)
                .quotaRoot(quotaRoot)
                .quotaCount(QuotaCount.count(messageCount))
                .quotaSize(QuotaSize.size(totalSize))
                .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId()))
                .block();
            return m;
        });


    }

    @Override
    public void renameMailbox(MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        LOGGER.debug("renameMailbox {} to {}", from, to);
        if (mailboxExists(to, session)) {
            throw new MailboxExistsException(to.toString());
        }
        if (isMailboxNameTooLong(to)) {
            throw new TooLongMailboxNameException("Mailbox name exceed maximum size of " + MAX_MAILBOX_NAME_LENGTH + " characters");
        }

        assertIsOwner(session, from);
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        mapper.execute(Mapper.toTransaction(() -> doRenameMailbox(from, to, session, mapper)));
    }

    private void assertIsOwner(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxNotFoundException {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            LOGGER.info("Mailbox {} does not belong to {}", mailboxPath.asString(), mailboxSession.getUser().asString());
            throw new MailboxNotFoundException(mailboxPath.asString());
        }
    }

    private void doRenameMailbox(MailboxPath from, MailboxPath to, MailboxSession session, MailboxMapper mapper) throws MailboxException {
        // TODO put this into a serilizable transaction
        Mailbox mailbox = Optional.ofNullable(mapper.findMailboxByPath(from))
            .orElseThrow(() -> new MailboxNotFoundException(from));

        mailbox.setNamespace(to.getNamespace());
        mailbox.setUser(to.getUser());
        mailbox.setName(to.getName());
        mapper.save(mailbox);

        eventBus.dispatch(EventFactory.mailboxRenamed()
            .randomEventId()
            .mailboxSession(session)
            .mailboxId(mailbox.getMailboxId())
            .oldPath(from)
            .newPath(to)
            .build(),
            new MailboxIdRegistrationKey(mailbox.getMailboxId()))
            .block();

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
                eventBus.dispatch(EventFactory.mailboxRenamed()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailboxId(sub.getMailboxId())
                    .oldPath(fromPath)
                    .newPath(sub.generateAssociatedPath())
                    .build(),
                    new MailboxIdRegistrationKey(sub.getMailboxId()))
                    .block();

                LOGGER.debug("Rename mailbox sub-mailbox {} to {}", subOriginalName, subNewName);
            }
            return null;

        }, true);
    }

    @Override
    public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return copyMessages(set, session, toMailbox, fromMailbox);
    }

    @Override
    public List<MessageRange> copyMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException {
        StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return copyMessages(set, session, toMailbox, fromMailbox);
    }

    
    private List<MessageRange> copyMessages(MessageRange set, MailboxSession session, StoreMessageManager toMailbox, StoreMessageManager fromMailbox) throws MailboxException {
        return configuration.getCopyBatcher().batchMessages(set,
            messageRange -> fromMailbox.copyTo(messageRange, toMailbox, session));
    }

    @Override
    public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return configuration.getMoveBatcher().batchMessages(set, messageRange -> fromMailbox.moveTo(messageRange, toMailbox, session));
    }

    @Override
    public List<MailboxMetaData> search(MailboxQuery mailboxExpression, MailboxSession session) throws MailboxException {
        return searchMailboxes(mailboxExpression, session, Right.Lookup);
    }

    private List<MailboxMetaData> searchMailboxes(MailboxQuery mailboxExpression, MailboxSession session, Right right) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Stream<Mailbox> baseMailboxes = mailboxMapper
            .findMailboxWithPathLike(getPathLike(mailboxExpression, session))
            .stream();
        Stream<Mailbox> delegatedMailboxes = getDelegatedMailboxes(mailboxMapper, mailboxExpression, right, session);
        List<Mailbox> mailboxes = Stream.concat(baseMailboxes,
                delegatedMailboxes)
            .distinct()
            .filter(Throwing.predicate(mailbox -> storeRightManager.hasRight(mailbox, right, session)))
            .collect(Guavate.toImmutableList());

        return mailboxes
            .stream()
            .filter(mailbox -> mailboxExpression.isPathMatch(mailbox.generateAssociatedPath()))
            .map(mailbox -> toMailboxMetadata(session, mailboxes, mailbox))
            .sorted(new StandardMailboxMetaDataComparator())
            .collect(Guavate.toImmutableList());
    }

    @VisibleForTesting
    public static MailboxPath getPathLike(MailboxQuery mailboxQuery, MailboxSession mailboxSession) {
        MailboxNameExpression nameExpression = mailboxQuery.getMailboxNameExpression();
        String combinedName = nameExpression.getCombinedName()
            .replace(nameExpression.getFreeWildcard(), SQL_WILDCARD_CHAR)
            .replace(nameExpression.getLocalWildcard(), SQL_WILDCARD_CHAR)
            + SQL_WILDCARD_CHAR;
        MailboxPath base = new MailboxPath(
            mailboxQuery.getNamespace().orElse(MailboxConstants.USER_NAMESPACE),
            mailboxQuery.getUser().orElse(mailboxSession.getUser().asString()),
            combinedName);
        return new MailboxPath(base, combinedName);
    }

    private Stream<Mailbox> getDelegatedMailboxes(MailboxMapper mailboxMapper, MailboxQuery mailboxQuery,
                                                  Right right, MailboxSession session) throws MailboxException {
        if (mailboxQuery.isPrivateMailboxes(session)) {
            return Stream.of();
        }
        return mailboxMapper.findNonPersonalMailboxes(session.getUser().asString(), right).stream();
    }

    private MailboxMetaData toMailboxMetadata(MailboxSession session, List<Mailbox> mailboxes, Mailbox mailbox) {
        return new MailboxMetaData(
            mailbox.generateAssociatedPath(),
            mailbox.getMailboxId(),
            getDelimiter(),
            computeChildren(session, mailboxes, mailbox),
            Selectability.NONE);
    }

    private MailboxMetaData.Children computeChildren(MailboxSession session, List<Mailbox> potentialChildren, Mailbox mailbox) {
        if (hasChildIn(mailbox, potentialChildren, session)) {
            return MailboxMetaData.Children.HAS_CHILDREN;
        } else {
            return MailboxMetaData.Children.HAS_NO_CHILDREN;
        }
    }

    private boolean hasChildIn(Mailbox parentMailbox, List<Mailbox> mailboxesWithPathLike, MailboxSession mailboxSession) {
        return mailboxesWithPathLike.stream()
            .anyMatch(mailbox -> mailbox.isChildOf(parentMailbox, mailboxSession));
    }

    @Override
    public List<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException {
        ImmutableSet<MailboxId> wantedMailboxesId =
            getInMailboxes(expression.getInMailboxes(), session)
                .filter(id -> !expression.getNotInMailboxes().contains(id))
                .collect(Guavate.toImmutableSet());

        return index.search(session, wantedMailboxesId, expression.getSearchQuery(), limit);
    }

    private Stream<MailboxId> getInMailboxes(ImmutableSet<MailboxId> inMailboxes, MailboxSession session) throws MailboxException {
       if (inMailboxes.isEmpty()) {
            return getAllReadableMailbox(session);
        } else {
            return getAllReadableMailbox(session).filter(inMailboxes::contains);
        }
    }

    private Stream<MailboxId> getAllReadableMailbox(MailboxSession session) throws MailboxException {
        return searchMailboxes(MailboxQuery.builder().matchesAllMailboxNames().build(), session, Right.Read)
            .stream()
            .map(MailboxMetaData::getId);
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

    /**
     * End processing of Request for session
     */
    @Override
    public void endProcessingRequest(MailboxSession session) {
        mailboxSessionMapperFactory.endProcessingRequest(session);
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
    public boolean hasRight(MailboxPath mailboxPath, Right right, MailboxSession session) throws MailboxException {
        return storeRightManager.hasRight(mailboxPath, right, session);
    }

    @Override
    public boolean hasRight(MailboxId mailboxId, Right right, MailboxSession session) throws MailboxException {
        return storeRightManager.hasRight(mailboxId, right, session);
    }

    @Override
    public Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return storeRightManager.myRights(mailboxPath, session);
    }

    @Override
    public Rfc4314Rights myRights(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        return storeRightManager.myRights(mailboxId, session);
    }

    @Override
    public Rfc4314Rights[] listRights(MailboxPath mailboxPath, MailboxACL.EntryKey key, MailboxSession session) throws MailboxException {
        return storeRightManager.listRights(mailboxPath, key, session);
    }

    @Override
    public MailboxACL listRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return storeRightManager.listRights(mailboxPath, session);
    }

    @Override
    public void applyRightsCommand(MailboxPath mailboxPath, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        storeRightManager.applyRightsCommand(mailboxPath, mailboxACLCommand, session);
    }

    @Override
    public void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        storeRightManager.setRights(mailboxPath, mailboxACL, session);
    }

    @Override
    public void setRights(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        storeRightManager.setRights(mailboxId, mailboxACL, session);
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return annotationManager.getAllAnnotations(mailboxPath, session);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys)
            throws MailboxException {
        return annotationManager.getAnnotationsByKeys(mailboxPath, session, keys);
    }

    @Override
    public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations)
            throws MailboxException {
        annotationManager.updateAnnotations(mailboxPath, session, mailboxAnnotations);
    }


    @Override
    public boolean hasCapability(MailboxCapabilities capability) {
        return getSupportedMailboxCapabilities().contains(capability);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session,
            Set<MailboxAnnotationKey> keys) throws MailboxException {
        return annotationManager.getAnnotationsByKeysWithOneDepth(mailboxPath, session, keys);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session,
            Set<MailboxAnnotationKey> keys) throws MailboxException {
        return annotationManager.getAnnotationsByKeysWithAllDepth(mailboxPath, session, keys);
    }

    @Override
    public boolean hasChildren(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return mapper.hasChildren(mailbox, session.getPathDelimiter());
    }
}
