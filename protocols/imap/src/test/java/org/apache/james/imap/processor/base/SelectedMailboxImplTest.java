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

package org.apache.james.imap.processor.base;

import static jakarta.mail.Flags.Flag.ANSWERED;
import static jakarta.mail.Flags.Flag.FLAGGED;
import static jakarta.mail.Flags.Flag.RECENT;
import static jakarta.mail.Flags.Flag.SEEN;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Registration;
import org.apache.james.imap.processor.base.SelectedMailboxImpl.ApplicableFlags;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.MailboxRenamed;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


class SelectedMailboxImplTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectedMailboxImplTest.class);
    private static final MessageUid EMITTED_EVENT_UID = MessageUid.of(5);
    private static final ModSeq MOD_SEQ = ModSeq.of(12);
    private static final int SIZE = 38;
    private static final String CUSTOM_FLAG = "custom";
    private static final Username BOB = Username.of("bob");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(2);

    private ExecutorService executorService;
    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private MailboxPath mailboxPath;
    private Mailbox mailbox;
    private TestId mailboxId;
    private EventBus eventBus;
    private MailboxIdRegistrationKey mailboxIdRegistrationKey;

    @BeforeEach
    void setUp() throws Exception {
        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        executorService = Executors.newFixedThreadPool(1, threadFactory);
        mailboxPath = MailboxPath.inbox(Username.of("tellier@linagora.com"));
        mailboxManager = mock(MailboxManager.class);
        messageManager = mock(MessageManager.class);
        mailbox = mock(Mailbox.class);
        mailboxId = TestId.of(42);
        mailboxIdRegistrationKey = new MailboxIdRegistrationKey(mailboxId);
        eventBus = mock(EventBus.class);

        when(mailboxManager.getMailbox(eq(mailboxPath), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(messageManager.getApplicableFlagsReactive(any(MailboxSession.class)))
            .thenReturn(Mono.just(new Flags()));
        when(messageManager.search(any(SearchQuery.class), any(MailboxSession.class)))
            .thenReturn(Flux.just(MessageUid.of(1), MessageUid.of(3))
                .delayElements(Duration.ofSeconds(1)));
        when(messageManager.getId()).thenReturn(mailboxId);

        when(mailbox.generateAssociatedPath()).thenReturn(mailboxPath);
        when(mailbox.getMailboxId()).thenReturn(mailboxId);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void concurrentEventShouldNotSkipAddedEventsEmittedDuringInitialisation() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitEventAnswer(successCount))
            .when(eventBus)
            .register(any(EventListener.ReactiveEventListener.class), eq(mailboxIdRegistrationKey));
        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            mock(MailboxSession.class),
            messageManager);
        selectedMailbox.finishInit().block();

        assertThat(selectedMailbox.getLastUid().get()).isEqualTo(EMITTED_EVENT_UID);
    }

    @Test
    void customFlagsEventShouldNotFailWhenConcurrentWithCreation() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitCustomFlagEventAnswer(successCount))
            .when(eventBus)
            .register(any(EventListener.ReactiveEventListener.class), eq(mailboxIdRegistrationKey));

        new SelectedMailboxImpl(mailboxManager, eventBus, mock(MailboxSession.class), messageManager).finishInit().block();

        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void applicableFlagsShouldBeWellUpdatedWhenConcurrentWithCreation() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitCustomFlagEventAnswer(successCount))
            .when(eventBus)
            .register(any(EventListener.ReactiveEventListener.class), eq(mailboxIdRegistrationKey));

        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(mailboxManager, eventBus, mock(MailboxSession.class), messageManager);
        selectedMailbox.finishInit().block();

        assertThat(selectedMailbox.getApplicableFlags().getUserFlags()).containsOnly(CUSTOM_FLAG);
    }

    @Test
    void concurrentEventShouldBeProcessedSuccessfullyDuringInitialisation() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitEventAnswer(successCount))
            .when(eventBus)
            .register(any(EventListener.ReactiveEventListener.class), eq(mailboxIdRegistrationKey));

        new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            mock(MailboxSession.class),
            messageManager).finishInit().block();

        assertThat(successCount.get())
            .as("Get the incremented value in case of successful event processing.")
            .isEqualTo(1);
    }

    Answer<Mono<Registration>> generateEmitEventAnswer(AtomicInteger success) {
        return generateEmitEventAnswer(event(), success);
    }

    Answer<Mono<Registration>> generateEmitCustomFlagEventAnswer(AtomicInteger success) {
        return generateEmitEventAnswer(customFlagEvent(), success);
    }

    Answer<Mono<Registration>> generateEmitEventAnswer(Event event, AtomicInteger success) {
        return invocation -> {
            Object[] args = invocation.getArguments();
            EventListener eventListener = (EventListener) args[0];
            executorService.submit(() -> {
                try {
                    eventListener.event(event);
                    success.incrementAndGet();
                } catch (Exception e) {
                    LOGGER.error("Error while processing event on a concurrent thread", e);
                }
            });
            return Mono.just(Mono::empty);
        };
    }

    Event event() {
        return EventFactory.added()
            .randomEventId()
            .mailboxSession(MailboxSessionUtil.create(Username.of("user")))
            .mailbox(mailbox)
            .addMetaData(new MessageMetaData(EMITTED_EVENT_UID, MOD_SEQ, new Flags(), SIZE, new Date(), Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())))
            .isDelivery(!IS_DELIVERY)
            .isAppended(!IS_APPENDED)
            .build();
    }

    Event customFlagEvent() {
        return EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MailboxSessionUtil.create(Username.of("user")))
            .mailbox(mailbox)
            .updatedFlag(UpdatedFlags.builder()
                .modSeq(ModSeq.of(36))
                .newFlags(new Flags(CUSTOM_FLAG))
                .oldFlags(new Flags())
                .uid(MessageUid.of(12))
                .build())
            .build();
    }

    @Nested
    class ApplicableFlagsTests {
        @Test
        void updateApplicableFlagsShouldNotUpdateWhenEmptyFlagsUpdate() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            FlagsUpdated flagsUpdated = flagsUpdated(updatedFlags().noOldFlag().noNewFlag());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, flagsUpdated);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotUpdateWhenNewFlag() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            FlagsUpdated flagsUpdated =
                flagsUpdated(updatedFlags().noOldFlag().newFlags(flags -> flags.add(ANSWERED)));
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, flagsUpdated);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add(ANSWERED).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotUpdateWhenSeveralUpdatedFlagsNewFlag() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            FlagsUpdated flagsUpdated =
                flagsUpdated(
                    updatedFlags().noOldFlag().newFlags(flags -> flags.add(ANSWERED)),
                    updatedFlags().noOldFlag().newFlags(flags -> flags.add(FLAGGED)));
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, flagsUpdated);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add(ANSWERED).add(FLAGGED).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotUpdateWhenOldFlagRemoved() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            FlagsUpdated flagsUpdated =
                flagsUpdated(updatedFlags().oldFlags(flags -> flags.add(SEEN)).noNewFlag());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, flagsUpdated);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotIncludeRecent() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            FlagsUpdated flagsUpdated =
                flagsUpdated(updatedFlags().noOldFlag().newFlags(flags -> flags.add(RECENT)));
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, flagsUpdated);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldUpdateWhenNewUserFlag() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            FlagsUpdated flagsUpdated =
                flagsUpdated(updatedFlags().noOldFlag().newFlags(flags -> flags.add("Foo")));
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, flagsUpdated);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isTrue();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add("Foo").build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotUpdateWhenAddedWithoutUserFlag() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            Added added = added(flagsBuilder().add(ANSWERED).build());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, added);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add(ANSWERED).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotIncludeRecentWhenAdded() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            Added added = added(flagsBuilder().add(RECENT).build());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, added);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).build());
            });
        }

        @Test
        void updateApplicableFlagsShouldUpdateWhenAddedWithNewUserFlag() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            Added added = added(flagsBuilder().add("Foo").build());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, added);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isTrue();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add("Foo").build());
            });
        }

        @Test
        void updateApplicableFlagsShouldNotUpdateWhenAddedWithAlreadyKnownUserFlag() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).add("Foo").build());
            Added added = added(flagsBuilder().add("Foo").build());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, added);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isFalse();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add("Foo").build());
            });
        }

        @Test
        void updateApplicableFlagsShouldMergeUserFlagsOfAllAddedMessages() {
            ApplicableFlags applicableFlags = ApplicableFlags.from(flagsBuilder().add(SEEN).build());
            Added added = added(flagsBuilder().add("Foo").build(), flagsBuilder().add("Bar").build());
            ApplicableFlags actual = SelectedMailboxImpl.updateApplicableFlags(applicableFlags, added);
            assertThat(actual).satisfies(ap -> {
                assertThat(ap.updated()).isTrue();
                assertThat(ap.flags()).isEqualTo(flagsBuilder().add(SEEN).add("Foo").add("Bar").build());
            });
        }
    }

    private Added added(Flags... flags) {
        return EventFactory.added()
            .randomEventId()
            .mailboxSession(MailboxSessionUtil.create(BOB))
            .mailbox(mailbox)
            .addMetaData(IntStream.range(0, flags.length)
                .mapToObj(i -> new MessageMetaData(MessageUid.of(i + 1), MOD_SEQ, flags[i], SIZE, new Date(),
                    Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())))
                .collect(ImmutableList.toImmutableList()))
            .isDelivery(!IS_DELIVERY)
            .isAppended(IS_APPENDED)
            .build();
    }

    private static FlagsBuilder flagsBuilder() {
        return FlagsBuilder.builder();
    }

    private FlagsUpdated flagsUpdated(UpdatedFlags... updatedFlags) {
        return new FlagsUpdated(
            SESSION_ID,
            BOB,
            mailboxPath,
            mailboxId,
            ImmutableList.copyOf(updatedFlags),
            Event.EventId.random());
    }

    interface RequireOldFlags {
        RequireNewFlags oldFlags(Flags flags);

        default RequireNewFlags noOldFlag() {
            return oldFlags(new Flags());
        }

        default RequireNewFlags oldFlags(Consumer<FlagsBuilder> builder) {
            FlagsBuilder internalBuilder = FlagsBuilder.builder();
            builder.accept(internalBuilder);
            return oldFlags(internalBuilder.build());
        }
    }

    interface RequireNewFlags {
        UpdatedFlags newFlags(Flags flags);

        default UpdatedFlags noNewFlag() {
            return newFlags(new Flags());
        }

        default UpdatedFlags newFlags(Consumer<FlagsBuilder> builder) {
            FlagsBuilder internalBuilder = FlagsBuilder.builder();
            builder.accept(internalBuilder);
            return newFlags(internalBuilder.build());
        }
    }

    static RequireOldFlags updatedFlags() {
        return oldFlags -> newFlags ->
            UpdatedFlags
                .builder()
                .modSeq(MOD_SEQ)
                .uid(EMITTED_EVENT_UID)
                .oldFlags(oldFlags)
                .newFlags(newFlags)
                .build();
    }

    @Test
    void unregisterIdleWithMatchingListenerShouldClearIdleState() {
        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            mock(MailboxSession.class),
            messageManager);

        EventListener.ReactiveEventListener listenerA = mock(EventListener.ReactiveEventListener.class);
        selectedMailbox.registerIdle(listenerA);
        assertThat(selectedMailbox.isIdling()).isTrue();

        selectedMailbox.unregisterIdle(listenerA);
        assertThat(selectedMailbox.isIdling()).isFalse();
    }

    @Test
    void unregisterIdleWithStaleListenerShouldNotClearNewListener() {
        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            mock(MailboxSession.class),
            messageManager);

        EventListener.ReactiveEventListener listenerA = mock(EventListener.ReactiveEventListener.class);
        EventListener.ReactiveEventListener listenerB = mock(EventListener.ReactiveEventListener.class);

        // Register initial listener A, then new listener B arrives
        selectedMailbox.registerIdle(listenerA);
        selectedMailbox.registerIdle(listenerB);

        // Delayed cleanup of A should NOT unregister B
        selectedMailbox.unregisterIdle(listenerA);
        assertThat(selectedMailbox.isIdling()).isTrue();

        // Cleanup of B should successfully unregister
        selectedMailbox.unregisterIdle(listenerB);
        assertThat(selectedMailbox.isIdling()).isFalse();
    }

    @Test
    void updatesShouldNoLongerBeTrackedOnceTheReadRightIsRevoked() throws Exception {
        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            MailboxSessionUtil.create(BOB),
            messageManager);
        MailboxACL sharedWithBob = MailboxACL.EMPTY.apply(MailboxACL.command()
            .forUser(BOB)
            .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read)
            .asAddition());

        Mono.from(selectedMailbox.reactiveEvent(EventFactory.aclUpdated()
            .randomEventId()
            .mailboxSession(MailboxSessionUtil.create(Username.of("tellier@linagora.com")))
            .mailbox(mailbox)
            .aclDiff(ACLDiff.computeDiff(sharedWithBob, MailboxACL.EMPTY))
            .build())).block();
        Mono.from(selectedMailbox.reactiveEvent(event())).block();

        assertThat(selectedMailbox.getLastUid()).isEmpty();
    }

    @Test
    void deselectShouldClearIdleListener() {
        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            mock(MailboxSession.class),
            messageManager);

        EventListener.ReactiveEventListener listener = mock(EventListener.ReactiveEventListener.class);
        selectedMailbox.registerIdle(listener);
        assertThat(selectedMailbox.isIdling()).isTrue();

        selectedMailbox.deselect().block();
        assertThat(selectedMailbox.isIdling()).isFalse();
    }

    @Nested
    class MessageManagerCache {
        private static final Duration TTL = Duration.ofSeconds(30);
        private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

        private UpdatableTickingClock clock;
        private MessageManager refreshed;
        private SelectedMailboxImpl selectedMailbox;

        @BeforeEach
        void setUp() {
            clock = new UpdatableTickingClock(NOW);
            refreshed = mock(MessageManager.class);
            when(mailboxManager.getMailboxReactive(eq(mailboxId), any(MailboxSession.class)))
                .thenReturn(Mono.just(refreshed));
            selectedMailbox = new SelectedMailboxImpl(mailboxManager, eventBus, MailboxSessionUtil.create(BOB), messageManager, TTL, clock);
        }

        @Test
        void zeroTtlShouldDisableTheCache() {
            SelectedMailboxImpl noCache = new SelectedMailboxImpl(mailboxManager, eventBus, MailboxSessionUtil.create(BOB), messageManager,
                Duration.ZERO, clock);

            noCache.messageManager().block();

            assertThat(noCache.messageManager().block()).isEqualTo(refreshed);
            verify(mailboxManager, times(2)).getMailboxReactive(eq(mailboxId), any(MailboxSession.class));
        }

        @Test
        void shouldServeTheSelectedMessageManager() {
            assertThat(selectedMailbox.messageManager().block()).isEqualTo(messageManager);
            verify(mailboxManager, never()).getMailboxReactive(any(MailboxId.class), any(MailboxSession.class));
        }

        @Test
        void shouldReadAgainOnceExpired() {
            clock.setInstant(NOW.plus(TTL));

            assertThat(selectedMailbox.messageManager().block()).isEqualTo(refreshed);
        }

        @Test
        void shouldServeTheMessageManagerReadOnceExpired() {
            clock.setInstant(NOW.plus(TTL));
            selectedMailbox.messageManager().block();

            assertThat(selectedMailbox.messageManager().block()).isEqualTo(refreshed);
            verify(mailboxManager, times(1)).getMailboxReactive(eq(mailboxId), any(MailboxSession.class));
        }

        @Test
        void aclUpdateShouldInvalidate() {
            Mono.from(selectedMailbox.reactiveEvent(EventFactory.aclUpdated()
                .randomEventId()
                .mailboxSession(MailboxSessionUtil.create(Username.of("tellier@linagora.com")))
                .mailbox(mailbox)
                .aclDiff(ACLDiff.computeDiff(MailboxACL.EMPTY, MailboxACL.EMPTY))
                .build())).block();

            assertThat(selectedMailbox.messageManager().block()).isEqualTo(refreshed);
        }

        @Test
        void deletionShouldInvalidate() {
            Mono.from(selectedMailbox.reactiveEvent(deletion())).block();

            assertThat(selectedMailbox.messageManager().block()).isEqualTo(refreshed);
        }

        @Test
        void renameShouldInvalidate() {
            Mono.from(selectedMailbox.reactiveEvent(rename())).block();

            assertThat(selectedMailbox.messageManager().block()).isEqualTo(refreshed);
        }

        @Test
        void aReadConcurrentWithAnInvalidationShouldNotBeCached() {
            MessageManager readDuringRename = mock(MessageManager.class);
            clock.setInstant(NOW.plus(TTL));
            when(mailboxManager.getMailboxReactive(eq(mailboxId), any(MailboxSession.class)))
                .thenReturn(Mono.from(selectedMailbox.reactiveEvent(rename())).thenReturn(readDuringRename))
                .thenReturn(Mono.just(refreshed));

            selectedMailbox.messageManager().block();

            assertThat(selectedMailbox.messageManager().block()).isEqualTo(refreshed);
        }

        private MailboxDeletion deletion() {
            return new MailboxDeletion(SESSION_ID, BOB, mailboxPath, MailboxACL.EMPTY,
                QuotaRoot.quotaRoot("root", Optional.empty()), QuotaCountUsage.count(0), QuotaSizeUsage.size(0),
                mailboxId, Event.EventId.random());
        }

        private MailboxRenamed rename() {
            return new MailboxRenamed(SESSION_ID, BOB, mailboxPath, mailboxId, MailboxPath.forUser(BOB, "other"), Event.EventId.random());
        }
    }
}
