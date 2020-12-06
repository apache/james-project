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

import static org.apache.james.mailbox.store.MailboxReactorUtils.block;
import static org.apache.james.mailbox.store.MailboxReactorUtils.blockOptional;
import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.MailboxAnnotationManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.InboxAlreadyCreated;
import org.apache.james.mailbox.exception.InsufficientRightsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxCounters;
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
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.apache.james.util.FunctionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * This base class of an {@link MailboxManager} implementation provides a high-level api for writing your own
 * {@link MailboxManager} implementation. If you plan to write your own {@link MailboxManager} its most times so easiest
 * to extend just this class or use it directly.
 * <p/>
 * If you need a more low-level api just implement {@link MailboxManager} directly
 */
public class StoreMailboxManager implements MailboxManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreMailboxManager.class);
    public static final char SQL_WILDCARD_CHAR = '%';
    public static final EnumSet<MessageCapabilities> DEFAULT_NO_MESSAGE_CAPABILITIES = EnumSet.noneOf(MessageCapabilities.class);
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration MIN_BACKOFF = Duration.ofMillis(100);
    public static final RetryBackoffSpec RETRY_BACKOFF_SPEC = Retry.backoff(MAX_ATTEMPTS, MIN_BACKOFF);

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
     */
    protected MessageSearchIndex getMessageSearchIndex() {
        return index;
    }

    /**
     * Return the {@link MailboxSessionMapperFactory} used by this {@link MailboxManager}
     */
    public MailboxSessionMapperFactory getMapperFactory() {
        return mailboxSessionMapperFactory;
    }

    protected MailboxPathLocker getLocker() {
        return locker;
    }

    protected StoreRightManager getStoreRightManager() {
        return storeRightManager;
    }

    protected MessageParser getMessageParser() {
        return messageParser;
    }

    protected PreDeletionHooks getPreDeletionHooks() {
        return preDeletionHooks;
    }

    @Override
    public MailboxSession createSystemSession(Username userName) {
        return sessionProvider.createSystemSession(userName);
    }

    @Override
    public char getDelimiter() {
        return sessionProvider.getDelimiter();
    }

    @Override
    public MailboxSession login(Username userid, String passwd) throws MailboxException {
        return sessionProvider.login(userid, passwd);
    }

    @Override
    public MailboxSession loginAsOtherUser(Username adminUserid, String passwd, Username otherUserId) throws MailboxException {
        return sessionProvider.loginAsOtherUser(adminUserid, passwd, otherUserId);
    }

    @Override
    public void logout(MailboxSession session) {
        sessionProvider.logout(session);
    }

    /**
     * Create a {@link MailboxManager} for the given Mailbox. By default this will return a {@link StoreMessageManager}. If
     * your implementation needs something different, just override this method
     *
     * @return storeMailbox
     */
    protected StoreMessageManager createMessageManager(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return new StoreMessageManager(DEFAULT_NO_MESSAGE_CAPABILITIES, getMapperFactory(), getMessageSearchIndex(), getEventBus(),
            getLocker(), mailbox, quotaManager,
            getQuotaComponents().getQuotaRootResolver(), configuration.getBatchSizes(),
            getStoreRightManager(), preDeletionHooks, new MessageStorer.WithoutAttachment(mailboxSessionMapperFactory, messageIdFactory, new MessageFactory.StoreMessageFactory()));
    }

    @Override
    public MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        final MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailboxRow = mapper.findMailboxByPath(mailboxPath)
            .blockOptional()
            .orElseThrow(() -> {
                LOGGER.info("Mailbox '{}' not found.", mailboxPath);
                return new MailboxNotFoundException(mailboxPath);
            });

        if (!assertUserHasAccessTo(mailboxRow, session)) {
            LOGGER.info("Mailbox '{}' does not belong to user '{}' but to '{}'", mailboxPath, session.getUser(), mailboxRow.getUser());
            throw new MailboxNotFoundException(mailboxPath);
        }

        LOGGER.debug("Loaded mailbox {}", mailboxPath);

        return createMessageManager(mailboxRow, session);
    }

    @Override
    public MessageManager getMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailboxRow = block(mapper.findMailboxById(mailboxId));

        if (!assertUserHasAccessTo(mailboxRow, session)) {
            LOGGER.info("Mailbox '{}' does not belong to user '{}' but to '{}'", mailboxId.serialize(), session.getUser(), mailboxRow.getUser());
            throw new MailboxNotFoundException(mailboxId);
        }

        LOGGER.debug("Loaded mailbox {}", mailboxId.serialize());

        return createMessageManager(mailboxRow, session);
    }

    private boolean assertUserHasAccessTo(Mailbox mailbox, MailboxSession session) {
        return belongsToCurrentUser(mailbox, session) || userHasLookupRightsOn(mailbox, session);
    }

    private boolean belongsToCurrentUser(Mailbox mailbox, MailboxSession session) {
        return mailbox.generateAssociatedPath().belongsTo(session);
    }

    private boolean userHasLookupRightsOn(Mailbox mailbox, MailboxSession session) {
        return storeRightManager.hasRight(mailbox, Right.Lookup, session);
    }

    @Override
    public Optional<MailboxId> createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        LOGGER.debug("createMailbox {}", mailboxPath);

        assertMailboxPathBelongToUser(mailboxSession, mailboxPath);

        if (mailboxPath.getName().isEmpty()) {
            LOGGER.warn("Ignoring mailbox with empty name");
        } else {
            MailboxPath sanitizedMailboxPath = mailboxPath.sanitize(mailboxSession.getPathDelimiter());
            sanitizedMailboxPath.assertAcceptable(mailboxSession.getPathDelimiter());

            if (block(mailboxExists(sanitizedMailboxPath, mailboxSession))) {
                throw new MailboxExistsException(sanitizedMailboxPath.asString());
            }

            List<MailboxId> mailboxIds = createMailboxesForPath(mailboxSession, sanitizedMailboxPath);

            if (!mailboxIds.isEmpty()) {
                return Optional.ofNullable(Iterables.getLast(mailboxIds));
            }
        }
        return Optional.empty();
    }

    private List<MailboxId> createMailboxesForPath(MailboxSession mailboxSession, MailboxPath sanitizedMailboxPath) {
        // Create parents first
        // If any creation fails then the mailbox will not be created
        // TODO: transaction
        List<MailboxPath> intermediatePaths = sanitizedMailboxPath.getHierarchyLevels(getDelimiter());
        boolean isRootPath = intermediatePaths.size() == 1;

        return intermediatePaths
            .stream()
            .flatMap(Throwing.<MailboxPath, Stream<MailboxId>>function(mailboxPath -> manageMailboxCreation(mailboxSession, isRootPath, mailboxPath)).sneakyThrow())
            .collect(Guavate.toImmutableList());
    }

    private Stream<MailboxId> manageMailboxCreation(MailboxSession mailboxSession, boolean isRootPath, MailboxPath mailboxPath) throws MailboxException {
        if (mailboxPath.isInbox()) {
            if (block(hasInbox(mailboxSession))) {
                return duplicatedINBOXCreation(isRootPath, mailboxPath);
            }

            return performConcurrentMailboxCreation(mailboxSession, MailboxPath.inbox(mailboxSession)).stream();
        }

        return performConcurrentMailboxCreation(mailboxSession, mailboxPath).stream();
    }


    private Stream<MailboxId> duplicatedINBOXCreation(boolean isRootPath, MailboxPath mailbox) throws InboxAlreadyCreated {
        if (isRootPath) {
            throw new InboxAlreadyCreated(mailbox.getName());
        }

        return Stream.empty();
    }

    private List<MailboxId> performConcurrentMailboxCreation(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxException {
        List<MailboxId> mailboxIds = new ArrayList<>();
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        locker.executeWithLock(mailboxPath, () ->
            block(mapper.pathExists(mailboxPath)
                .filter(FunctionalUtils.identityPredicate().negate())
                .flatMap(any -> mapper.executeReactive(mapper.create(mailboxPath, UidValidity.generate())
                    .doOnNext(mailbox -> mailboxIds.add(mailbox.getMailboxId()))
                    .flatMap(mailbox ->
                        // notify listeners
                        eventBus.dispatch(EventFactory.mailboxAdded()
                                .randomEventId()
                                .mailboxSession(mailboxSession)
                                .mailbox(mailbox)
                                .build(),
                            new MailboxIdRegistrationKey(mailbox.getMailboxId()))))
                    .onErrorResume(e -> {
                        if (e instanceof MailboxExistsException) {
                            LOGGER.info("{} mailbox was created concurrently", mailboxPath.asString());
                        } else if (e instanceof MailboxException) {
                            return Mono.error(e);
                        }
                        return Mono.empty();
                    }))
                .then()), MailboxPathLocker.LockType.Write);

        return mailboxIds;
    }

    private void assertMailboxPathBelongToUser(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxException {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            throw new InsufficientRightsException("mailboxPath '" + mailboxPath.asString() + "'"
                + " does not belong to user '" + mailboxSession.getUser().asString() + "'");
        }
    }

    @Override
    public void deleteMailbox(final MailboxPath mailboxPath, final MailboxSession session) throws MailboxException {
        LOGGER.info("deleteMailbox {}", mailboxPath);
        assertIsOwner(session, mailboxPath);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        mailboxMapper.execute(() -> block(mailboxMapper.findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> doDeleteMailbox(mailboxMapper, mailbox, session))
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailboxPath)))));
    }

    @Override
    public Mailbox deleteMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        LOGGER.info("deleteMailbox {}", mailboxId);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mailboxMapper.execute(() -> block(mailboxMapper.findMailboxById(mailboxId)
            .map(Throwing.<Mailbox, Mailbox>function(mailbox -> {
                assertIsOwner(session, mailbox.generateAssociatedPath());
                return mailbox;
            }).sneakyThrow())
            .flatMap(mailbox -> doDeleteMailbox(mailboxMapper, mailbox, session))));
    }

    private Mono<Mailbox> doDeleteMailbox(MailboxMapper mailboxMapper, Mailbox mailbox, MailboxSession session) {
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(session);

        Mono<QuotaRoot> quotaRootPublisher = Mono.fromCallable(() -> quotaRootResolver.getQuotaRoot(mailbox.generateAssociatedPath()));
        Mono<Long> messageCountPublisher = Mono.fromCallable(() -> messageMapper.countMessagesInMailbox(mailbox));

        return quotaRootPublisher.zipWith(messageCountPublisher).flatMap(quotaRootWithMessageCount -> messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.Metadata, UNLIMITED)
            .map(message -> MetadataWithMailboxId.from(message.metaData(), message.getMailboxId()))
            .collect(Guavate.toImmutableList())
            .flatMap(metadata -> {
                long totalSize = metadata.stream()
                    .map(MetadataWithMailboxId::getMessageMetaData)
                    .mapToLong(MessageMetaData::getSize)
                    .sum();

                return preDeletionHooks.runHooks(PreDeletionHook.DeleteOperation.from(metadata))
                    .then(mailboxMapper.delete(mailbox))
                    .then(eventBus.dispatch(EventFactory.mailboxDeleted()
                            .randomEventId()
                            .mailboxSession(session)
                            .mailbox(mailbox)
                            .quotaRoot(quotaRootWithMessageCount.getT1())
                            .quotaCount(QuotaCountUsage.count(quotaRootWithMessageCount.getT2()))
                            .quotaSize(QuotaSizeUsage.size(totalSize))
                            .build(),
                        new MailboxIdRegistrationKey(mailbox.getMailboxId())));
            })
            .retryWhen(RETRY_BACKOFF_SPEC)
            // We need to create a copy of the mailbox as maybe we can not refer to the real
            // mailbox once we remove it
            .thenReturn(new Mailbox(mailbox)));
    }

    @Override
    public List<MailboxRenamedResult> renameMailbox(MailboxPath from, MailboxPath to, RenameOption option,
                                                    MailboxSession session) throws MailboxException {
        LOGGER.debug("renameMailbox {} to {}", from, to);
        MailboxPath sanitizedMailboxPath = to.sanitize(session.getPathDelimiter());
        validateDestinationPath(sanitizedMailboxPath, session);

        assertIsOwner(session, from);
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mapper.execute(() -> {
            Mailbox mailbox = blockOptional(mapper.findMailboxByPath(from))
                .orElseThrow(() -> new MailboxNotFoundException(from));
            return renameSubscriptionsIfNeeded(
                doRenameMailbox(mailbox, sanitizedMailboxPath, session, mapper), option, session);
        });
    }

    private List<MailboxRenamedResult> renameSubscriptionsIfNeeded(List<MailboxRenamedResult> renamedResults,
                                                                   RenameOption option, MailboxSession session) throws SubscriptionException {
        if (option == RenameOption.RENAME_SUBSCRIPTIONS) {
            SubscriptionMapper subscriptionMapper = mailboxSessionMapperFactory.getSubscriptionMapper(session);
            List<Subscription> subscriptionsForUser = subscriptionMapper.findSubscriptionsForUser(session.getUser());
            renamedResults.forEach(Throwing.<MailboxRenamedResult>consumer(renamedResult -> {
                Subscription subscription = new Subscription(session.getUser(), renamedResult.getOriginPath().getName());
                if (subscriptionsForUser.contains(subscription)) {
                    subscriptionMapper.delete(subscription);
                    subscriptionMapper.save(new Subscription(session.getUser(), renamedResult.getDestinationPath().getName()));
                }
            }).sneakyThrow());
        }
        return renamedResults;
    }

    @Override
    public List<MailboxRenamedResult> renameMailbox(MailboxId mailboxId, MailboxPath newMailboxPath, RenameOption option,
                                                    MailboxSession session) throws MailboxException {
        LOGGER.debug("renameMailbox {} to {}", mailboxId, newMailboxPath);
        MailboxPath sanitizedMailboxPath = newMailboxPath.sanitize(session.getPathDelimiter());
        validateDestinationPath(sanitizedMailboxPath, session);

        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mapper.execute(() -> {
            Mailbox mailbox = mapper.findMailboxById(mailboxId).blockOptional()
                .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
            assertIsOwner(session, mailbox.generateAssociatedPath());
            return renameSubscriptionsIfNeeded(
                doRenameMailbox(mailbox, sanitizedMailboxPath, session, mapper), option, session);
        });
    }

    private void validateDestinationPath(MailboxPath newMailboxPath, MailboxSession session) throws MailboxException {
        if (block(mailboxExists(newMailboxPath, session))) {
            throw new MailboxExistsException(newMailboxPath.toString());
        }
        assertIsOwner(session, newMailboxPath);
        newMailboxPath.assertAcceptable(session.getPathDelimiter());
    }

    private void assertIsOwner(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxNotFoundException {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            LOGGER.info("Mailbox {} does not belong to {}", mailboxPath.asString(), mailboxSession.getUser().asString());
            throw new MailboxNotFoundException(mailboxPath.asString());
        }
    }

    private List<MailboxRenamedResult> doRenameMailbox(Mailbox mailbox, MailboxPath newMailboxPath, MailboxSession session, MailboxMapper mapper) throws MailboxException {
        // TODO put this into a serilizable transaction

        ImmutableList.Builder<MailboxRenamedResult> resultBuilder = ImmutableList.builder();

        MailboxPath from = mailbox.generateAssociatedPath();
        mailbox.setNamespace(newMailboxPath.getNamespace());
        mailbox.setUser(newMailboxPath.getUser());
        mailbox.setName(newMailboxPath.getName());

        block(mapper.rename(mailbox)
            .map(mailboxId -> {
                resultBuilder.add(new MailboxRenamedResult(mailboxId, from, newMailboxPath));
                return mailboxId;
            }).then(eventBus.dispatch(EventFactory.mailboxRenamed()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailboxId(mailbox.getMailboxId())
                    .oldPath(from)
                    .newPath(newMailboxPath)
                    .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId()))));

        // rename submailboxes
        MailboxQuery.UserBound query = MailboxQuery.builder()
            .userAndNamespaceFrom(from)
            .expression(new PrefixedWildcard(from.getName() + getDelimiter()))
            .build()
            .asUserBound();
        locker.executeWithLock(from, (LockAwareExecution<Void>) () -> {
            block(mapper.findMailboxWithPathLike(query)
                .flatMap(sub -> {
                    String subOriginalName = sub.getName();
                    String subNewName = newMailboxPath.getName() + subOriginalName.substring(from.getName().length());
                    MailboxPath fromPath = new MailboxPath(from, subOriginalName);
                    sub.setName(subNewName);
                    return mapper.rename(sub)
                        .map(mailboxId -> {
                            resultBuilder.add(new MailboxRenamedResult(sub.getMailboxId(), fromPath, sub.generateAssociatedPath()));
                            return mailboxId;
                        }).then(eventBus.dispatch(EventFactory.mailboxRenamed()
                                .randomEventId()
                                .mailboxSession(session)
                                .mailboxId(sub.getMailboxId())
                                .oldPath(fromPath)
                                .newPath(sub.generateAssociatedPath())
                                .build(),
                            new MailboxIdRegistrationKey(sub.getMailboxId())))
                        .then(Mono.fromRunnable(() -> LOGGER.debug("Rename mailbox sub-mailbox {} to {}", subOriginalName, subNewName)));
                }, DEFAULT_CONCURRENCY)
                .then());

            return null;

        }, MailboxPathLocker.LockType.Write);
        return resultBuilder.build();
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
    public List<MessageRange> moveMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException {
        StoreMessageManager toMailbox = (StoreMessageManager) getMailbox(to, session);
        StoreMessageManager fromMailbox = (StoreMessageManager) getMailbox(from, session);

        return configuration.getMoveBatcher().batchMessages(set, messageRange -> fromMailbox.moveTo(messageRange, toMailbox, session));
    }

    @Override
    public Flux<MailboxMetaData> search(MailboxQuery expression, MailboxSearchFetchType fetchType, MailboxSession session) {
        Mono<List<Mailbox>> mailboxesMono = searchMailboxes(expression, session, Right.Lookup).collectList();

        return mailboxesMono
            .flatMapMany(mailboxes -> Flux.fromIterable(mailboxes)
                .filter(expression::matches)
                .transform(metadataTransformation(fetchType, session, mailboxes)))
            .sort(MailboxMetaData.COMPARATOR);
    }

    private Function<Flux<Mailbox>, Flux<MailboxMetaData>> metadataTransformation(MailboxSearchFetchType fetchType, MailboxSession session, List<Mailbox> mailboxes) {
        if (fetchType == MailboxSearchFetchType.Counters) {
            return withCounters(session, mailboxes);
        }
        return withoutCounters(session, mailboxes);
    }

    private Function<Flux<Mailbox>, Flux<MailboxMetaData>> withCounters(MailboxSession session, List<Mailbox> mailboxes) {
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(session);
        int concurrency = 4;
        return mailboxFlux -> mailboxFlux
            .flatMap(mailbox -> retrieveCounters(messageMapper, mailbox, session)
                .map(Throwing.<MailboxCounters, MailboxMetaData>function(
                    counters -> toMailboxMetadata(session, mailboxes, mailbox, counters))
                    .sneakyThrow()),
                concurrency);
    }

    private Function<Flux<Mailbox>, Flux<MailboxMetaData>> withoutCounters(MailboxSession session, List<Mailbox> mailboxes) {
        return mailboxFlux -> mailboxFlux
                .map(Throwing.<Mailbox, MailboxMetaData>function(
                    mailbox -> toMailboxMetadata(session, mailboxes, mailbox, MailboxCounters
                        .builder()
                        .mailboxId(mailbox.getMailboxId())
                        .count(0)
                        .unseen(0)
                        .build()))
                    .sneakyThrow());
    }

    private Mono<MailboxCounters> retrieveCounters(MessageMapper messageMapper, Mailbox mailbox, MailboxSession session) {
        return messageMapper.getMailboxCountersReactive(mailbox)
            .filter(Throwing.<MailboxCounters>predicate(counter -> storeRightManager.hasRight(mailbox, Right.Read, session)).sneakyThrow())
            .switchIfEmpty(Mono.just(MailboxCounters
                .builder()
                .mailboxId(mailbox.getMailboxId())
                .count(0)
                .unseen(0)
                .build()));
    }

    private Flux<Mailbox> searchMailboxes(MailboxQuery mailboxQuery, MailboxSession session, Right right) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Flux<Mailbox> baseMailboxes = mailboxMapper
            .findMailboxWithPathLike(toSingleUserQuery(mailboxQuery, session));
        Flux<Mailbox> delegatedMailboxes = getDelegatedMailboxes(mailboxMapper, mailboxQuery, right, session);
        return Flux.concat(baseMailboxes, delegatedMailboxes)
            .distinct()
            .filter(Throwing.predicate(mailbox -> storeRightManager.hasRight(mailbox, right, session)));
    }

    static MailboxQuery.UserBound toSingleUserQuery(MailboxQuery mailboxQuery, MailboxSession mailboxSession) {
        return MailboxQuery.builder()
            .namespace(mailboxQuery.getNamespace().orElse(MailboxConstants.USER_NAMESPACE))
            .username(mailboxQuery.getUser().orElse(mailboxSession.getUser()))
            .expression(mailboxQuery.getMailboxNameExpression()
                .includeChildren())
            .build()
            .asUserBound();
    }

    private Flux<Mailbox> getDelegatedMailboxes(MailboxMapper mailboxMapper, MailboxQuery mailboxQuery,
                                                Right right, MailboxSession session) {
        if (mailboxQuery.isPrivateMailboxes(session)) {
            return Flux.empty();
        }
        return mailboxMapper.findNonPersonalMailboxes(session.getUser(), right);
    }

    private MailboxMetaData toMailboxMetadata(MailboxSession session, List<Mailbox> mailboxes, Mailbox mailbox, MailboxCounters counters) throws UnsupportedRightException {
        return new MailboxMetaData(
            mailbox.generateAssociatedPath(),
            mailbox.getMailboxId(),
            getDelimiter(),
            computeChildren(session, mailboxes, mailbox),
            Selectability.NONE,
            storeRightManager.getResolvedMailboxACL(mailbox, session),
            counters);
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
    public Flux<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException {
        return getInMailboxes(expression, session)
            .filter(id -> !expression.getNotInMailboxes().contains(id.getMailboxId()))
            .filter(mailbox -> expression.getNamespace().keepAccessible(mailbox))
            .map(Mailbox::getMailboxId)
            .collect(Guavate.toImmutableSet())
            .flatMapMany(Throwing.function(ids -> index.search(session, ids, expression.getSearchQuery(), limit)));
    }


    private Flux<Mailbox> getInMailboxes(MultimailboxesSearchQuery expression, MailboxSession session) throws MailboxException {
        if (expression.getInMailboxes().isEmpty()) {
            return searchMailboxes(expression.getNamespace().associatedMailboxSearchQuery(), session, Right.Read);
        } else {
            return filterReadable(expression.getInMailboxes(), session);
        }
    }

    private Flux<Mailbox> getAllReadableMailbox(MailboxQuery mailboxQuery, MailboxSession session) throws MailboxException {
        return searchMailboxes(mailboxQuery, session, Right.Read);
    }

    private Flux<Mailbox> filterReadable(ImmutableSet<MailboxId> inMailboxes, MailboxSession session) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        return Flux.fromIterable(inMailboxes)
            .concatMap(mailboxMapper::findMailboxById)
            .filter(Throwing.<Mailbox>predicate(mailbox -> storeRightManager.hasRight(mailbox, Right.Read, session)).sneakyThrow());
    }

    @Override
    public Mono<Boolean> mailboxExists(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mapper.pathExists(mailboxPath);
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
        return block(mailboxSessionMapperFactory.getMailboxMapper(session)
            .list()
            .map(Mailbox::generateAssociatedPath)
            .distinct()
            .collect(Guavate.toImmutableList()));
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
    public Mono<Rfc4314Rights> myRights(MailboxId mailboxId, MailboxSession session) {
        return storeRightManager.myRights(mailboxId, session);
    }

    @Override
    public Rfc4314Rights myRights(Mailbox mailbox, MailboxSession session) {
        return storeRightManager.myRights(mailbox, session);
    }

    @Override
    public List<Rfc4314Rights> listRights(MailboxPath mailboxPath, MailboxACL.EntryKey key, MailboxSession session) throws MailboxException {
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
    public void applyRightsCommand(MailboxId mailboxId, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        storeRightManager.applyRightsCommand(mailboxId, mailboxACLCommand, session);
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
        return block(mapper.findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> mapper.hasChildren(mailbox, session.getPathDelimiter())));
    }
}
