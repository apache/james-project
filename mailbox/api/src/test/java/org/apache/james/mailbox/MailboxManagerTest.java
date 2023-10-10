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

import static org.apache.james.mailbox.MailboxManager.RenameOption.RENAME_SUBSCRIPTIONS;
import static org.apache.james.mailbox.MessageManager.FlagsUpdateMode.REPLACE;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManager.MailboxCapabilities;
import org.apache.james.mailbox.MailboxManager.MailboxRenamedResult;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.HasEmptyMailboxNameInHierarchyException;
import org.apache.james.mailbox.exception.InboxAlreadyCreated;
import org.apache.james.mailbox.exception.InsufficientRightsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.mock.DataProvisioner;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
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
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.AccessibleNamespace;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.PersonalNamespace;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.ClassLoaderUtils;
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Test the {@link MailboxManager} methods that
 * are not covered by the protocol-tester suite.
 * 
 * This class needs to be extended by the different mailbox 
 * implementations which are responsible to setup and 
 * implement the test methods.
 */
public abstract class MailboxManagerTest<T extends MailboxManager> {
    public static final Username USER_1 = Username.of("USER_1");
    public static final Username USER_2 = Username.of("USER_2");
    private static final int DEFAULT_MAXIMUM_LIMIT = 256;

    protected T mailboxManager;
    private  SubscriptionManager subscriptionManager;
    private MailboxSession session;
    protected Message.Builder message;

    private PreDeletionHook preDeletionHook1;
    private PreDeletionHook preDeletionHook2;

    protected abstract T provideMailboxManager();

    protected abstract SubscriptionManager provideSubscriptionManager();

    protected abstract EventBus retrieveEventBus(T mailboxManager);

    protected Set<PreDeletionHook> preDeletionHooks() {
        return ImmutableSet.of(preDeletionHook1, preDeletionHook2);
    }

    @BeforeEach
    void setUp() throws Exception {
        setupMockForPreDeletionHooks();
        this.mailboxManager = provideMailboxManager();
        this.subscriptionManager = provideSubscriptionManager();

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
    void tearDown() {
        mailboxManager.endProcessingRequest(session);
    }

    @Test
    protected void creatingConcurrentlyMailboxesWithSameParentShouldNotFail() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USER_1);
        String mailboxName = "a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z";

