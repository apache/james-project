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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.mail.Flags;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxManager.MailboxCapabilities;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.mock.DataProvisioner;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

/**
 * Test the {@link MailboxManager} methods that
 * are not covered by the protocol-tester suite.
 * 
 * This class needs to be extended by the different mailbox 
 * implementations which are responsible to setup and 
 * implement the test methods.
 * 
 */
public abstract class MailboxManagerTest<T extends MailboxManager> {
    public static final String USER_1 = "USER_1";
    public static final String USER_2 = "USER_2";
    private static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private T mailboxManager;
    private MailboxSession session;
    private Message.Builder message;

    private PreDeletionHook preDeletionHook1;
    private PreDeletionHook preDeletionHook2;

    protected abstract T provideMailboxManager();

    protected abstract EventBus retrieveEventBus(T mailboxManager);

    protected Set<PreDeletionHook> preDeletionHooks() {
        return ImmutableSet.of(preDeletionHook1, preDeletionHook2);
    }

    @BeforeEach
    void setUp() throws Exception {
        setupMockForPreDeletionHooks();
        this.mailboxManager = provideMailboxManager();

        this.message = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);
    }

    private void setupMockForPreDeletionHooks() {
        preDeletionHook1 = mock(PreDeletionHook.class);
        when(preDeletionHook1.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenReturn(Mono.empty());

        preDeletionHook2 = mock(PreDeletionHook.class);
        when(preDeletionHook2.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
            .thenReturn(Mono.empty());
    }

    @AfterEach
    void tearDown() throws Exception {
        mailboxManager.logout(session, false);
        mailboxManager.endProcessingRequest(session);
    }

    @Test
    public void creatingConcurrentlyMailboxesWithSameParentShouldNotFail() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USER_1);
        String mailboxName = "a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z";

        ConcurrentTestRunner.builder()
            .operation((a, b) -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName + a), session))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    public void createMailboxShouldReturnRightId() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "name.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
        MessageManager retrievedMailbox = mailboxManager.getMailbox(mailboxPath, session);

        assertThat(mailboxId.isPresent()).isTrue();
        assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
    }

    @Nested
    class MailboxNameLimitTests {
        @Test
        void creatingMailboxShouldNotFailWhenLimitNameLength() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName = Strings.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH);

            assertThatCode(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session))
                .doesNotThrowAnyException();
        }

        @Test
        void renamingMailboxShouldNotFailWhenLimitNameLength() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName = Strings.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH);

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatCode(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session))
                .doesNotThrowAnyException();
        }

        @Test
        void creatingMailboxShouldThrowWhenOverLimitNameLength() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName = Strings.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH + 1);

            assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(TooLongMailboxNameException.class);
        }

        @Test
        void renamingMailboxShouldThrowWhenOverLimitNameLength() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName = Strings.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH + 1);

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(TooLongMailboxNameException.class);
        }
    }

    @Nested
    class AnnotationTests {
        private final MailboxAnnotationKey privateKey = new MailboxAnnotationKey("/private/comment");
        private final MailboxAnnotationKey privateChildKey = new MailboxAnnotationKey("/private/comment/user");
        private final MailboxAnnotationKey privateGrandchildKey = new MailboxAnnotationKey("/private/comment/user/name");
        private final MailboxAnnotationKey sharedKey = new MailboxAnnotationKey("/shared/comment");

        private final MailboxAnnotation privateAnnotation = MailboxAnnotation.newInstance(privateKey, "My private comment");
        private final MailboxAnnotation privateChildAnnotation = MailboxAnnotation.newInstance(privateChildKey, "My private comment");
        private final MailboxAnnotation privateGrandchildAnnotation = MailboxAnnotation.newInstance(privateGrandchildKey, "My private comment");
        private final MailboxAnnotation privateAnnotationUpdate = MailboxAnnotation.newInstance(privateKey, "My updated private comment");
        private final MailboxAnnotation sharedAnnotation =  MailboxAnnotation.newInstance(sharedKey, "My shared comment");

        private final List<MailboxAnnotation> annotations = ImmutableList.of(privateAnnotation, sharedAnnotation);

        @Test
        void updateAnnotationsShouldUpdateStoredAnnotation() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotation));

            mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotationUpdate));
            assertThat(mailboxManager.getAllAnnotations(inbox, session)).containsOnly(privateAnnotationUpdate);
        }

        @Test
        void updateAnnotationsShouldDeleteAnnotationWithNilValue() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotation));

            mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(MailboxAnnotation.nil(privateKey)));
            assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEmpty();
        }

        @Test
        void updateAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);

            assertThatThrownBy(() -> mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotation)))
                .isInstanceOf(MailboxException.class);
        }

        @Test
        void getAnnotationsShouldReturnEmptyForNonStoredAnnotation() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEmpty();
        }

        @Test
        void getAllAnnotationsShouldRetrieveStoredAnnotations() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            mailboxManager.updateAnnotations(inbox, session, annotations);

            assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEqualTo(annotations);
        }

        @Test
        void getAllAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);

            assertThatThrownBy(() -> mailboxManager.getAllAnnotations(inbox, session))
                .isInstanceOf(MailboxException.class);
        }

        @Test
        void getAnnotationsByKeysShouldRetrieveStoresAnnotationsByKeys() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            mailboxManager.updateAnnotations(inbox, session, annotations);

            assertThat(mailboxManager.getAnnotationsByKeys(inbox, session, ImmutableSet.of(privateKey)))
                .containsOnly(privateAnnotation);
        }

        @Test
        void getAnnotationsByKeysShouldThrowExceptionIfMailboxDoesNotExist() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);

            assertThatThrownBy(() -> mailboxManager.getAnnotationsByKeys(inbox, session, ImmutableSet.of(privateKey)))
                .isInstanceOf(MailboxException.class);
        }

        @Test
        void getAnnotationsByKeysWithOneDepthShouldRetriveAnnotationsWithOneDepth() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotation, privateChildAnnotation, privateGrandchildAnnotation));

            assertThat(mailboxManager.getAnnotationsByKeysWithOneDepth(inbox, session, ImmutableSet.of(privateKey)))
                .contains(privateAnnotation, privateChildAnnotation);
        }

        @Test
        void getAnnotationsByKeysWithAllDepthShouldThrowExceptionWhenMailboxDoesNotExist() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);

            assertThatThrownBy(() -> mailboxManager.getAnnotationsByKeysWithAllDepth(inbox, session, ImmutableSet.of(privateKey)))
                .isInstanceOf(MailboxException.class);
        }

        @Test
        void getAnnotationsByKeysWithAllDepthShouldRetriveAnnotationsWithAllDepth() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotation, privateChildAnnotation, privateGrandchildAnnotation));

            assertThat(mailboxManager.getAnnotationsByKeysWithAllDepth(inbox, session, ImmutableSet.of(privateKey)))
                .contains(privateAnnotation, privateChildAnnotation, privateGrandchildAnnotation);
        }

        @Test
        void updateAnnotationsShouldThrowExceptionIfAnnotationDataIsOverLimitation() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThatThrownBy(() -> mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(MailboxAnnotation.newInstance(privateKey, "The limitation of data is less than 30"))))
                .isInstanceOf(AnnotationException.class);
        }

        @Test
        void shouldUpdateAnnotationWhenRequestCreatesNewAndMailboxIsNotOverLimit() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            ImmutableList.Builder<MailboxAnnotation> builder = ImmutableList.builder();
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment1"), "AnyValue"));
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment2"), "AnyValue"));
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment3"), "AnyValue"));

            mailboxManager.updateAnnotations(inbox, session, builder.build());
        }

        @Test
        void updateAnnotationsShouldThrowExceptionIfRequestCreateNewButMailboxIsOverLimit() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            ImmutableList.Builder<MailboxAnnotation> builder = ImmutableList.builder();
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment1"), "AnyValue"));
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment2"), "AnyValue"));
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment3"), "AnyValue"));
            builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment4"), "AnyValue"));

            assertThatThrownBy(() -> mailboxManager.updateAnnotations(inbox, session, builder.build()))
                .isInstanceOf(MailboxException.class);
        }
    }

    @Nested
    class EventTests {
        private final QuotaRoot quotaRoot = QuotaRoot.quotaRoot("#private&USER_1", Optional.empty());
        private EventCollector listener;
        private MailboxPath inbox;
        private MailboxId inboxId;
        private MessageManager inboxManager;
        private MailboxPath newPath;

        @BeforeEach
        void setUp() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            inbox = MailboxPath.inbox(session);
            newPath = MailboxPath.forUser(USER_1, "specialMailbox");

            listener = new EventCollector();
            inboxId = mailboxManager.createMailbox(inbox, session).get();
            inboxManager = mailboxManager.getMailbox(inbox, session);
        }

        @Test
        void deleteMailboxShouldFireMailboxDeletionEvent() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Quota));
            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.MailboxDeletion)
                .hasSize(1)
                .extracting(event -> (MailboxListener.MailboxDeletion) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getQuotaRoot()).isEqualTo(quotaRoot))
                .satisfies(event -> assertThat(event.getDeletedMessageCount()).isEqualTo(QuotaCount.count(0)))
                .satisfies(event -> assertThat(event.getTotalDeletedSize()).isEqualTo(QuotaSize.size(0)));
        }

        @Test
        void createMailboxShouldFireMailboxAddedEvent() throws Exception {
            retrieveEventBus(mailboxManager).register(listener);

            Optional<MailboxId> newId = mailboxManager.createMailbox(newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.MailboxAdded)
                .hasSize(1)
                .extracting(event -> (MailboxListener.MailboxAdded) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(newId.get()));
        }

        @Test
        void addingMessageShouldFireQuotaUpdateEvent() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Quota));
            retrieveEventBus(mailboxManager).register(listener);

            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                    .build(message), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.QuotaUsageUpdatedEvent)
                .hasSize(1)
                .extracting(event -> (MailboxListener.QuotaUsageUpdatedEvent) event)
                .element(0)
                .satisfies(event -> assertThat(event.getQuotaRoot()).isEqualTo(quotaRoot))
                .satisfies(event -> assertThat(event.getSizeQuota()).isEqualTo(Quota.<QuotaSize>builder()
                    .used(QuotaSize.size(85))
                    .computedLimit(QuotaSize.unlimited())
                    .build()))
                .satisfies(event -> assertThat(event.getCountQuota()).isEqualTo(Quota.<QuotaCount>builder()
                    .used(QuotaCount.count(1))
                    .computedLimit(QuotaCount.unlimited())
                    .build()));
        }

        @Test
        void addingMessageShouldFireAddedEvent() throws Exception {
            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                    .build(message), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Added)
                .hasSize(1)
                .extracting(event -> (MailboxListener.Added) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void expungeMessageShouldFireExpungedEvent() throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(message), session);
            inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            inboxManager.expunge(MessageRange.all(), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Expunged)
                .hasSize(1)
                .extracting(event -> (MailboxListener.Expunged) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void deleteMessageShouldFireExpungedEvent() throws Exception {
            ComposedMessageId messageId = inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(message), session);
            inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            inboxManager.delete(ImmutableList.of(messageId.getUid()), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Expunged)
                .hasSize(1)
                .extracting(event -> (MailboxListener.Expunged) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void setFlagsShouldFireFlagsUpdatedEvent() throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            inboxManager.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.FlagsUpdated)
                .hasSize(1)
                .extracting(event -> (MailboxListener.FlagsUpdated) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void moveShouldFireAddedEventInTargetMailbox() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Move));
            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()));
            mailboxManager.moveMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Added)
                .hasSize(1)
                .extracting(event -> (MailboxListener.Added) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(targetMailboxId.get()))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void moveShouldFireExpungedEventInOriginMailbox() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Move));
            mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            mailboxManager.moveMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Expunged)
                .hasSize(1)
                .extracting(event -> (MailboxListener.Expunged) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void copyShouldFireAddedEventInTargetMailbox() throws Exception {
            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()));
            mailboxManager.copyMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Added)
                .hasSize(1)
                .extracting(event -> (MailboxListener.Added) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(targetMailboxId.get()))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void copyShouldFireMovedEventInTargetMailbox() throws Exception {
            assumeTrue(mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.UniqueID));

            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            ComposedMessageId messageId = inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()));
            mailboxManager.copyMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MessageMoveEvent)
                .hasSize(1)
                .extracting(event -> (MessageMoveEvent) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMessageIds()).containsExactly(messageId.getMessageId()));
        }

        @Test
        void copyShouldNotFireMovedEventInOriginMailbox() throws Exception {
            assumeTrue(mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.UniqueID));

            mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            mailboxManager.copyMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MessageMoveEvent)
                .isEmpty();;
        }

        @Test
        void moveShouldFireMovedEventInTargetMailbox() throws Exception {
            assumeTrue(mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.UniqueID));

            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            ComposedMessageId messageId = inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()));
            mailboxManager.moveMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MessageMoveEvent)
                .hasSize(1)
                .extracting(event -> (MessageMoveEvent) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMessageIds()).containsExactly(messageId.getMessageId()));
        }

        @Test
        void moveShouldFireMovedEventInOriginMailbox() throws Exception {
            assumeTrue(mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.UniqueID));

            mailboxManager.createMailbox(newPath, session);
            ComposedMessageId messageId = inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId));
            mailboxManager.moveMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MessageMoveEvent)
                .hasSize(1)
                .extracting(event -> (MessageMoveEvent) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMessageIds()).containsExactly(messageId.getMessageId()));
        }

        @Test
        void copyShouldNotFireExpungedEventInOriginMailbox() throws Exception {
            mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            retrieveEventBus(mailboxManager).register(listener);
            mailboxManager.copyMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxListener.Expunged)
                .isEmpty();
        }
    }

    @Nested
    class SearchTests {
        @Test
        void searchShouldNotReturnResultsFromOtherNamespaces() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Namespace));
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.createMailbox(new MailboxPath("other_namespace", USER_1, "Other"), session);
            mailboxManager.createMailbox(MailboxPath.inbox(session), session);
            List<MailboxMetaData> metaDatas = mailboxManager.search(
                MailboxQuery.privateMailboxesBuilder(session)
                    .matchesAllMailboxNames()
                    .build(),
                session);
            assertThat(metaDatas).hasSize(1);
            assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
        }

        @Test
        void searchShouldNotReturnResultsFromOtherUsers() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            mailboxManager.createMailbox(MailboxPath.forUser(USER_2, "Other"), session2);
            mailboxManager.createMailbox(MailboxPath.inbox(session), session);
            List<MailboxMetaData> metaDatas = mailboxManager.search(
                MailboxQuery.privateMailboxesBuilder(session)
                    .matchesAllMailboxNames()
                    .build(),
                session);
            assertThat(metaDatas).hasSize(1);
            assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
        }

        @Test
        void searchShouldNotDuplicateMailboxWhenReportedAsUserMailboxesAndUserHasRightOnMailboxes() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_1)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session1))
                .extracting(MailboxMetaData::getPath)
                .hasSize(1)
                .containsOnly(inbox1);
        }

        @Test
        void searchShouldIncludeDelegatedMailboxes() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session2))
                .extracting(MailboxMetaData::getPath)
                .containsOnly(inbox1);
        }

        @Test
        void searchShouldCombinePrivateAndDelegatedMailboxes() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            MailboxPath inbox2 = MailboxPath.inbox(session2);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.createMailbox(inbox2, session2);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session2))
                .extracting(MailboxMetaData::getPath)
                .containsOnly(inbox1, inbox2);
        }

        @Test
        void searchShouldAllowUserFiltering() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            MailboxPath inbox2 = MailboxPath.inbox(session2);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.createMailbox(inbox2, session2);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .username(USER_1)
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session2))
                .extracting(MailboxMetaData::getPath)
                .containsOnly(inbox1);
        }

        @Test
        void searchShouldAllowNamespaceFiltering() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            String specificNamespace = "specificNamespace";
            MailboxPath mailboxPath1 = new MailboxPath(specificNamespace, USER_1, "mailbox");
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.createMailbox(mailboxPath1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);
            mailboxManager.setRights(mailboxPath1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .namespace(specificNamespace)
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session2))
                .extracting(MailboxMetaData::getPath)
                .containsOnly(mailboxPath1);
        }

        @Test
        void searchForMessageShouldReturnMessagesFromAllMyMailboxesIfNoMailboxesAreSpecified() throws Exception {
            assumeTrue(mailboxManager
                .getSupportedMessageCapabilities()
                .contains(MailboxManager.MessageCapabilities.UniqueID));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath cacahueteFolder = MailboxPath.forUser(USER_1, "CACAHUETE");
            MailboxId cacahueteMailboxId = mailboxManager.createMailbox(cacahueteFolder, session).get();
            MessageManager cacahueteMessageManager = mailboxManager.getMailbox(cacahueteMailboxId, session);
            MessageId cacahueteMessageId = cacahueteMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getMessageId();

            MailboxPath pirouetteFilder = MailboxPath.forUser(USER_1, "PIROUETTE");
            MailboxId pirouetteMailboxId = mailboxManager.createMailbox(pirouetteFilder, session).get();
            MessageManager pirouetteMessageManager = mailboxManager.getMailbox(pirouetteMailboxId, session);

            MessageId pirouetteMessageId = pirouetteMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getMessageId();

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .build();


            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .containsOnly(cacahueteMessageId, pirouetteMessageId);
        }

        @Test
        void searchForMessageShouldReturnMessagesFromMyDelegatedMailboxes() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
            MailboxPath delegatedMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
            MailboxId delegatedMailboxId = mailboxManager.createMailbox(delegatedMailboxPath, sessionFromDelegater).get();
            MessageManager delegatedMessageManager = mailboxManager.getMailbox(delegatedMailboxId, sessionFromDelegater);

            MessageId messageId = delegatedMessageManager
                .appendMessage(AppendCommand.from(message), sessionFromDelegater)
                .getMessageId();

            mailboxManager.setRights(delegatedMailboxPath,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_1)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .containsOnly(messageId);
        }

        @Test
        void searchForMessageShouldNotReturnMessagesFromMyDelegatedMailboxesICanNotRead() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
            MailboxPath delegatedMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
            MailboxId delegatedMailboxId = mailboxManager.createMailbox(delegatedMailboxPath, sessionFromDelegater).get();
            MessageManager delegatedMessageManager = mailboxManager.getMailbox(delegatedMailboxId, sessionFromDelegater);

            delegatedMessageManager.appendMessage(AppendCommand.from(message), sessionFromDelegater);

            mailboxManager.setRights(delegatedMailboxPath,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_1)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()),
                sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .isEmpty();
        }

        @Test
        void searchForMessageShouldOnlySearchInMailboxICanRead() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
            MailboxPath otherMailboxPath = MailboxPath.forUser(USER_2, "OTHER_MAILBOX");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, sessionFromDelegater).get();
            MessageManager otherMailboxManager = mailboxManager.getMailbox(otherMailboxId, sessionFromDelegater);

            otherMailboxManager.appendMessage(AppendCommand.from(message), sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .isEmpty();
        }

        @Test
        void searchForMessageShouldIgnoreMailboxThatICanNotRead() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
            MailboxPath otherMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, sessionFromDelegater).get();
            MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, sessionFromDelegater);

            otherMessageManager.appendMessage(AppendCommand.from(message), sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .inMailboxes(otherMailboxId)
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .isEmpty();
        }

        @Test
        void searchForMessageShouldCorrectlyExcludeMailbox() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxPath otherMailboxPath = MailboxPath.forUser(USER_1, "SHARED");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, session).get();
            MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, session);

            otherMessageManager.appendMessage(AppendCommand.from(message), session);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .notInMailboxes(otherMailboxId)
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .isEmpty();
        }

        @Test
        void searchForMessageShouldPriorizeExclusionFromInclusion() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxPath otherMailboxPath = MailboxPath.forUser(USER_1, "SHARED");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, session).get();
            MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, session);

            otherMessageManager.appendMessage(AppendCommand.from(message), session);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .inMailboxes(otherMailboxId)
                .notInMailboxes(otherMailboxId)
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .isEmpty();
        }

        @Test
        void searchForMessageShouldOnlySearchInGivenMailbox() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath searchedMailboxPath = MailboxPath.forUser(USER_1, "WANTED");
            MailboxId searchedMailboxId = mailboxManager.createMailbox(searchedMailboxPath, session).get();
            MessageManager searchedMessageManager = mailboxManager.getMailbox(searchedMailboxId, session);

            MailboxPath otherMailboxPath = MailboxPath.forUser(USER_1, "SHARED");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, session).get();
            MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, session);

            otherMessageManager.appendMessage(AppendCommand.from(message), session);

            MessageId messageId = searchedMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getMessageId();

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(new SearchQuery())
                .inMailboxes(searchedMailboxId)
                .build();

            assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .containsExactly(messageId);
        }

        @Test
        void searchShouldNotReturnNoMoreDelegatedMailboxes() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asRemoval()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session2))
                .isEmpty();
        }
    }

    @Nested
    class MetadataTests {
        @Test
        void getMailboxCountersShouldReturnDefaultValueWhenNoReadRight() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            mailboxManager.getMailbox(inbox1, session1).appendMessage(AppendCommand.from(message), session1);

            MailboxCounters mailboxCounters = mailboxManager.getMailbox(inbox1, session2)
                .getMailboxCounters(session2);

            assertThat(mailboxCounters)
                .isEqualTo(MailboxCounters.builder()
                    .count(0)
                    .unseen(0)
                    .build());
        }

        @Test
        void getMailboxCountersShouldReturnStoredValueWhenReadRight() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read)
                    .asAddition()),
                session1);

            mailboxManager.getMailbox(inbox1, session1)
                .appendMessage(AppendCommand.builder()
                    .recent()
                    .build(message), session1);

            MailboxCounters mailboxCounters = mailboxManager.getMailbox(inbox1, session2)
                .getMailboxCounters(session2);

            assertThat(mailboxCounters)
                .isEqualTo(MailboxCounters.builder()
                    .count(1)
                    .unseen(1)
                    .build());
        }

        @Test
        @SuppressWarnings("unchecked")
        void getMetaDataShouldReturnDefaultValueWhenNoReadRight() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            mailboxManager.getMailbox(inbox1, session1)
                .appendMessage(AppendCommand.builder()
                    .recent()
                    .build(message), session1);

            boolean resetRecent = false;
            MessageManager.MetaData metaData = mailboxManager.getMailbox(inbox1, session2)
                .getMetaData(resetRecent, session2, MessageManager.MetaData.FetchGroup.UNSEEN_COUNT);

            assertSoftly(
                softly -> {
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MetaData::getHighestModSeq)
                        .contains(0L);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MetaData::getUidNext)
                        .contains(MessageUid.MIN_VALUE);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MetaData::getMessageCount)
                        .contains(0L);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MetaData::getUnseenCount)
                        .contains(0L);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MetaData::getRecent)
                        .contains(ImmutableList.of());
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MetaData::getPermanentFlags)
                        .contains(new Flags());
                });
        }
    }

    @Nested
    public class BasicFeaturesTests {
        @Test
        void user1ShouldNotHaveAnInbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            assertThat(mailboxManager.mailboxExists(inbox, session)).isFalse();
        }

        @Test
        void user1ShouldBeAbleToCreateInbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThat(mailboxManager.mailboxExists(inbox, session)).isTrue();
        }

        @Test
        protected void renameMailboxShouldChangeTheMailboxPathOfAMailbox() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx2");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath1, session);

            mailboxManager.renameMailbox(mailboxPath1, mailboxPath2, session);

            assertThat(mailboxManager.getMailbox(mailboxId.get(), session).getMailboxPath())
                .isEqualTo(mailboxPath2);
        }

        @Test
        void user1ShouldNotBeAbleToCreateInboxTwice() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThatThrownBy(() -> mailboxManager.createMailbox(inbox, session))
                .isInstanceOf(MailboxException.class);
        }

        @Test
        void user1ShouldNotHaveTestSubmailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThat(mailboxManager.mailboxExists(new MailboxPath(inbox, "INBOX.Test"), session)).isFalse();
        }

        @Test
        void user1ShouldBeAbleToCreateTestSubmailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
            mailboxManager.createMailbox(inboxSubMailbox, session);

            assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isTrue();
        }

        @Test
        void user1ShouldBeAbleToDeleteInbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);
            MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
            mailboxManager.createMailbox(inboxSubMailbox, session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(mailboxManager.mailboxExists(inbox, session)).isFalse();
            assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isTrue();
        }

        @Test
        void user1ShouldBeAbleToDeleteSubmailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);
            MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
            mailboxManager.createMailbox(inboxSubMailbox, session);

            mailboxManager.deleteMailbox(inboxSubMailbox, session);

            assertThat(mailboxManager.mailboxExists(inbox, session)).isTrue();
            assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isFalse();
        }

        @Test
        void listShouldReturnMailboxes() throws Exception {
            session = mailboxManager.createSystemSession("manager");
            mailboxManager.startProcessingRequest(session);

            DataProvisioner.feedMailboxManager(mailboxManager);

            assertThat(mailboxManager.list(session)).hasSize(DataProvisioner.EXPECTED_MAILBOXES_COUNT);
        }

        @Test
        void user2ShouldBeAbleToCreateRootlessFolder() throws MailboxException {
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath trash = MailboxPath.forUser(USER_2, "Trash");
            mailboxManager.createMailbox(trash, session);

            assertThat(mailboxManager.mailboxExists(trash, session)).isTrue();
        }

        @Test
        void user2ShouldBeAbleToCreateNestedFoldersWithoutTheirParents() throws Exception {
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath nestedFolder = MailboxPath.forUser(USER_2, "INBOX.testfolder");
            mailboxManager.createMailbox(nestedFolder, session);

            assertThat(mailboxManager.mailboxExists(nestedFolder, session)).isTrue();
            mailboxManager.getMailbox(MailboxPath.inbox(session), session)
                .appendMessage(AppendCommand.from(message), session);
        }

        @Test
        void moveMessagesShouldNotThrowWhenMovingAllMessagesOfAnEmptyMailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThatCode(() -> mailboxManager.moveMessages(MessageRange.all(), inbox, inbox, session))
                .doesNotThrowAnyException();
        }

        @Test
        void copyMessagesShouldNotThrowWhenMovingAllMessagesOfAnEmptyMailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThatCode(() -> mailboxManager.copyMessages(MessageRange.all(), inbox, inbox, session))
                .doesNotThrowAnyException();
        }

        @Test
        void createMailboxShouldNotThrowWhenMailboxPathBelongsToUser() throws MailboxException {
            session = mailboxManager.createSystemSession(USER_1);
            Optional<MailboxId> mailboxId = mailboxManager
                .createMailbox(MailboxPath.forUser(USER_1, "mailboxName"), session);

            assertThat(mailboxId).isNotEmpty();
        }

        @Test
        void createMailboxShouldThrowWhenMailboxPathBelongsToAnotherUser() throws MailboxException {
            session = mailboxManager.createSystemSession(USER_1);

            assertThatThrownBy(() -> mailboxManager
                    .createMailbox(MailboxPath.forUser(USER_2, "mailboxName"), session))
                .isInstanceOf(MailboxException.class);
        }
    }

    @Nested
    class SessionTests {
        @Test
        void createUser1SystemSessionShouldReturnValidSession() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            assertThat(session.getUser().asString()).isEqualTo(USER_1);
        }

        @Test
        void closingSessionShouldWork() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            mailboxManager.logout(session, false);
            mailboxManager.endProcessingRequest(session);

            assertThat(session.isOpen()).isFalse();
        }
    }

    @Nested
    class HookTests {

        @Nested
        class PreDeletion {

            private MailboxPath inbox;
            private MailboxId inboxId;
            private MailboxId anotherMailboxId;
            private MessageManager inboxManager;
            private MessageManager anotherMailboxManager;

            @BeforeEach
            void setUp() throws Exception {
                session = mailboxManager.createSystemSession(USER_1);
                inbox = MailboxPath.inbox(session);

                MailboxPath anotherMailboxPath = MailboxPath.forUser(USER_1, "anotherMailbox");
                anotherMailboxId = mailboxManager.createMailbox(anotherMailboxPath, session).get();

                inboxId = mailboxManager.createMailbox(inbox, session).get();
                inboxManager = mailboxManager.getMailbox(inbox, session);
                anotherMailboxManager = mailboxManager.getMailbox(anotherMailboxPath, session);
            }

            @Test
            void expungeShouldCallAllPreDeletionHooks() throws Exception {
                ComposedMessageId composeId = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);
                inboxManager.expunge(MessageRange.one(composeId.getUid()), session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(1)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(1)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getValue().getDeletionMetadataList())
                    .hasSize(1)
                    .hasSameElementsAs(preDeleteCaptor2.getValue().getDeletionMetadataList())
                    .allSatisfy(deleteMetadata -> SoftAssertions.assertSoftly(softy -> {
                        softy.assertThat(deleteMetadata.getMailboxId()).isEqualTo(inboxId);
                        softy.assertThat(deleteMetadata.getMessageMetaData().getMessageId()).isEqualTo(composeId.getMessageId());
                    }));
            }

            @Test
            void deleteMailboxShouldCallAllPreDeletionHooks() throws Exception {
                ComposedMessageId composeId = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);
                mailboxManager.deleteMailbox(inbox, session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(1)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(1)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getValue().getDeletionMetadataList())
                    .hasSize(1)
                    .hasSameElementsAs(preDeleteCaptor2.getValue().getDeletionMetadataList())
                    .allSatisfy(deleteMetadata -> SoftAssertions.assertSoftly(softy -> {
                        softy.assertThat(deleteMetadata.getMailboxId()).isEqualTo(inboxId);
                        softy.assertThat(deleteMetadata.getMessageMetaData().getMessageId()).isEqualTo(composeId.getMessageId());
                    }));
            }

            @Test
            void expungeShouldCallAllPreDeletionHooksOnEachMessageDeletionCall() throws Exception {
                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);
                ComposedMessageId composeId2 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);

                inboxManager.expunge(MessageRange.one(composeId1.getUid()), session);
                inboxManager.expunge(MessageRange.one(composeId2.getUid()), session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(2)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(2)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getAllValues())
                    .hasSize(2)
                    .hasSameElementsAs(preDeleteCaptor2.getAllValues())
                    .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
                    .allSatisfy(deleteMetadata -> assertThat(deleteMetadata.getMailboxId()).isEqualTo(inboxId))
                    .extracting(deleteMetadata -> deleteMetadata.getMessageMetaData().getMessageId())
                    .containsOnly(composeId1.getMessageId(), composeId2.getMessageId());
            }

            @Test
            void expungeShouldCallAllPreDeletionHooksOnlyOnMessagesMarkedAsDeleted() throws Exception {
                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);
                inboxManager.appendMessage(AppendCommand.builder()
                    .build(message), session);

                inboxManager.expunge(MessageRange.all(), session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(1)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(1)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getValue().getDeletionMetadataList())
                    .hasSize(1)
                    .hasSameElementsAs(preDeleteCaptor2.getValue().getDeletionMetadataList())
                    .allSatisfy(deleteMetadata -> SoftAssertions.assertSoftly(softy -> {
                        softy.assertThat(deleteMetadata.getMailboxId()).isEqualTo(inboxId);
                        softy.assertThat(deleteMetadata.getMessageMetaData().getMessageId()).isEqualTo(composeId1.getMessageId());
                    }));
            }

            @Test
            void expungeShouldNotCallPredeletionHooksWhenNoMessagesMarkedAsDeleted() throws Exception {
                inboxManager.appendMessage(AppendCommand.builder()
                    .build(message), session);

                inboxManager.expunge(MessageRange.all(), session);

                verifyZeroInteractions(preDeletionHook1);
                verifyZeroInteractions(preDeletionHook2);
            }

            @Test
            void expungeShouldCallAllPreDeletionHooksOnEachMessageDeletionOnDifferentMailboxes() throws Exception {
                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);
                ComposedMessageId composeId2 = anotherMailboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);

                inboxManager.expunge(MessageRange.one(composeId1.getUid()), session);
                anotherMailboxManager.expunge(MessageRange.one(composeId2.getUid()), session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(2)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(2)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getAllValues())
                    .hasSameElementsAs(preDeleteCaptor2.getAllValues())
                    .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
                    .extracting(deleteMetadata -> deleteMetadata.getMessageMetaData().getMessageId())
                    .containsOnly(composeId1.getMessageId(), composeId2.getMessageId());

                assertThat(preDeleteCaptor1.getAllValues())
                    .hasSameElementsAs(preDeleteCaptor2.getAllValues())
                    .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
                    .extracting(MetadataWithMailboxId::getMailboxId)
                    .containsOnly(inboxId, anotherMailboxId);
            }

            @Test
            void expungeShouldNotBeExecutedWhenOneOfPreDeleteHooksFails() throws Exception {
                when(preDeletionHook1.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
                    .thenThrow(new RuntimeException("throw at hook 1"));

                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session);
                assertThatThrownBy(() -> inboxManager.expunge(MessageRange.one(composeId1.getUid()), session))
                    .isInstanceOf(RuntimeException.class);

                assertThat(ImmutableList.copyOf(inboxManager.getMessages(MessageRange.one(composeId1.getUid()), FetchGroupImpl.MINIMAL, session))
                        .stream()
                        .map(MessageResult::getMessageId))
                    .hasSize(1)
                    .containsOnly(composeId1.getMessageId());
            }

            @Test
            void expungeShouldBeExecutedAfterAllHooksFinish() throws Exception {
                CountDownLatch latchForHook1 = new CountDownLatch(1);
                when(preDeletionHook1.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
                    .thenAnswer(invocation -> {
                        latchForHook1.countDown();
                        return Mono.empty();
                    });

                CountDownLatch latchForHook2 = new CountDownLatch(1);
                when(preDeletionHook2.notifyDelete(any(PreDeletionHook.DeleteOperation.class)))
                    .thenAnswer(invocation -> {
                        latchForHook2.countDown();
                        return Mono.empty();
                    });

                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder().build(message), session);
                inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD,
                    MessageRange.one(composeId1.getUid()), session);
                inboxManager.expunge(MessageRange.all(), session);

                latchForHook1.await();
                latchForHook2.await();

                assertThat(inboxManager.getMessages(MessageRange.one(composeId1.getUid()), FetchGroupImpl.MINIMAL, session))
                    .isEmpty();
            }
        }
    }
}
