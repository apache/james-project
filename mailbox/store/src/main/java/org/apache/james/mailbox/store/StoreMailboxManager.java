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
import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxAnnotationManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.InboxAlreadyCreated;
import org.apache.james.mailbox.exception.InsufficientRightsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
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
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
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
    private static final int LOW_CONCURRENCY = 2;

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
    private final ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm;
    private final Clock clock;

    @Inject
    public StoreMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, SessionProvider sessionProvider,
                               MailboxPathLocker locker, MessageParser messageParser,
                               Factory messageIdFactory, MailboxAnnotationManager annotationManager,
                               EventBus eventBus, StoreRightManager storeRightManager,
                               QuotaComponents quotaComponents, MessageSearchIndex searchIndex, MailboxManagerConfiguration configuration,
                               PreDeletionHooks preDeletionHooks, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm, Clock clock) {
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
        this.threadIdGuessingAlgorithm = threadIdGuessingAlgorithm;
        this.clock = clock;
    }

    public QuotaComponents getQuotaComponents() {
        return quotaComponents;
    }

    @Override
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

    public ThreadIdGuessingAlgorithm getThreadIdGuessingAlgorithm() {
        return threadIdGuessingAlgorithm;
    }

    public Clock getClock() {
        return clock;
    }

    @Override
    public MailboxSession createSystemSession(Username userName) {
        return sessionProvider.createSystemSession(userName);
    }

    @Override
    public AuthorizationStep authenticate(Username givenUserid, String passwd) {
        return sessionProvider.authenticate(givenUserid, passwd);
    }

    @Override
    public AuthorizationStep authenticate(Username givenUserid) {
        return sessionProvider.authenticate(givenUserid);
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
            getStoreRightManager(), preDeletionHooks, new MessageStorer.WithoutAttachment(mailboxSessionMapperFactory, messageIdFactory, new MessageFactory.StoreMessageFactory(), threadIdGuessingAlgorithm, clock));
    }

    @Override
    public MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(getMailboxReactive(mailboxPath, session));
    }

    @Override
    public Mono<MessageManager> getMailboxReactive(MailboxPath mailboxPath, MailboxSession session) {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mapper.findMailboxByPath(mailboxPath)
            .map(Throwing.<Mailbox, MessageManager>function(mailboxRow -> getMailbox(mailboxRow, session)).sneakyThrow())
            .switchIfEmpty(Mono.fromCallable(() -> {
                LOGGER.debug("Mailbox '{}' not found.", mailboxPath);
                throw new MailboxNotFoundException(mailboxPath);
            }));
    }

    @Override
    public MessageManager getMailbox(Mailbox mailboxRow, MailboxSession session) throws MailboxException {
        MailboxPath mailboxPath = mailboxRow.generateAssociatedPath();
        if (!assertUserHasAccessTo(mailboxRow, session)) {
            LOGGER.info("Mailbox '{}' does not belong to user '{}' but to '{}'", mailboxPath, session.getUser(), mailboxRow.getUser());
            throw new MailboxNotFoundException(mailboxPath);
        }

        LOGGER.debug("Loaded mailbox {}", mailboxPath);

        return createMessageManager(mailboxRow, session);
    }

    @Override
    public MessageManager getMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        return block(getMailboxReactive(mailboxId, session));
    }

    @Override
    public Publisher<MessageManager> getMailboxReactive(MailboxId mailboxId, MailboxSession session) {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mapper.findMailboxById(mailboxId)
            .map(Throwing.<Mailbox, MessageManager>function(mailboxRow -> {
                if (!assertUserHasAccessTo(mailboxRow, session)) {
                    LOGGER.info("Mailbox '{} {}' does not belong to user '{}' but to '{}'", mailboxRow.getMailboxId().serialize(), mailboxRow.generateAssociatedPath(), session.getUser(), mailboxRow.getUser());
                    throw new MailboxNotFoundException(mailboxId);
                }

                LOGGER.debug("Loaded mailbox {} {}", mailboxRow.getMailboxId().serialize(), mailboxRow.generateAssociatedPath());

                return createMessageManager(mailboxRow, session);
            }).sneakyThrow());
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
    public Optional<MailboxId> createMailbox(MailboxPath mailboxPath, CreateOption createOption, MailboxSession mailboxSession) throws MailboxException {
        return MailboxReactorUtils.blockOptional(createMailboxReactive(mailboxPath, createOption, mailboxSession));
    }

    @Override
    public Mono<MailboxId> createMailboxReactive(MailboxPath mailboxPath, CreateOption createOption, MailboxSession mailboxSession) {
        LOGGER.debug("createMailbox {}", mailboxPath);

        return assertMailboxPathBelongToUserReactive(mailboxSession, mailboxPath)
            .then(doCreateMailboxReactive(mailboxPath, mailboxSession))
            .flatMap(any -> createSubscriptionIfNeeded(mailboxPath, createOption, mailboxSession).thenReturn(any));
    }

    private Mono<Void> createSubscriptionIfNeeded(MailboxPath mailboxPath, CreateOption createOption, MailboxSession session) {
        if (createOption.equals(CreateOption.CREATE_SUBSCRIPTION)) {
            return mailboxSessionMapperFactory.getSubscriptionMapper(session)
                .saveReactive(new Subscription(session.getUser(), mailboxPath.asEscapedString()));
        }
        return Mono.empty();
    }

    private Mono<MailboxId> doCreateMailboxReactive(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        if (mailboxPath.getName().isEmpty()) {
            LOGGER.warn("Ignoring mailbox with empty name");
            return Mono.empty();
        } else {
            try {
                MailboxPath sanitizedMailboxPath = mailboxPath.sanitize(mailboxSession.getPathDelimiter());
                sanitizedMailboxPath.assertAcceptable(mailboxSession.getPathDelimiter());

                return mailboxExists(sanitizedMailboxPath, mailboxSession)
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new MailboxExistsException(sanitizedMailboxPath.asString()));
                        } else {
                            return createMailboxesForPath(mailboxSession, sanitizedMailboxPath).takeLast(1).next();
                        }
                    })
                    .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .modifyErrorFilter(old -> old.and(e -> !(e instanceof MailboxException)))
                        .jitter(0.5)
                        .maxBackoff(Duration.ofSeconds(1)));
            } catch (MailboxNameException e) {
                return Mono.error(e);
            }
        }
    }

    private Flux<MailboxId> createMailboxesForPath(MailboxSession mailboxSession, MailboxPath sanitizedMailboxPath) {
        // Create parents first
        // If any creation fails then the mailbox will not be created
        // TODO: transaction
        List<MailboxPath> intermediatePaths = sanitizedMailboxPath.getHierarchyLevels(mailboxSession.getPathDelimiter());
        boolean isRootPath = intermediatePaths.size() == 1;

        return Flux.fromIterable(intermediatePaths)
            .concatMap(path -> manageMailboxCreation(mailboxSession, isRootPath, path));
    }

    private Mono<MailboxId> manageMailboxCreation(MailboxSession mailboxSession, boolean isRootPath, MailboxPath mailboxPath)  {
        if (mailboxPath.isInbox()) {
            return Mono.from(hasInbox(mailboxSession))
                .flatMap(hasInbox -> {
                    if (hasInbox) {
                        return duplicatedINBOXCreation(isRootPath, mailboxPath);
                    }
                    return performConcurrentMailboxCreation(mailboxSession, MailboxPath.inbox(mailboxSession));
                });
        }

        return performConcurrentMailboxCreation(mailboxSession, mailboxPath);
    }


    private Mono<MailboxId> duplicatedINBOXCreation(boolean isRootPath, MailboxPath mailbox) {
        if (isRootPath) {
            return Mono.error(new InboxAlreadyCreated(mailbox.getName()));
        }

        return Mono.empty();
    }

    private Mono<MailboxId> performConcurrentMailboxCreation(MailboxSession mailboxSession, MailboxPath mailboxPath) {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        return Mono.from(locker.executeReactiveWithLockReactive(mailboxPath,
            mapper.executeReactive(mapper.create(mailboxPath, UidValidity.generate())
                .flatMap(mailbox ->
                    // notify listeners
                    eventBus.dispatch(EventFactory.mailboxAdded()
                            .randomEventId()
                            .mailboxSession(mailboxSession)
                            .mailbox(mailbox)
                            .build(),
                        new MailboxIdRegistrationKey(mailbox.getMailboxId()))
                        .thenReturn(mailbox.getMailboxId()))
                .onErrorResume(MailboxExistsException.class, e -> {
                    LOGGER.info("{} mailbox was created concurrently", mailboxPath.asString());
                    return Mono.empty();
                })), MailboxPathLocker.LockType.Write));
    }

    private Mono<Void> assertMailboxPathBelongToUserReactive(MailboxSession mailboxSession, MailboxPath mailboxPath) {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            return Mono.error(new InsufficientRightsException("mailboxPath '" + mailboxPath.asString() + "'"
                + " does not belong to user '" + mailboxSession.getUser().asString() + "'"));
        }
        return Mono.empty();
    }

    @Override
    public void deleteMailbox(final MailboxPath mailboxPath, final MailboxSession session) throws MailboxException {
        LOGGER.info("deleteMailbox {}", mailboxPath);
        assertIsOwner(session, mailboxPath);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        mailboxMapper.execute(() -> block(mailboxMapper.findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> doDeleteMailbox(mailboxMapper, mailbox, session))
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxPath)))));
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

    @Override
    public Mono<Mailbox> deleteMailboxReactive(MailboxId mailboxId, MailboxSession session) {
        LOGGER.info("deleteMailbox {}", mailboxId);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mailboxMapper.executeReactive(mailboxMapper.findMailboxById(mailboxId)
            .map(Throwing.<Mailbox, Mailbox>function(mailbox -> {
                assertIsOwner(session, mailbox.generateAssociatedPath());
                return mailbox;
            }).sneakyThrow())
            .flatMap(mailbox -> doDeleteMailbox(mailboxMapper, mailbox, session)));
    }

    @Override
    public Mono<Void> deleteMailboxReactive(MailboxPath mailboxPath, MailboxSession session) {
        LOGGER.info("deleteMailbox {}", mailboxPath);
        if (!mailboxPath.belongsTo(session)) {
            LOGGER.info("Mailbox {} does not belong to {}", mailboxPath.asString(), session.getUser().asString());
            return Mono.error(new MailboxNotFoundException(mailboxPath.asString()));
        }
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return mailboxMapper.executeReactive(mailboxMapper.findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> doDeleteMailbox(mailboxMapper, mailbox, session))
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxPath))))
            .then();
    }

    private Mono<Mailbox> doDeleteMailbox(MailboxMapper mailboxMapper, Mailbox mailbox, MailboxSession session) {
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(session);

        Mono<QuotaRoot> quotaRootPublisher = Mono.fromCallable(() -> quotaRootResolver.getQuotaRoot(mailbox.generateAssociatedPath()));
        Mono<Long> messageCountPublisher = Mono.from(messageMapper.getMailboxCountersReactive(mailbox))
            .map(MailboxCounters::getCount);

        return quotaRootPublisher.zipWith(messageCountPublisher).flatMap(quotaRootWithMessageCount -> messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED)
            .map(message -> MetadataWithMailboxId.from(message.metaData(), message.getMailboxId()))
            .collect(ImmutableList.toImmutableList())
            .flatMap(metadata -> {
                long totalSize = metadata.stream()
                    .mapToLong(MetadataWithMailboxId::getSize)
                    .sum();

                return preDeletionHooks.runHooks(PreDeletionHook.DeleteOperation.from(metadata))
                    .then(mailboxMapper.delete(mailbox))
                    .then(eventBus.dispatch(EventFactory.mailboxDeleted()
                            .randomEventId()
                            .mailboxSession(session)
                            .mailbox(mailbox)
                            .quotaRoot(quotaRootWithMessageCount.getT1())
                            .mailboxACL(mailbox.getACL())
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
        return MailboxReactorUtils.block(renameMailboxReactive(from, to, option, session));
    }

    @Override
    public Mono<List<MailboxRenamedResult>> renameMailboxReactive(MailboxPath from, MailboxPath to, RenameOption option, MailboxSession session) {
        return renameMailboxReactive(from, to, option, session, session);
    }

    public Mono<List<MailboxRenamedResult>> renameMailboxReactive(MailboxPath from, MailboxPath to, RenameOption option,
                                                                  MailboxSession fromSession, MailboxSession toSession) {
        LOGGER.debug("renameMailbox {} to {}", from, to);
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(fromSession);

        return sanitizedPath(from, to, fromSession, toSession)
        .flatMap(sanitizedPath -> mapper.executeReactive(
            mapper.findMailboxByPath(from)
                .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(from)))
                .flatMap(mailbox -> doRenameMailbox(mailbox, sanitizedPath, fromSession, toSession, mapper)
                    .flatMap(renamedResults -> renameSubscriptionsIfNeeded(renamedResults, option, fromSession, toSession)))));
    }

    private Mono<MailboxPath> sanitizedPath(MailboxPath from, MailboxPath to, MailboxSession fromSession, MailboxSession toSession) {
        MailboxPath sanitizedMailboxPath = to.sanitize(toSession.getPathDelimiter());

        return validateDestinationPath(sanitizedMailboxPath, toSession)
            .then(Mono.fromRunnable(Throwing.runnable(() -> assertIsOwner(fromSession, from))))
            .thenReturn(sanitizedMailboxPath);
    }

    private Mono<MailboxPath> sanitizedPath(MailboxPath to, MailboxSession session) {
        MailboxPath sanitizedMailboxPath = to.sanitize(session.getPathDelimiter());

        return validateDestinationPath(sanitizedMailboxPath, session)
            .thenReturn(sanitizedMailboxPath);
    }

    private Mono<List<MailboxRenamedResult>> renameSubscriptionsIfNeeded(List<MailboxRenamedResult> renamedResults,
                                                                   RenameOption option, MailboxSession session) {
        return renameSubscriptionsIfNeeded(renamedResults, option, session, session);
    }

    private Mono<List<MailboxRenamedResult>> renameSubscriptionsIfNeeded(List<MailboxRenamedResult> renamedResults,
                                                                   RenameOption option, MailboxSession fromSession, MailboxSession toSession) {
        if (option == RenameOption.RENAME_SUBSCRIPTIONS) {
            SubscriptionMapper subscriptionMapper = mailboxSessionMapperFactory.getSubscriptionMapper(fromSession);

            return subscriptionMapper.findSubscriptionsForUserReactive(fromSession.getUser())
                .collectList()
                .flatMap(subscriptions -> Flux.fromIterable(renamedResults)
                    .flatMap(renamedResult -> {
                        Subscription legacySubscription = new Subscription(fromSession.getUser(), renamedResult.getOriginPath().getName());
                        if (subscriptions.contains(legacySubscription)) {
                            return subscriptionMapper.deleteReactive(legacySubscription)
                                .then(subscriptionMapper.saveReactive(new Subscription(toSession.getUser(), renamedResult.getDestinationPath().asEscapedString())));
                        }
                        Subscription subscription = new Subscription(fromSession.getUser(), renamedResult.getOriginPath().asEscapedString());
                        if (subscriptions.contains(subscription)) {
                            return subscriptionMapper.deleteReactive(subscription)
                                .then(subscriptionMapper.saveReactive(new Subscription(toSession.getUser(), renamedResult.getDestinationPath().asEscapedString())));
                        }
                        return Mono.empty();
                    })
                    .then()
                    .thenReturn(renamedResults));
        }
        return Mono.just(renamedResults);
    }

    @Override
    public List<MailboxRenamedResult> renameMailbox(MailboxId mailboxId, MailboxPath newMailboxPath, RenameOption option,
                                                    MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(renameMailboxReactive(mailboxId, newMailboxPath, option, session));
    }

    @Override
    public Mono<List<MailboxRenamedResult>> renameMailboxReactive(MailboxId mailboxId, MailboxPath newMailboxPath, RenameOption option,
                                                    MailboxSession session) {
        LOGGER.debug("renameMailbox {} to {}", mailboxId, newMailboxPath);
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);

        return sanitizedPath(newMailboxPath, session)
            .flatMap(sanitizedPath -> mapper.executeReactive(
                mapper.findMailboxById(mailboxId)
                    .doOnNext(Throwing.<Mailbox>consumer(mailbox -> assertIsOwner(session, mailbox.generateAssociatedPath())).sneakyThrow())
                    .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxId)))
                    .flatMap(mailbox -> doRenameMailbox(mailbox, sanitizedPath, session, session, mapper)
                        .flatMap(renamedResults -> renameSubscriptionsIfNeeded(renamedResults, option, session)))));
    }

    private Mono<Void> validateDestinationPath(MailboxPath newMailboxPath, MailboxSession session) {
        return mailboxExists(newMailboxPath, session)
            .handle(Throwing.<Boolean, SynchronousSink<Void>>biConsumer((exists, sink) -> {
                if (exists) {
                    sink.error(new MailboxExistsException(newMailboxPath.toString()));
                }
                assertIsOwner(session, newMailboxPath);
                newMailboxPath.assertAcceptable(session.getPathDelimiter());
            }).sneakyThrow());
    }

    private void assertIsOwner(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxNotFoundException {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            LOGGER.info("Mailbox {} does not belong to {}", mailboxPath.asString(), mailboxSession.getUser().asString());
            throw new MailboxNotFoundException(mailboxPath.asString());
        }
    }

    private Mono<List<MailboxRenamedResult>> doRenameMailbox(Mailbox mailbox, MailboxPath newMailboxPath, MailboxSession fromSession, MailboxSession toSession, MailboxMapper mapper) {
        // TODO put this into a serilizable transaction

        ImmutableList.Builder<MailboxRenamedResult> resultBuilder = ImmutableList.builder();

        MailboxPath from = mailbox.generateAssociatedPath();
        mailbox.setNamespace(newMailboxPath.getNamespace());
        mailbox.setUser(newMailboxPath.getUser());
        mailbox.setName(newMailboxPath.getName());
        // Find submailboxes
        MailboxQuery.UserBound query = MailboxQuery.builder()
            .userAndNamespaceFrom(from)
            .expression(new PrefixedWildcard(from.getName() + fromSession.getPathDelimiter()))
            .build()
            .asUserBound();

        return mapper.rename(mailbox)
            .map(mailboxId -> {
                resultBuilder.add(new MailboxRenamedResult(mailboxId, from, newMailboxPath));
                return mailboxId;
            })
            .then(Mono.from(locker.executeReactiveWithLockReactive(from, mapper.findMailboxWithPathLike(query)
                    .flatMap(sub -> {
                        String subOriginalName = sub.getName();
                        String subNewName = newMailboxPath.getName() + subOriginalName.substring(from.getName().length());
                        MailboxPath fromPath = new MailboxPath(from, subOriginalName);
                        sub.setName(subNewName);
                        sub.setUser(toSession.getUser());
                        return mapper.rename(sub)
                            .map(mailboxId -> {
                                resultBuilder.add(new MailboxRenamedResult(sub.getMailboxId(), fromPath, sub.generateAssociatedPath()));
                                return mailboxId;
                            })
                            .retryWhen(Retry.backoff(5, Duration.ofMillis(10)))
                            .then(Mono.fromRunnable(() -> LOGGER.debug("Rename mailbox sub-mailbox {} to {}", subOriginalName, subNewName)));
                    }, LOW_CONCURRENCY)
                    .then(), MailboxPathLocker.LockType.Write)))
            .then(Mono.defer(() -> Flux.fromIterable(resultBuilder.build())
                .concatMap(result -> eventBus.dispatch(EventFactory.mailboxRenamed()
                        .randomEventId()
                        .mailboxSession(fromSession)
                        .mailboxId(result.getMailboxId())
                        .oldPath(result.getOriginPath())
                        .newPath(result.getDestinationPath())
                        .build(),
                    new MailboxIdRegistrationKey(result.getMailboxId())))
                .then()))
            .then(Mono.fromCallable(resultBuilder::build));
    }

    @Override
    public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(copyMessagesReactive(set, from, to, session).collectList());
    }

    @Override
    public List<MessageRange> copyMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(copyMessagesReactive(set, from, to, session).collectList());
    }

    @Override
    public Flux<MessageRange> copyMessagesReactive(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) {
        return Mono.zip(Mono.from(getMailboxReactive(from, session)), Mono.from(getMailboxReactive(to, session)))
            .flatMapMany(fromTo -> configuration.getMoveBatcher().batchMessagesReactive(set, messageRange -> {
                StoreMessageManager fromMessageManager = (StoreMessageManager) fromTo.getT1();
                StoreMessageManager toMessageManager = (StoreMessageManager) fromTo.getT2();

                return fromMessageManager.copyTo(messageRange, toMessageManager, session).flatMapIterable(Function.identity());
            }));
    }

    @Override
    public Flux<MessageRange> copyMessagesReactive(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) {
        return Mono.zip(Mono.from(getMailboxReactive(from, session)), Mono.from(getMailboxReactive(to, session)))
            .flatMapMany(fromTo -> configuration.getMoveBatcher().batchMessagesReactive(set, messageRange -> {
                StoreMessageManager fromMessageManager = (StoreMessageManager) fromTo.getT1();
                StoreMessageManager toMessageManager = (StoreMessageManager) fromTo.getT2();

                return fromMessageManager.copyTo(messageRange, toMessageManager, session).flatMapIterable(Function.identity());
            }));
    }

    @Override
    public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(moveMessagesReactive(set, from, to, session).collectList());
    }

    @Override
    public List<MessageRange> moveMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(moveMessagesReactive(set, from, to, session).collectList());
    }

    @Override
    public Flux<MessageRange> moveMessagesReactive(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) {
        return Mono.zip(Mono.from(getMailboxReactive(from, session)), Mono.from(getMailboxReactive(to, session)))
            .flatMapMany(fromTo -> configuration.getMoveBatcher().batchMessagesReactive(set, messageRange -> {
                StoreMessageManager fromMessageManager = (StoreMessageManager) fromTo.getT1();
                StoreMessageManager toMessageManager = (StoreMessageManager) fromTo.getT2();

                return fromMessageManager.moveTo(messageRange, toMessageManager, session).flatMapIterable(Function.identity());
            }));
    }

    @Override
    public Flux<MessageRange> moveMessagesReactive(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) {
        return Mono.zip(Mono.from(getMailboxReactive(from, session)), Mono.from(getMailboxReactive(to, session)))
            .flatMapMany(fromTo -> configuration.getMoveBatcher().batchMessagesReactive(set, messageRange -> {
                StoreMessageManager fromMessageManager = (StoreMessageManager) fromTo.getT1();
                StoreMessageManager toMessageManager = (StoreMessageManager) fromTo.getT2();

                return fromMessageManager.moveTo(messageRange, toMessageManager, session).flatMapIterable(Function.identity());
            }));
    }

    @Override
    public Flux<MailboxMetaData> search(MailboxQuery expression, MailboxSearchFetchType fetchType, MailboxSession session) {
        Mono<List<Mailbox>> mailboxesMono = searchMailboxes(expression, session, Right.Lookup).collectList();

        return mailboxesMono
            .publishOn(Schedulers.parallel())
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
        Map<MailboxPath, Boolean> parentMap = parentMap(mailboxes, session);
        int concurrency = 4;
        return mailboxFlux -> mailboxFlux
            .flatMap(mailbox -> retrieveCounters(messageMapper, mailbox, session)
                .map(Throwing.<MailboxCounters, MailboxMetaData>function(
                    counters -> toMailboxMetadata(session, parentMap, mailbox, counters))
                    .sneakyThrow()),
                concurrency);
    }

    private Map<MailboxPath, Boolean> parentMap(List<Mailbox> mailboxes, MailboxSession session) {
        return mailboxes.stream()
            .flatMap(mailbox -> mailbox.generateAssociatedPath()
                .getParents(session.getPathDelimiter())
                .stream())
            .collect(ImmutableMap.toImmutableMap(
                Function.identity(),
                any -> true,
                (a, b) -> true));
    }

    private Function<Flux<Mailbox>, Flux<MailboxMetaData>> withoutCounters(MailboxSession session, List<Mailbox> mailboxes) {
        Map<MailboxPath, Boolean> parentMap = parentMap(mailboxes, session);
        return mailboxFlux -> mailboxFlux
                .map(Throwing.<Mailbox, MailboxMetaData>function(
                    mailbox -> toMailboxMetadata(session, parentMap, mailbox, MailboxCounters.empty(mailbox.getMailboxId())))
                    .sneakyThrow());
    }

    private Mono<MailboxCounters> retrieveCounters(MessageMapper messageMapper, Mailbox mailbox, MailboxSession session) {
        return messageMapper.getMailboxCountersReactive(mailbox)
            .filter(Throwing.<MailboxCounters>predicate(counter -> storeRightManager.hasRight(mailbox, Right.Read, session)).sneakyThrow())
            .switchIfEmpty(Mono.just(MailboxCounters.empty(mailbox.getMailboxId())));
    }

    private Flux<Mailbox> searchMailboxes(MailboxQuery mailboxQuery, MailboxSession session, Right right) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Flux<Mailbox> baseMailboxes = mailboxMapper
            .findMailboxWithPathLike(toSingleUserQuery(mailboxQuery, session));
        Flux<Mailbox> delegatedMailboxes = getDelegatedMailboxes(mailboxMapper, mailboxQuery, right, session)
            .filter(Throwing.predicate(mailbox -> storeRightManager.hasRight(mailbox, right, session)))
            .filter(mailbox -> !mailbox.getUser().equals(session.getUser()));
        return Flux.concat(baseMailboxes, delegatedMailboxes);
    }

    private Flux<MailboxId> accessibleMailboxIds(MultimailboxesSearchQuery.Namespace namespace, Right right, MailboxSession session) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Flux<MailboxId> baseMailboxes = mailboxMapper
            .userMailboxes(session.getUser());
        Flux<MailboxId> delegatedMailboxes = getDelegatedMailboxes(mailboxMapper, namespace, right, session);
        return Flux.concat(baseMailboxes, delegatedMailboxes);
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

    private Flux<MailboxId> getDelegatedMailboxes(MailboxMapper mailboxMapper, MultimailboxesSearchQuery.Namespace namespace,
                                                Right right, MailboxSession session) {
        if (!namespace.accessDelegatedMailboxes()) {
            return Flux.empty();
        }
        return mailboxMapper.findNonPersonalMailboxes(session.getUser(), right)
            .filter(mailbox -> !mailbox.getUser().equals(session.getUser()))
            .map(Mailbox::getMailboxId);
    }

    private MailboxMetaData toMailboxMetadata(MailboxSession session, Map<MailboxPath, Boolean> parentMap, Mailbox mailbox, MailboxCounters counters) throws UnsupportedRightException {
        return new MailboxMetaData(
            mailbox,
            session.getPathDelimiter(),
            computeChildren(parentMap, mailbox),
            Selectability.NONE,
            storeRightManager.getResolvedMailboxACL(mailbox, session),
            counters);
    }

    private MailboxMetaData.Children computeChildren(Map<MailboxPath, Boolean> parentMap, Mailbox mailbox) {
        if (parentMap.getOrDefault(mailbox.generateAssociatedPath(), false)) {
            return MailboxMetaData.Children.HAS_CHILDREN;
        } else {
            return MailboxMetaData.Children.HAS_NO_CHILDREN;
        }
    }

    @Override
    public Flux<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) {
        return getInMailboxIds(expression, session)
            .filter(id -> !expression.getNotInMailboxes().contains(id))
            .collect(ImmutableSet.toImmutableSet())
            .flatMapMany(Throwing.function(ids -> index.search(session, ids, expression.getSearchQuery(), limit)));
    }

    @Override
    public Flux<MessageId> getThread(ThreadId threadId, MailboxSession session) {
        return threadIdGuessingAlgorithm.getMessageIdsInThread(threadId, session);
    }

    private Flux<MailboxId> getInMailboxIds(MultimailboxesSearchQuery expression, MailboxSession session) {
        if (expression.getInMailboxes().isEmpty()) {
            return accessibleMailboxIds(expression.getNamespace(), Right.Read, session);
        } else {
            return filterReadable(expression.getInMailboxes(), session)
                .filter(mailbox -> expression.getNamespace().keepAccessible(mailbox))
                .map(Mailbox::getMailboxId);
        }
    }

    private Flux<Mailbox> filterReadable(ImmutableSet<MailboxId> inMailboxes, MailboxSession session) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        return Flux.fromIterable(inMailboxes)
            .concatMap(mailboxMapper::findMailboxById)
            .filter(Throwing.<Mailbox>predicate(mailbox -> storeRightManager.hasRight(mailbox, Right.Read, session)).sneakyThrow());
    }

    @Override
    public Mono<Boolean> mailboxExists(MailboxPath mailboxPath, MailboxSession session) {
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
            .collect(ImmutableList.toImmutableList()));
    }

    @Override
    public boolean hasRight(MailboxPath mailboxPath, Right right, MailboxSession session) throws MailboxException {
        return storeRightManager.hasRight(mailboxPath, right, session);
    }

    @Override
    public boolean hasRight(Mailbox mailbox, Right right, MailboxSession session) throws MailboxException {
        return storeRightManager.hasRight(mailbox, right, session);
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
    public List<Rfc4314Rights> listRights(Mailbox mailbox, MailboxACL.EntryKey identifier, MailboxSession session) throws MailboxException {
        return storeRightManager.listRights(mailbox, identifier, session);
    }

    @Override
    public MailboxACL listRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return storeRightManager.listRights(mailboxPath, session);
    }

    @Override
    public MailboxACL listRights(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        return storeRightManager.listRights(mailboxId, session);
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

    @Override
    public Publisher<Boolean> hasChildrenReactive(MailboxPath mailboxPath, MailboxSession session) {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        return mapper.findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> mapper.hasChildren(mailbox, session.getPathDelimiter()));
    }
}