        ConcurrentTestRunner.builder()
            .operation((a, b) -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName + a), session))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    void createMailboxShouldReturnRightId() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "name.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
        MessageManager retrievedMailbox = mailboxManager.getMailbox(mailboxPath, session);

        assertThat(mailboxId.isPresent()).isTrue();
        assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
    }

    @Test
    void createMailboxShouldNotSubscribeByDefault() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "name.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);

        assertThat(subscriptionManager.subscriptions(session)).isEmpty();
    }

    @Test
    void createMailboxShouldNotSubscribeWhenNone() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "name.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, MailboxManager.CreateOption.NONE, session);

        assertThat(subscriptionManager.subscriptions(session)).isEmpty();
    }

    @Test
    void createMailboxShouldSubscribeWhenRequested() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);

        assertThat(subscriptionManager.subscriptions(session)).containsOnly(mailboxPath);
    }

    @Test
    void createMailboxShouldSubscribeCreatedParentsWhenRequested() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath parentPath = MailboxPath.forUser(USER_1, "parent");
        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "parent.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);

        assertThat(subscriptionManager.subscriptions(session)).containsOnly(parentPath, mailboxPath);
    }

    @Test
    void createShouldSucceedWhenSubFolderExists() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxId parentId = mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "name"), session).get();
        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "name.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
        MessageManager retrievedMailbox = mailboxManager.getMailbox(mailboxPath, session);

        assertThat(mailboxId.isPresent()).isTrue();
        assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
        assertThat(mailboxManager.getMailbox(MailboxPath.forUser(USER_1, "name"), session).getId()).isEqualTo(parentId);
    }

    @Nested
    class MailboxCreationTests {
        @Test
        void hasInboxShouldBeFalseWhenINBOXIsNotCreated() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            assertThat(Mono.from(mailboxManager.hasInbox(session)).block()).isFalse();
        }

        @Test
        void hasInboxShouldBeTrueWhenINBOXIsCreated() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath mailboxPath = MailboxPath.inbox(session);
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
            MessageManager retrievedMailbox = mailboxManager.getMailbox(mailboxPath, session);

            assertThat(Mono.from(mailboxManager.hasInbox(session)).block()).isTrue();
            assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
        }

        @Test
        void creatingMixedCaseINBOXShouldCreateItAsINBOX() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "iNbOx"), session);
            MessageManager retrievedMailbox = mailboxManager.getMailbox(MailboxPath.inbox(session), session);

            assertThat(Mono.from(mailboxManager.hasInbox(session)).block()).isTrue();
            assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
        }

        @Test
        void creatingMixedCaseINBOXShouldNotBeRetrievableAsIt() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "iNbOx");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
            assertThat(mailboxId).isPresent();

            assertThatThrownBy(() -> mailboxManager.getMailbox(mailboxPath, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void creatingMixedCaseINBOXWhenItHasAlreadyBeenCreatedShouldThrow() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            mailboxManager.createMailbox(MailboxPath.inbox(session), session);

            assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "iNbOx"), session))
                .isInstanceOf(InboxAlreadyCreated.class);
        }

        @Test
        void creatingMixedCaseINBOXShouldCreateItAsINBOXUponChildMailboxCreation() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "iNbOx.submailbox"), session);
            MessageManager retrievedMailbox = mailboxManager.getMailbox(MailboxPath.inbox(session), session);

            assertThat(Mono.from(mailboxManager.hasInbox(session)).block()).isTrue();
        }

        @Test
        void creatingMixedCaseINBOXChildShouldNormalizeChildPath() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath childPath = MailboxPath.forUser(USER_1, "iNbOx.submailbox");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(childPath, session);
            MessageManager retrievedMailbox = mailboxManager.getMailbox(childPath, session);

            assertThat(Mono.from(mailboxManager.hasInbox(session)).block()).isTrue();
            assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
        }
    }

    @Nested
    public class MailboxNameLimitTests {
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
        protected void renamingMailboxByIdShouldNotFailWhenLimitNameLength() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName = Strings.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH);

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatCode(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session))
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

        @Test
        void renamingMailboxByIdShouldThrowWhenOverLimitNameLength() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName = Strings.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH + 1);

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(TooLongMailboxNameException.class);
        }

        @Test
        void creatingMailboxShouldNotThrowWhenNameWithoutEmptyHierarchicalLevel() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a.b.c";

            assertThatCode(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session)).doesNotThrowAnyException();
        }

        @Test
        void creatingMailboxShouldNotThrowWhenNameWithASingleToBeNormalizedTrailingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a.b.";

            assertThatCode(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session))
                .doesNotThrowAnyException();
        }

        @Test
        void creatingMailboxShouldThrowWhenNameWithMoreThanOneTrailingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a..";

            assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void creatingMailboxShouldThrowWhenNameWithHeadingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  ".a";

            assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void creatingMailboxShouldThrowWhenNameWithEmptyHierarchicalLevel() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a..b";

            assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void renamingMailboxShouldNotThrowWhenNameWithoutEmptyHierarchicalLevel() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a.b.c";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatCode(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session)).doesNotThrowAnyException();
        }

        @Test
        protected void renamingMailboxByIdShouldNotThrowWhenNameWithoutEmptyHierarchicalLevel() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a.b.c";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatCode(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session)).doesNotThrowAnyException();
        }

        @Test
        void renamingMailboxShouldNotThrowWhenNameWithASingleToBeNormalizedTrailingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a.b.";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatCode(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session)).doesNotThrowAnyException();
        }

        @Test
        protected void renamingMailboxByIdShouldNotThrowWhenNameWithASingleToBeNormalizedTrailingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a.b.";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatCode(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session)).doesNotThrowAnyException();
        }

        @Test
        void renamingMailboxShouldThrowWhenNameWithMoreThanOneTrailingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a..";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void renamingMailboxByIdShouldThrowWhenNameWithMoreThanOneTrailingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a..";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void renamingMailboxShouldThrowWhenNameWithHeadingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  ".a";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void renamingMailboxByIdShouldThrowWhenNameWithHeadingDelimiter() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  ".a";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void renamingMailboxShouldThrowWhenNameWithEmptyHierarchicalLevel() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a..b";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            mailboxManager.createMailbox(originPath, session);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(originPath, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void renamingMailboxByIdShouldThrowWhenNameWithEmptyHierarchicalLevel() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            String mailboxName =  "a..b";

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxId mailboxId = mailboxManager.createMailbox(originPath, session).get();

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId, MailboxPath.forUser(USER_1, mailboxName), session))
                .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
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
        void updateAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox = MailboxPath.inbox(session);

            assertThatThrownBy(() -> mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(privateAnnotation)))
                .isInstanceOf(MailboxNotFoundException.class);
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
                .isInstanceOf(MailboxNotFoundException.class);
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
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void getAnnotationsByKeysWithOneDepthShouldRetrieveAnnotationsWithOneDepth() throws Exception {
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
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void getAnnotationsByKeysWithAllDepthShouldRetrieveAnnotationsWithAllDepth() throws Exception {
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
        private final QuotaRoot quotaRoot = QuotaRoot.quotaRoot("#private&user_1", Optional.empty());
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
            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxDeletion)
                .hasSize(1)
                .extracting(event -> (MailboxDeletion) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getQuotaRoot()).isEqualTo(quotaRoot))
                .satisfies(event -> assertThat(event.getDeletedMessageCount()).isEqualTo(QuotaCountUsage.count(0)))
                .satisfies(event -> assertThat(event.getTotalDeletedSize()).isEqualTo(QuotaSizeUsage.size(0)));
        }

        @Test
        void deleteMailboxByIdShouldFireMailboxDeletionEvent() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Quota));
            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();

            mailboxManager.deleteMailbox(inboxId, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxDeletion)
                .hasSize(1)
                .extracting(event -> (MailboxDeletion) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getQuotaRoot()).isEqualTo(quotaRoot))
                .satisfies(event -> assertThat(event.getDeletedMessageCount()).isEqualTo(QuotaCountUsage.count(0)))
                .satisfies(event -> assertThat(event.getTotalDeletedSize()).isEqualTo(QuotaSizeUsage.size(0)));
        }

        @Test
        void createMailboxShouldFireMailboxAddedEvent() throws Exception {
            retrieveEventBus(mailboxManager).register(listener);

            Optional<MailboxId> newId = mailboxManager.createMailbox(newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MailboxAdded)
                .hasSize(1)
                .extracting(event -> (MailboxAdded) event)
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
                .filteredOn(event -> event instanceof QuotaUsageUpdatedEvent)
                .hasSize(1)
                .extracting(event -> (QuotaUsageUpdatedEvent) event)
                .element(0)
                .satisfies(event -> assertThat(event.getQuotaRoot()).isEqualTo(quotaRoot))
                .satisfies(event -> assertThat(event.getSizeQuota()).isEqualTo(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                    .used(QuotaSizeUsage.size(85))
                    .computedLimit(QuotaSizeLimit.unlimited())
                    .build()))
                .satisfies(event -> assertThat(event.getCountQuota()).isEqualTo(Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                    .used(QuotaCountUsage.count(1))
                    .computedLimit(QuotaCountLimit.unlimited())
                    .build()));
        }

        @Test
        void addingMessageShouldFireAddedEvent() throws Exception {
            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                    .build(message), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof Added)
                .hasSize(1)
                .extracting(event -> (Added) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void expungeMessageShouldFireExpungedEvent() throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(message), session);
            inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
            inboxManager.expunge(MessageRange.all(), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof Expunged)
                .hasSize(1)
                .extracting(event -> (Expunged) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void deleteMessageShouldFireExpungedEvent() throws Exception {
            ComposedMessageId messageId = inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(message), session).getId();
            inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
            inboxManager.delete(ImmutableList.of(messageId.getUid()), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof Expunged)
                .hasSize(1)
                .extracting(event -> (Expunged) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void setFlagsShouldFireFlagsUpdatedEvent() throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(message), session);

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
            inboxManager.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof FlagsUpdated)
                .hasSize(1)
                .extracting(event -> (FlagsUpdated) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void moveShouldFireAddedEventInTargetMailbox() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Move));
            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()))).block();
            mailboxManager.moveMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof Added)
                .hasSize(1)
                .extracting(event -> (Added) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(targetMailboxId.get()))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void moveShouldFireExpungedEventInOriginMailbox() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Move));
            mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
            mailboxManager.moveMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof Expunged)
                .hasSize(1)
                .extracting(event -> (Expunged) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(inboxId))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void copyShouldFireAddedEventInTargetMailbox() throws Exception {
            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            inboxManager.appendMessage(AppendCommand.builder().build(message), session);

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()))).block();
            mailboxManager.copyMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof Added)
                .hasSize(1)
                .extracting(event -> (Added) event)
                .element(0)
                .satisfies(event -> assertThat(event.getMailboxId()).isEqualTo(targetMailboxId.get()))
                .satisfies(event -> assertThat(event.getUids()).hasSize(1));
        }

        @Test
        void copyShouldFireMovedEventInTargetMailbox() throws Exception {
            assumeTrue(mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.UniqueID));

            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            ComposedMessageId messageId = inboxManager.appendMessage(AppendCommand.builder().build(message), session).getId();

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()))).block();
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

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
            mailboxManager.copyMessages(MessageRange.all(), inbox, newPath, session);

            assertThat(listener.getEvents())
                .filteredOn(event -> event instanceof MessageMoveEvent)
                .isEmpty();;
        }

        @Test
        void moveShouldFireMovedEventInTargetMailbox() throws Exception {
            assumeTrue(mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.UniqueID));

            Optional<MailboxId> targetMailboxId = mailboxManager.createMailbox(newPath, session);
            ComposedMessageId messageId = inboxManager.appendMessage(AppendCommand.builder().build(message), session).getId();

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(targetMailboxId.get()))).block();
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
            ComposedMessageId messageId = inboxManager.appendMessage(AppendCommand.builder().build(message), session).getId();

            Mono.from(retrieveEventBus(mailboxManager).register(listener, new MailboxIdRegistrationKey(inboxId))).block();
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
                .filteredOn(event -> event instanceof Expunged)
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
                session)
                .collectList()
                .block();
            assertThat(metaDatas).hasSize(1);
            assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
        }

        @Test
        void searchShouldReturnACL() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Namespace));
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            session = mailboxManager.createSystemSession(USER_1);
            Optional<MailboxId> inboxId = mailboxManager.createMailbox(MailboxPath.inbox(session), session);

            MailboxACL acl = MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition());
            mailboxManager.setRights(inboxId.get(), acl, session);

            List<MailboxMetaData> metaDatas = mailboxManager.search(
                MailboxQuery.privateMailboxesBuilder(session)
                    .matchesAllMailboxNames()
                    .build(),
                session)
                .collectList()
                .block();
            assertThat(metaDatas)
                .hasSize(1)
                .first()
                .extracting(MailboxMetaData::getResolvedAcls)
                .isEqualTo(acl.apply(MailboxACL.command()
                    .forOwner()
                    .rights(MailboxACL.Rfc4314Rights.allExcept())
                    .asAddition()));
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
                session)
                .collectList()
                .block();
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

            assertThat(mailboxManager.search(mailboxQuery, session1).toStream())
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
            Optional<MailboxId> mailboxIdInbox1 = mailboxManager.createMailbox(inbox1, session1);
            mailboxManager.setRights(inbox1,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_2)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition()),
                session1);

            MailboxQuery mailboxQuery = MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build();

            assertThat(mailboxManager.search(mailboxQuery, session2).toStream())
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

            assertThat(mailboxManager.search(mailboxQuery, session2).toStream())
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

            assertThat(mailboxManager.search(mailboxQuery, session2).toStream())
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

            assertThat(mailboxManager.search(mailboxQuery, session2).toStream())
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
                .getId().getMessageId();

            MailboxPath pirouetteFilder = MailboxPath.forUser(USER_1, "PIROUETTE");
            MailboxId pirouetteMailboxId = mailboxManager.createMailbox(pirouetteFilder, session).get();
            MessageManager pirouetteMessageManager = mailboxManager.getMailbox(pirouetteMailboxId, session);

            MessageId pirouetteMessageId = pirouetteMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId().getMessageId();

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .build();


            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
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
                .getId().getMessageId();

            mailboxManager.setRights(delegatedMailboxPath,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_1)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsOnly(messageId);
        }

        @Test
        void searchForMessageShouldReturnMessagesFromMyDelegatedMailboxesWhenAccessibleNamespace() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
            MailboxPath delegatedMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
            MailboxId delegatedMailboxId = mailboxManager.createMailbox(delegatedMailboxPath, sessionFromDelegater).get();
            MessageManager delegatedMessageManager = mailboxManager.getMailbox(delegatedMailboxId, sessionFromDelegater);

            MessageId messageId = delegatedMessageManager
                .appendMessage(AppendCommand.from(message), sessionFromDelegater)
                .getId().getMessageId();

            mailboxManager.setRights(delegatedMailboxPath,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_1)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inNamespace(new AccessibleNamespace())
                .build();

            assertThat(
                Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                    .collectList()
                    .block())
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
                .from(SearchQuery.matchAll())
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
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
                .from(SearchQuery.matchAll())
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
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
                .from(SearchQuery.matchAll())
                .inMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
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
                .from(SearchQuery.matchAll())
                .notInMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
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
                .from(SearchQuery.matchAll())
                .inMailboxes(otherMailboxId)
                .notInMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .isEmpty();
        }

        @Test
        void searchShouldRestrictResultsToTheSuppliedUserNamespace() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
            MailboxPath delegatedMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
            MailboxId delegatedMailboxId = mailboxManager.createMailbox(delegatedMailboxPath, sessionFromDelegater).get();
            MessageManager delegatedMessageManager = mailboxManager.getMailbox(delegatedMailboxId, sessionFromDelegater);

            MessageId messageId = delegatedMessageManager
                .appendMessage(AppendCommand.from(message), sessionFromDelegater)
                .getId().getMessageId();

            mailboxManager.setRights(delegatedMailboxPath,
                MailboxACL.EMPTY.apply(MailboxACL.command()
                    .forUser(USER_1)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition()),
                sessionFromDelegater);

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inNamespace(new PersonalNamespace(session))
                .build();

            assertThat(
                Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                    .collectList()
                    .block())
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
                .getId().getMessageId();

            MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(searchedMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId);
        }

        @Test
        void searchShouldNotReturnNoMoreDelegatedMailboxes() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            Optional<MailboxId> mailboxIdInbox1 = mailboxManager.createMailbox(inbox1, session1);
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

            assertThat(mailboxManager.search(mailboxQuery, session2).toStream())
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
            Optional<MailboxId> mailboxIdInbox1 = mailboxManager.createMailbox(inbox1, session1);
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
                .isEqualTo(MailboxCounters.empty(mailboxIdInbox1.get()));
        }

        @Test
        void getMailboxCountersShouldReturnStoredValueWhenReadRight() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
            MailboxPath inbox1 = MailboxPath.inbox(session1);
            Optional<MailboxId> mailboxIdInbox1 = mailboxManager.createMailbox(inbox1, session1);
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
                    .mailboxId(mailboxIdInbox1.get())
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

            MessageManager.MailboxMetaData metaData = mailboxManager.getMailbox(inbox1, session2)
                .getMetaData(IGNORE, session2, MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT);

            assertSoftly(
                softly -> {
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MailboxMetaData::getHighestModSeq)
                        .isEqualTo(ModSeq.first());
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MailboxMetaData::getUidNext)
                        .isEqualTo(MessageUid.MIN_VALUE);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MailboxMetaData::getMessageCount)
                        .isEqualTo(0L);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MailboxMetaData::getUnseenCount)
                        .isEqualTo(0L);
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MailboxMetaData::getRecent)
                        .isEqualTo(ImmutableList.of());
                    softly.assertThat(metaData)
                        .extracting(MessageManager.MailboxMetaData::getPermanentFlags)
                        .isEqualTo(new Flags());
                });
        }
    }

    @Nested
    public class BasicFeaturesTests {

        @Test
        void renameMailboxShouldReturnAllRenamedResultsIncludeChildren() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx1.mbx2");
            MailboxPath mailboxPath3 = MailboxPath.forUser(USER_1, "mbx1.mbx2.mbx3");
            MailboxPath mailboxPath4 = MailboxPath.forUser(USER_1, "mbx1.mbx2.mbx3.mbx4");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx1.mbx9");

            mailboxManager.createMailbox(mailboxPath1, session);
            Optional<MailboxId> mailboxId2 = mailboxManager.createMailbox(mailboxPath2, session);
            Optional<MailboxId> mailboxId3 = mailboxManager.createMailbox(mailboxPath3, session);
            Optional<MailboxId> mailboxId4 = mailboxManager.createMailbox(mailboxPath4, session);

            List<MailboxRenamedResult> mailboxRenamedResults = mailboxManager.renameMailbox(mailboxPath2, newMailboxPath, session);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mailboxRenamedResults).hasSize(3);
                softly.assertThat(mailboxRenamedResults).contains(
                    new MailboxRenamedResult(mailboxId2.get(), mailboxPath2, MailboxPath.forUser(USER_1, "mbx1.mbx9")),
                    new MailboxRenamedResult(mailboxId3.get(), mailboxPath3, MailboxPath.forUser(USER_1, "mbx1.mbx9.mbx3")),
                    new MailboxRenamedResult(mailboxId4.get(), mailboxPath4, MailboxPath.forUser(USER_1, "mbx1.mbx9.mbx3.mbx4"))
                );
            });
        }

        @Test
        void renameMailboxShouldReturnRenamedMailboxOnlyWhenNoChildren() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx1.mbx2");
            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1.mbx2.mbx3");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx1.mbx2.mbx9");

            mailboxManager.createMailbox(mailboxPath1, session);
            mailboxManager.createMailbox(mailboxPath2, session);
            Optional<MailboxId> mailboxId3 = mailboxManager.createMailbox(originalPath, session);

            List<MailboxRenamedResult> mailboxRenamedResults = mailboxManager.renameMailbox(originalPath, newMailboxPath, session);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mailboxRenamedResults).hasSize(1);
                softly.assertThat(mailboxRenamedResults).contains(
                    new MailboxRenamedResult(mailboxId3.get(), originalPath, newMailboxPath)
                );
            });
        }

        @Test
        void renameMailboxShouldRenamedChildMailboxesWithRenameOption() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx1.mbx2");
            MailboxPath mailboxPath3 = MailboxPath.forUser(USER_1, "mbx1.mbx2.mbx3");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx9");

            mailboxManager.createMailbox(originalPath, session);
            mailboxManager.createMailbox(mailboxPath2, session);
            subscriptionManager.subscribe(session, originalPath);
            subscriptionManager.subscribe(session, mailboxPath2);

            mailboxManager.createMailbox(mailboxPath3, session);
            subscriptionManager.subscribe(session, mailboxPath3);

            mailboxManager.renameMailbox(originalPath, newMailboxPath, RENAME_SUBSCRIPTIONS, session);

            assertThat(subscriptionManager.subscriptions(session)).containsOnly(
                newMailboxPath,
                MailboxPath.forUser(USER_1, "mbx9.mbx2"),
                MailboxPath.forUser(USER_1, "mbx9.mbx2.mbx3"));
        }

        @Test
        void renameMailboxShouldRenameSubscriptionWhenCalledWithRenameSubscriptionOption() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx2");

            mailboxManager.createMailbox(originalPath, session);
            subscriptionManager.subscribe(session, originalPath);

            mailboxManager.renameMailbox(originalPath, newMailboxPath, RENAME_SUBSCRIPTIONS, session);

            assertThat(subscriptionManager.subscriptions(session)).containsExactly(newMailboxPath);
        }

        @Test
        void renameMailboxShouldNotSubscribeUnsubscribedMailboxes() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx2");

            mailboxManager.createMailbox(originalPath, session);

            mailboxManager.renameMailbox(originalPath, newMailboxPath, RENAME_SUBSCRIPTIONS, session);

            assertThat(subscriptionManager.subscriptions(session)).isEmpty();
        }

        @Test
        void renameMailboxShouldNotRenameSubscriptionWhenCalledWithoutRenameSubscriptionOption() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx2");

            mailboxManager.createMailbox(originalPath, session);
            subscriptionManager.subscribe(session, originalPath);

            mailboxManager.renameMailbox(originalPath, newMailboxPath, MailboxManager.RenameOption.NONE, session);

            assertThat(subscriptionManager.subscriptions(session)).containsExactly(originalPath);
        }

        @Test
        void renameMailboxByIdShouldRenamedMailboxesWithRenameOption() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx1.mbx2");
            MailboxPath mailboxPath3 = MailboxPath.forUser(USER_1, "mbx1.mbx2.mbx3");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx9");

            Optional<MailboxId> id = mailboxManager.createMailbox(originalPath, session);
            mailboxManager.createMailbox(mailboxPath2, session);
            subscriptionManager.subscribe(session, originalPath);
            subscriptionManager.subscribe(session, mailboxPath2);

            mailboxManager.createMailbox(mailboxPath3, session);
            subscriptionManager.subscribe(session, mailboxPath3);

            mailboxManager.renameMailbox(id.get(), newMailboxPath, RENAME_SUBSCRIPTIONS, session);

            assertThat(subscriptionManager.subscriptions(session)).containsOnly(
                newMailboxPath,
                MailboxPath.forUser(USER_1, "mbx9.mbx2"),
                MailboxPath.forUser(USER_1, "mbx9.mbx2.mbx3"));
        }

        @Test
        void renameMailboxByIdShouldRenameSubscriptionWhenCalledWithRenameSubscriptionOption() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx2");

            Optional<MailboxId> id = mailboxManager.createMailbox(originalPath, session);
            subscriptionManager.subscribe(session, originalPath);

            mailboxManager.renameMailbox(id.get(), newMailboxPath, RENAME_SUBSCRIPTIONS, session);

            assertThat(subscriptionManager.subscriptions(session)).containsExactly(newMailboxPath);
        }

        @Test
        void renameMailboxByIdShouldNotSubscribeUnsubscribedMailboxes() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx2");

            Optional<MailboxId> id = mailboxManager.createMailbox(originalPath, session);

            mailboxManager.renameMailbox(id.get(), newMailboxPath, RENAME_SUBSCRIPTIONS, session);

            assertThat(subscriptionManager.subscriptions(session)).isEmpty();
        }

        @Test
        void renameMailboxByIdShouldNotRenameSubscriptionWhenCalledWithoutRenameSubscriptionOption() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originalPath = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath newMailboxPath = MailboxPath.forUser(USER_1, "mbx2");

            Optional<MailboxId> id = mailboxManager.createMailbox(originalPath, session);
            subscriptionManager.subscribe(session, originalPath);

            mailboxManager.renameMailbox(id.get(), newMailboxPath, MailboxManager.RenameOption.NONE, session);

            assertThat(subscriptionManager.subscriptions(session)).containsExactly(originalPath);
        }

        @Test
        void user1ShouldNotHaveAnInbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            assertThat(Mono.from(mailboxManager.mailboxExists(inbox, session)).block()).isFalse();
        }

        @Test
        void user1ShouldBeAbleToCreateInbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(inbox, session)).block()).isTrue();
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
        protected void renameMailboxShouldChangeTheMailboxPathOfTheChildMailbox() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx2");
            mailboxManager.createMailbox(mailboxPath1, session);
            MailboxPath mailboxPath1Child = MailboxPath.forUser(USER_1, "mbx1.child");
            Optional<MailboxId> mailboxChildId = mailboxManager.createMailbox(mailboxPath1Child, session);

            mailboxManager.renameMailbox(mailboxPath1, mailboxPath2, session);

            assertThat(mailboxManager.getMailbox(mailboxChildId.get(), session).getMailboxPath())
                    .isEqualTo(MailboxPath.forUser(USER_1, "mbx2.child"));
        }

        @Test
        protected void renameMailboxByIdShouldChangeTheMailboxPathOfAMailbox() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx2");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath1, session);

            mailboxManager.renameMailbox(mailboxId.get(), mailboxPath2, session);

            assertThat(mailboxManager.getMailbox(mailboxId.get(), session).getMailboxPath())
                .isEqualTo(mailboxPath2);
        }

        @Test
        void renameMailboxShouldThrowWhenMailboxPathsDoNotBelongToUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx2");
            mailboxManager.createMailbox(mailboxPath1, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxPath1, mailboxPath2, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void renameMailboxByIdShouldThrowWhenMailboxPathsDoNotBelongToUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_1, "mbx2");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath1, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId.get(), mailboxPath2, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void renameMailboxShouldThrowWhenFromMailboxPathDoesNotBelongToUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_2, "mbx2");
            mailboxManager.createMailbox(mailboxPath1, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxPath1, mailboxPath2, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void renameMailboxByIdShouldThrowWhenFromMailboxPathDoesNotBelongToUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_2, "mbx2");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath1, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId.get(), mailboxPath2, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void renameMailboxShouldThrowWhenToMailboxPathDoesNotBelongToUser() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_2, "mbx2");
            mailboxManager.createMailbox(mailboxPath1, session);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxPath1, mailboxPath2, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void renameMailboxByIdShouldThrowWhenToMailboxPathDoesNotBelongToUser() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath1 = MailboxPath.forUser(USER_1, "mbx1");
            MailboxPath mailboxPath2 = MailboxPath.forUser(USER_2, "mbx2");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath1, session);

            assertThatThrownBy(() -> mailboxManager.renameMailbox(mailboxId.get(), mailboxPath2, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void user1ShouldNotBeAbleToCreateInboxTwice() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThatThrownBy(() -> mailboxManager.createMailbox(inbox, session))
                .isInstanceOf(MailboxExistsException.class);
        }

        @Test
        void user1ShouldNotHaveTestSubmailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(new MailboxPath(inbox, "INBOX.Test"), session)).block()).isFalse();
        }

        @Test
        void user1ShouldBeAbleToCreateTestSubmailbox() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);

            MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
            mailboxManager.createMailbox(inboxSubMailbox, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(inboxSubMailbox, session)).block()).isTrue();
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

            assertThat(Mono.from(mailboxManager.mailboxExists(inbox, session)).block()).isFalse();
            assertThat(Mono.from(mailboxManager.mailboxExists(inboxSubMailbox, session)).block()).isTrue();
        }


        @Test
        protected void user1ShouldBeAbleToDeleteInboxById() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();
            MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
            mailboxManager.createMailbox(inboxSubMailbox, session);

            mailboxManager.deleteMailbox(inboxId, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(inbox, session)).block()).isFalse();
            assertThat(Mono.from(mailboxManager.mailboxExists(inboxSubMailbox, session)).block()).isTrue();
        }

        @Test
        void renamingMailboxByPathShouldThrowWhenFromNotFound() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath originPath = MailboxPath.forUser(USER_1, "origin");
            MailboxPath destinationPath = MailboxPath.forUser(USER_1, "destination");

            assertThatThrownBy(() -> mailboxManager.renameMailbox(originPath, destinationPath, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void renamingMailboxByIdShouldThrowWhenFromNotFound() throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USER_1);

            MailboxPath notFound = MailboxPath.forUser(USER_1, "notFound");

            assertThatThrownBy(() -> mailboxManager.deleteMailbox(notFound, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void user2ShouldNotBeAbleToDeleteUser1Mailbox() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath inbox = MailboxPath.inbox(sessionUser1);
            mailboxManager.createMailbox(inbox, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.deleteMailbox(inbox, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }


        @Test
        void user2ShouldNotBeAbleToDeleteUser1MailboxById() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath inbox = MailboxPath.inbox(sessionUser1);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, sessionUser1).get();

            assertThatThrownBy(() -> mailboxManager.deleteMailbox(inboxId, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
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

            assertThat(Mono.from(mailboxManager.mailboxExists(inbox, session)).block()).isTrue();
            assertThat(Mono.from(mailboxManager.mailboxExists(inboxSubMailbox, session)).block()).isFalse();
        }

        @Test
        protected void user1ShouldBeAbleToDeleteSubmailboxByid() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            mailboxManager.startProcessingRequest(session);

            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session);
            MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
            MailboxId inboxSubMailboxId = mailboxManager.createMailbox(inboxSubMailbox, session).get();

            mailboxManager.deleteMailbox(inboxSubMailboxId, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(inbox, session)).block()).isTrue();
            assertThat(Mono.from(mailboxManager.mailboxExists(inboxSubMailbox, session)).block()).isFalse();
        }

        @Test
        void listShouldReturnMailboxes() throws Exception {
            session = mailboxManager.createSystemSession(Username.of("manager"));
            mailboxManager.startProcessingRequest(session);

            DataProvisioner.feedMailboxManager(mailboxManager);

            assertThat(mailboxManager.list(session)).hasSize(DataProvisioner.EXPECTED_MAILBOXES_COUNT);
        }

        @Test
        void listShouldReturnEmptyListWhenNoMailboxes() throws Exception {
            session = mailboxManager.createSystemSession(Username.of("manager"));

            assertThat(mailboxManager.list(session))
                .isEmpty();
        }

        @Test
        void user2ShouldBeAbleToCreateRootlessFolder() throws MailboxException {
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath trash = MailboxPath.forUser(USER_2, "Trash");
            mailboxManager.createMailbox(trash, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(trash, session)).block()).isTrue();
        }

        @Test
        void user2ShouldBeAbleToCreateNestedFoldersWithoutTheirParents() throws Exception {
            session = mailboxManager.createSystemSession(USER_2);
            MailboxPath nestedFolder = MailboxPath.forUser(USER_2, "INBOX.testfolder");
            mailboxManager.createMailbox(nestedFolder, session);

            assertThat(Mono.from(mailboxManager.mailboxExists(nestedFolder, session)).block()).isTrue();
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
        void moveMessagesShouldMoveAllMessagesFromOneMailboxToAnOtherOfASameUser() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Move));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();

            MailboxPath otherMailbox = MailboxPath.forUser(USER_1, "otherMailbox");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailbox, session).get();

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox, session);

            MessageId messageId1 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();
            MessageId messageId2 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();

            mailboxManager.moveMessages(MessageRange.all(), inbox, otherMailbox, session);

            MultimailboxesSearchQuery inboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(inboxId)
                .build();

            MultimailboxesSearchQuery otherMailboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(inboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .isEmpty();
            assertThat(Flux.from(mailboxManager.search(otherMailboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId1, messageId2);
        }

        @Test
        void moveMessagesShouldMoveOnlyOneMessageFromOneMailboxToAnOtherOfASameUser() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Move));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();

            MailboxPath otherMailbox = MailboxPath.forUser(USER_1, "otherMailbox");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailbox, session).get();

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox, session);

            ComposedMessageId composedMessageId1 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId();
            MessageId messageId2 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();

            mailboxManager.moveMessages(MessageRange.one(composedMessageId1.getUid()), inbox, otherMailbox, session);

            MultimailboxesSearchQuery inboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(inboxId)
                .build();

            MultimailboxesSearchQuery otherMailboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(inboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId2);
            assertThat(Flux.from(mailboxManager.search(otherMailboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(composedMessageId1.getMessageId());
        }

        @Test
        void moveMessagesShouldThrowWhenMovingMessageFromMailboxNotBelongingToSameUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath inbox1 = MailboxPath.inbox(sessionUser1);
            mailboxManager.createMailbox(inbox1, sessionUser1);

            MailboxPath inbox2 = MailboxPath.inbox(sessionUser2);
            mailboxManager.createMailbox(inbox2, sessionUser2);

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox1, sessionUser1);

            inboxMessageManager
                .appendMessage(AppendCommand.from(message), sessionUser1);

            assertThatThrownBy(() -> mailboxManager.moveMessages(MessageRange.all(), inbox1, inbox2, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void moveMessagesShouldThrowWhenMovingMessageToMailboxNotBelongingToSameUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath inbox1 = MailboxPath.inbox(sessionUser1);
            mailboxManager.createMailbox(inbox1, sessionUser1);

            MailboxPath inbox2 = MailboxPath.inbox(sessionUser2);
            mailboxManager.createMailbox(inbox2, sessionUser2);

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox1, sessionUser1);

            inboxMessageManager
                .appendMessage(AppendCommand.from(message), sessionUser1);

            assertThatThrownBy(() -> mailboxManager.moveMessages(MessageRange.all(), inbox1, inbox2, sessionUser1))
                .isInstanceOf(MailboxNotFoundException.class);
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
        void copyMessagesShouldCopyAllMessagesFromOneMailboxToAnOtherOfASameUser() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();

            MailboxPath otherMailbox = MailboxPath.forUser(USER_1, "otherMailbox");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailbox, session).get();

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox, session);

            MessageId messageId1 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();
            MessageId messageId2 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();

            mailboxManager.copyMessages(MessageRange.all(), inbox, otherMailbox, session);

            MultimailboxesSearchQuery inboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(inboxId)
                .build();

            MultimailboxesSearchQuery otherMailboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(inboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId1, messageId2);
            assertThat(Flux.from(mailboxManager.search(otherMailboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId1, messageId2);
        }

        @Test
        void copyMessagesShouldCopyOnlyOneMessageFromOneMailboxToAnOtherOfASameUser() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();

            MailboxPath otherMailbox = MailboxPath.forUser(USER_1, "otherMailbox");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailbox, session).get();

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox, session);

            ComposedMessageId composedMessageId1 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId();
            MessageId messageId2 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();

            MessageId messageId1 = composedMessageId1.getMessageId();

            mailboxManager.copyMessages(MessageRange.one(composedMessageId1.getUid()), inbox, otherMailbox, session);

            MultimailboxesSearchQuery inboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(inboxId)
                .build();

            MultimailboxesSearchQuery otherMailboxQuery = MultimailboxesSearchQuery
                .from(SearchQuery.matchAll())
                .inMailboxes(otherMailboxId)
                .build();

            assertThat(Flux.from(mailboxManager.search(inboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId1, messageId2);
            assertThat(Flux.from(mailboxManager.search(otherMailboxQuery, session, DEFAULT_MAXIMUM_LIMIT))
                .collectList().block())
                .containsExactly(messageId1);
        }

        @Test
        void copyMessagesShouldUpdateMailboxCounts() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();

            MailboxPath otherMailbox = MailboxPath.forUser(USER_1, "otherMailbox");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailbox, session).get();

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox, session);

            ComposedMessageId composedMessageId1 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId();
            MessageId messageId2 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();

            MessageId messageId1 = composedMessageId1.getMessageId();

            mailboxManager.copyMessages(MessageRange.all(), inbox, otherMailbox, session);

            assertThat(mailboxManager.getMailbox(inboxId, session).getMessageCount(session))
                .isEqualTo(2);
            assertThat(mailboxManager.getMailbox(otherMailboxId, session).getMessageCount(session))
                .isEqualTo(2);
        }

        @Test
        void moveMessagesShouldUpdateMailboxCounts() throws Exception {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath inbox = MailboxPath.inbox(session);
            MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();

            MailboxPath otherMailbox = MailboxPath.forUser(USER_1, "otherMailbox");
            MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailbox, session).get();

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox, session);

            ComposedMessageId composedMessageId1 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId();
            MessageId messageId2 = inboxMessageManager
                .appendMessage(AppendCommand.from(message), session)
                .getId()
                .getMessageId();

            MessageId messageId1 = composedMessageId1.getMessageId();

            mailboxManager.moveMessages(MessageRange.all(), inbox, otherMailbox, session);

            assertThat(mailboxManager.getMailbox(inboxId, session).getMessageCount(session))
                .isEqualTo(0);
            assertThat(mailboxManager.getMailbox(otherMailboxId, session).getMessageCount(session))
                .isEqualTo(2);
        }

        @Test
        void copyMessagesShouldThrowWhenCopyingMessageFromMailboxNotBelongingToSameUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath inbox1 = MailboxPath.inbox(sessionUser1);
            mailboxManager.createMailbox(inbox1, sessionUser1);

            MailboxPath inbox2 = MailboxPath.inbox(sessionUser2);
            mailboxManager.createMailbox(inbox2, sessionUser2);

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox1, sessionUser1);

            inboxMessageManager
                .appendMessage(AppendCommand.from(message), sessionUser1);

            assertThatThrownBy(() -> mailboxManager.copyMessages(MessageRange.all(), inbox1, inbox2, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void copyMessagesShouldThrowWhenCopyingMessageToMailboxNotBelongingToSameUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath inbox1 = MailboxPath.inbox(sessionUser1);
            mailboxManager.createMailbox(inbox1, sessionUser1);

            MailboxPath inbox2 = MailboxPath.inbox(sessionUser2);
            mailboxManager.createMailbox(inbox2, sessionUser2);

            MessageManager inboxMessageManager = mailboxManager.getMailbox(inbox1, sessionUser1);

            inboxMessageManager
                .appendMessage(AppendCommand.from(message), sessionUser1);

            assertThatThrownBy(() -> mailboxManager.copyMessages(MessageRange.all(), inbox1, inbox2, sessionUser1))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void createMailboxShouldNotThrowWhenMailboxPathBelongsToUser() throws MailboxException {
            session = mailboxManager.createSystemSession(USER_1);
            Optional<MailboxId> mailboxId = mailboxManager
                .createMailbox(MailboxPath.forUser(USER_1, "mailboxName"), session);

            assertThat(mailboxId).isNotEmpty();
        }

        @Test
        void createMailboxShouldThrowWhenMailboxPathBelongsToAnotherUser() {
            session = mailboxManager.createSystemSession(USER_1);

            assertThatThrownBy(() -> mailboxManager
                    .createMailbox(MailboxPath.forUser(USER_2, "mailboxName"), session))
                .isInstanceOf(InsufficientRightsException.class);
        }

        @Test
        void getMailboxShouldThrowWhenMailboxDoesNotExist() {
            session = mailboxManager.createSystemSession(USER_1);

            assertThatThrownBy(() -> mailboxManager.getMailbox(MailboxPath.forUser(USER_1, "mailboxName"), session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void getMailboxByPathShouldReturnMailboxWhenBelongingToUser() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "mailboxName");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);

            assertThat(mailboxManager.getMailbox(mailboxPath, session).getId())
                .isEqualTo(mailboxId.get());
        }

        @Test
        protected void getMailboxByIdShouldReturnMailboxWhenBelongingToUser() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "mailboxName");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);

            assertThat(mailboxManager.getMailbox(mailboxId.get(), session).getId())
                .isEqualTo(mailboxId.get());
        }

        @Test
        void getMailboxByPathShouldThrowWhenMailboxNotBelongingToUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "mailboxName");
            mailboxManager.createMailbox(mailboxPath, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.getMailbox(mailboxPath, sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void getMailboxByIdShouldThrowWhenMailboxNotBelongingToUser() throws Exception {
            MailboxSession sessionUser1 = mailboxManager.createSystemSession(USER_1);
            MailboxSession sessionUser2 = mailboxManager.createSystemSession(USER_2);

            MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "mailboxName");
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, sessionUser1);

            assertThatThrownBy(() -> mailboxManager.getMailbox(mailboxId.get(), sessionUser2))
                .isInstanceOf(MailboxNotFoundException.class);
        }
    }

    @Nested
    class SessionTests {
        @Test
        void createUser1SystemSessionShouldReturnValidSession() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);

            assertThat(session.getUser()).isEqualTo(USER_1);
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
                    .build(message), session).getId();
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
                        softy.assertThat(deleteMetadata.getMessageId()).isEqualTo(composeId.getMessageId());
                    }));
            }

            @Test
            void deleteMailboxShouldCallAllPreDeletionHooks() throws Exception {
                ComposedMessageId composeId = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();
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
                        softy.assertThat(deleteMetadata.getMessageId()).isEqualTo(composeId.getMessageId());
                    }));
            }

            @Test
            void deleteMailboxByIdShouldCallAllPreDeletionHooks() throws Exception {
                ComposedMessageId composeId = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();
                mailboxManager.deleteMailbox(inboxId, session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(1)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(1)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getValue().getDeletionMetadataList())
                    .hasSize(1)
                    .hasSameElementsAs(preDeleteCaptor2.getValue().getDeletionMetadataList())
                    .allSatisfy(deleteMetadata -> SoftAssertions.assertSoftly(softy -> {
                        softy.assertThat(deleteMetadata.getMailboxId()).isEqualTo(inboxId);
                        softy.assertThat(deleteMetadata.getMessageId()).isEqualTo(composeId.getMessageId());
                    }));
            }

            @Test
            void expungeShouldCallAllPreDeletionHooksOnEachMessageDeletionCall() throws Exception {
                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();
                ComposedMessageId composeId2 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();

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
                    .extracting(deleteMetadata -> deleteMetadata.getMessageId())
                    .containsOnly(composeId1.getMessageId(), composeId2.getMessageId());
            }

            @Test
            void expungeShouldCallAllPreDeletionHooksOnlyOnMessagesMarkedAsDeleted() throws Exception {
                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();
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
                        softy.assertThat(deleteMetadata.getMessageId()).isEqualTo(composeId1.getMessageId());
                    }));
            }

            @Test
            void expungeShouldNotCallPredeletionHooksWhenNoMessagesMarkedAsDeleted() throws Exception {
                inboxManager.appendMessage(AppendCommand.builder()
                    .build(message), session);

                inboxManager.expunge(MessageRange.all(), session);

                verifyNoMoreInteractions(preDeletionHook1);
                verifyNoMoreInteractions(preDeletionHook2);
            }

            @Test
            void expungeShouldCallAllPreDeletionHooksOnEachMessageDeletionOnDifferentMailboxes() throws Exception {
                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();
                ComposedMessageId composeId2 = anotherMailboxManager.appendMessage(AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.DELETED))
                    .build(message), session).getId();

                inboxManager.expunge(MessageRange.one(composeId1.getUid()), session);
                anotherMailboxManager.expunge(MessageRange.one(composeId2.getUid()), session);

                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor1 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                ArgumentCaptor<PreDeletionHook.DeleteOperation> preDeleteCaptor2 = ArgumentCaptor.forClass(PreDeletionHook.DeleteOperation.class);
                verify(preDeletionHook1, times(2)).notifyDelete(preDeleteCaptor1.capture());
                verify(preDeletionHook2, times(2)).notifyDelete(preDeleteCaptor2.capture());

                assertThat(preDeleteCaptor1.getAllValues())
                    .hasSameElementsAs(preDeleteCaptor2.getAllValues())
                    .flatExtracting(PreDeletionHook.DeleteOperation::getDeletionMetadataList)
                    .extracting(deleteMetadata -> deleteMetadata.getMessageId())
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
                    .build(message), session).getId();
                assertThatThrownBy(() -> inboxManager.expunge(MessageRange.one(composeId1.getUid()), session))
                    .isInstanceOf(RuntimeException.class);

                assertThat(ImmutableList.copyOf(inboxManager.getMessages(MessageRange.one(composeId1.getUid()), FetchGroup.MINIMAL, session))
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

                ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder().build(message), session).getId();
                inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD,
                    MessageRange.one(composeId1.getUid()), session);
                inboxManager.expunge(MessageRange.all(), session);

                latchForHook1.await();
                latchForHook2.await();

                assertThat(inboxManager.getMessages(MessageRange.one(composeId1.getUid()), FetchGroup.MINIMAL, session))
                    .toIterable()
                    .isEmpty();
            }
        }
    }

    @Nested
    class MessageTests {
        private MessageManager inboxManager;

        @BeforeEach
        void setUp() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session).get();
            inboxManager = mailboxManager.getMailbox(inbox, session);
        }

        @Test
        void listMessagesMetadataShouldReturnEmptyWhenNoMessages() {
            assertThat(Flux.from(inboxManager.listMessagesMetadata(MessageRange.all(), session))
                .collectList().block())
                .isEmpty();
        }

        @Test
        void listMessagesMetadataShouldReturnAppendedMessage() throws Exception {
            Flags flags = new Flags(Flags.Flag.DELETED);
            ComposedMessageId composeId = inboxManager.appendMessage(AppendCommand.builder()
                .withFlags(flags)
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/twoAttachmentsApi.eml")), session).getId();

            assertThat(Flux.from(inboxManager.listMessagesMetadata(MessageRange.all(), session))
                    .collectList().block())
                .hasSize(1)
                .allSatisfy(ids -> SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(ids.getComposedMessageId().getMailboxId()).isEqualTo(composeId.getMailboxId());
                    softly.assertThat(ids.getComposedMessageId().getUid()).isEqualTo(composeId.getUid());
                    softly.assertThat(ids.getFlags()).isEqualTo(flags);
                }));
        }

        @Test
        void listMessagesMetadataShouldReturnUpdatedMessage() throws Exception {
            Flags flags = new Flags(Flags.Flag.SEEN);
            ComposedMessageId composeId = inboxManager.appendMessage(AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.DELETED))
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/twoAttachmentsApi.eml")), session).getId();

            inboxManager.setFlags(flags, REPLACE, MessageRange.all(), session);

            assertThat(Flux.from(inboxManager.listMessagesMetadata(MessageRange.all(), session))
                    .collectList().block())
                .hasSize(1)
                .allSatisfy(ids -> SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(ids.getComposedMessageId().getMailboxId()).isEqualTo(composeId.getMailboxId());
                    softly.assertThat(ids.getComposedMessageId().getUid()).isEqualTo(composeId.getUid());
                    softly.assertThat(ids.getFlags()).isEqualTo(flags);
                }));
        }

        @Test
        void listMessagesMetadataShouldNotReturnDeletedMessage() throws Exception {
            inboxManager.appendMessage(AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.DELETED))
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/twoAttachmentsApi.eml")), session).getId();

            inboxManager.expunge(MessageRange.all(), session);

            assertThat(Flux.from(inboxManager.listMessagesMetadata(MessageRange.all(), session))
                    .collectList().block())
                .isEmpty();
        }

        @Test
        void shouldBeAbleToAccessThreadIdOfAMessageAndThatThreadIdShouldWrapsMessageId() throws Exception {
            ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder().build(message), session).getId();
            MessageResult messageResult = inboxManager.getMessages(MessageRange.one(composeId1.getUid()), FetchGroup.MINIMAL, session).next();
            assertThat(messageResult.getThreadId().getBaseMessageId()).isInstanceOf(MessageId.class);
        }
    }

    @Nested
    class SaveDateTests {
        private MessageManager inboxManager;

        @BeforeEach
        void setUp() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            MailboxPath inbox = MailboxPath.inbox(session);
            mailboxManager.createMailbox(inbox, session).get();
            inboxManager = mailboxManager.getMailbox(inbox, session);
        }

        @Test
        void shouldSetSaveDateWhenAppendMessage() throws Exception {
            ComposedMessageId composeId1 = inboxManager.appendMessage(AppendCommand.builder().build(message), session).getId();
            MessageResult messageResult = inboxManager.getMessages(MessageRange.one(composeId1.getUid()), FetchGroup.MINIMAL, session).next();
            assertThat(messageResult.getSaveDate()).isPresent();
        }
    }

    @Nested
    class RightTests {

        private MailboxSession session2;

        @BeforeEach
        void setUp() {
            assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

            session = mailboxManager.createSystemSession(USER_1);
            session2 = mailboxManager.createSystemSession(USER_2);
        }

        @Test
        void hasRightShouldThrowOnUnknownMailbox() {
            assertThatThrownBy(() -> mailboxManager.hasRight(
                    MailboxPath.forUser(USER_1, "notFound"),
                    MailboxACL.Right.Administer,
                    session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void listRightsShouldThrowOnUnknownMailbox() {
            assertThatThrownBy(() -> mailboxManager.listRights(
                    MailboxPath.forUser(USER_1, "notFound"),
                    session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void myRightsShouldThrowOnUnknownMailbox() {
            assertThatThrownBy(() -> mailboxManager.myRights(
                    MailboxPath.forUser(USER_1, "notFound"),
                    session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void listRightsForEntryShouldThrowOnUnknownMailbox() {
            assertThatThrownBy(() -> mailboxManager.listRights(
                    MailboxPath.forUser(USER_1, "notFound"),
                    MailboxACL.EntryKey.createUserEntryKey(USER_2),
                    session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void setRightsShouldNotThrowOnUnknownMailbox() {
            assertThatCode(() -> mailboxManager.setRights(
                    MailboxPath.forUser(USER_1, "notFound"),
                    MailboxACL.EMPTY,
                    session))
                .doesNotThrowAnyException();
        }

        @Test
        void hasRightShouldThrowOnDeletedMailbox() throws Exception {
            MailboxId id = mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "deleted"), session).get();
            mailboxManager.deleteMailbox(id, session);

            assertThatThrownBy(() -> mailboxManager.hasRight(id, MailboxACL.Right.Administer, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void myRightsShouldThrowOnDeletedMailbox() throws Exception {
            MailboxId id = mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "deleted"), session).get();
            mailboxManager.deleteMailbox(id, session);

            assertThatThrownBy(() -> Mono.from(mailboxManager.myRights(id, session)).blockOptional())
                .hasCauseInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void setRightsShouldThrowOnDeletedMailbox() throws Exception {
            MailboxId id = mailboxManager.createMailbox(MailboxPath.forUser(USER_1, "deleted"), session).get();
            mailboxManager.deleteMailbox(id, session);

            assertThatThrownBy(() -> mailboxManager.setRights(id, MailboxACL.EMPTY, session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void setRightsByIdShouldThrowWhenNotOwner() throws Exception {
            MailboxId id = mailboxManager.createMailbox(MailboxPath.forUser(USER_2, "mailbox"), session2).get();
            mailboxManager.setRights(id,  MailboxACL.EMPTY.apply(MailboxACL.command()
                .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                .rights(new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup))
                .asAddition()), session2);

            assertThatThrownBy(() -> mailboxManager.setRights(id, MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition()), session))
                .isInstanceOf(InsufficientRightsException.class);
        }

        @Test
        void setRightsByPathShouldThrowWhenNotOwner() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            mailboxManager.createMailbox(mailboxPath, session2).get();
            mailboxManager.setRights(mailboxPath,  MailboxACL.EMPTY.apply(MailboxACL.command()
                .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                .rights(new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup))
                .asAddition()), session2);

            assertThatThrownBy(() -> mailboxManager.setRights(mailboxPath, MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition()), session))
                .isInstanceOf(InsufficientRightsException.class);
        }

        @Test
        void applyRightsCommandShouldThrowWhenNotOwner() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            mailboxManager.createMailbox(mailboxPath, session2).get();
            mailboxManager.setRights(mailboxPath,  MailboxACL.EMPTY.apply(MailboxACL.command()
                .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                .rights(new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup))
                .asAddition()), session2);

            assertThatThrownBy(() -> mailboxManager.applyRightsCommand(mailboxPath,
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition(), session))
                .isInstanceOf(InsufficientRightsException.class);
        }

        @Test
        void applyRightsCommandByIdShouldThrowWhenNotOwner() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session2).get();
            mailboxManager.setRights(mailboxPath,  MailboxACL.EMPTY.apply(MailboxACL.command()
                .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                .rights(new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup))
                .asAddition()), session2);

            assertThatThrownBy(() -> mailboxManager.applyRightsCommand(mailboxId,
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition(), session))
                .isInstanceOf(InsufficientRightsException.class);
        }

        @Test
        void setRightsByIdShouldThrowWhenNoRights() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session2).get();

            assertThatThrownBy(() -> mailboxManager.setRights(mailboxId, MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition()), session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void setRightsByPathShouldThrowWhenNoRights() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            mailboxManager.createMailbox(mailboxPath, session2).get();

            assertThatThrownBy(() -> mailboxManager.setRights(mailboxPath, MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition()), session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void applyRightsCommandShouldThrowWhenNoRights() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            mailboxManager.createMailbox(mailboxPath, session2).get();

            assertThatThrownBy(() -> mailboxManager.applyRightsCommand(mailboxPath,
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition(), session))
                .isInstanceOf(MailboxNotFoundException.class);
        }

        @Test
        void applyRightsCommandByIdShouldThrowWhenNoRights() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USER_2, "mailbox");
            MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session2).get();

            assertThatThrownBy(() -> mailboxManager.applyRightsCommand(mailboxId,
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(USER_1))
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition(), session))
                .isInstanceOf(MailboxNotFoundException.class);
        }
    }
}
