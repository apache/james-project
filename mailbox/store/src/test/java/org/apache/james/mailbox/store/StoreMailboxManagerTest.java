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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.NotAdminException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class StoreMailboxManagerTest {
    static final Username CURRENT_USER = Username.of("user");
    static final String CURRENT_USER_PASSWORD = "secret";
    static final Username ADMIN = Username.of("admin");
    static final String ADMIN_PASSWORD = "adminsecret";
    static final MailboxId MAILBOX_ID = TestId.of(123);
    static final Username UNKNOWN_USER = Username.of("otheruser");
    static final String BAD_PASSWORD = "badpassword";
    static final String EMPTY_PREFIX = "";
    static final double DEFAULT_JITTER_FACTOR = 0.5;
    static final java.time.Duration DEFAULT_FIRST_BACKOFF = java.time.Duration.ofMillis(5);
    public static final RetryBackoffConfiguration RETRY_BACKOFF_CONFIGURATION = RetryBackoffConfiguration.builder()
        .maxRetries(3)
        .firstBackoff(DEFAULT_FIRST_BACKOFF)
        .jitterFactor(DEFAULT_JITTER_FACTOR)
        .build();

    StoreMailboxManager storeMailboxManager;
    MailboxMapper mockedMailboxMapper;
    MailboxSession mockedMailboxSession;

    @BeforeEach
    void setUp() throws MailboxException {
        MailboxSessionMapperFactory mockedMapperFactory = mock(MailboxSessionMapperFactory.class);
        mockedMailboxSession = MailboxSessionUtil.create(CURRENT_USER);
        mockedMailboxMapper = mock(MailboxMapper.class);
        when(mockedMapperFactory.getMailboxMapper(mockedMailboxSession))
            .thenReturn(mockedMailboxMapper);
        Factory messageIdFactory = mock(MessageId.Factory.class);
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(CURRENT_USER, CURRENT_USER_PASSWORD);
        authenticator.addUser(ADMIN, ADMIN_PASSWORD);

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());

        StoreRightManager storeRightManager = new StoreRightManager(mockedMapperFactory, new UnionMailboxACLResolver(),
                                                                    new SimpleGroupMembershipResolver(), eventBus);

        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mockedMapperFactory, storeRightManager);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, FakeAuthorizator.forUserAndAdmin(ADMIN, CURRENT_USER));
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mockedMapperFactory);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mockedMapperFactory, mockedMapperFactory, new DefaultTextExtractor(), attachmentContentLoader);

        storeMailboxManager = new StoreMailboxManager(mockedMapperFactory, sessionProvider,
                new JVMMailboxPathLocker(), new MessageParser(), messageIdFactory,
                annotationManager, eventBus, storeRightManager, quotaComponents, index, MailboxManagerConfiguration.DEFAULT,
                PreDeletionHooks.NO_PRE_DELETION_HOOK);
    }

    @Test
    void getMailboxShouldThrowWhenUnknownId() {
        when(mockedMailboxMapper.findMailboxById(MAILBOX_ID)).thenReturn(Mono.error(new MailboxNotFoundException(MAILBOX_ID)));

        assertThatThrownBy(() -> storeMailboxManager.getMailbox(MAILBOX_ID, mockedMailboxSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void getMailboxShouldReturnMailboxManagerWhenKnownId() throws Exception {
        Mailbox mockedMailbox = mock(Mailbox.class);
        when(mockedMailbox.generateAssociatedPath())
            .thenReturn(MailboxPath.forUser(CURRENT_USER, "mailboxName"));
        when(mockedMailbox.getMailboxId()).thenReturn(MAILBOX_ID);
        when(mockedMailboxMapper.findMailboxById(MAILBOX_ID)).thenReturn(Mono.just(mockedMailbox));

        MessageManager expected = storeMailboxManager.getMailbox(MAILBOX_ID, mockedMailboxSession);

        assertThat(expected.getId()).isEqualTo(MAILBOX_ID);
    }

    @Test
    void getMailboxShouldReturnMailboxManagerWhenKnownIdAndDifferentCaseUser() throws Exception {
        Mailbox mockedMailbox = mock(Mailbox.class);
        when(mockedMailbox.generateAssociatedPath())
            .thenReturn(MailboxPath.forUser(Username.of("uSEr"), "mailboxName"));
        when(mockedMailbox.getMailboxId()).thenReturn(MAILBOX_ID);
        when(mockedMailboxMapper.findMailboxById(MAILBOX_ID)).thenReturn(Mono.just(mockedMailbox));

        MessageManager expected = storeMailboxManager.getMailbox(MAILBOX_ID, mockedMailboxSession);

        assertThat(expected.getId()).isEqualTo(MAILBOX_ID);
    }

    @Test
    void getMailboxShouldThrowWhenMailboxDoesNotMatchUserWithoutRight() {
        Username otherUser = Username.of("other.user");
        Mailbox mockedMailbox = mock(Mailbox.class);
        when(mockedMailbox.getACL()).thenReturn(new MailboxACL());
        when(mockedMailbox.generateAssociatedPath())
            .thenReturn(MailboxPath.forUser(otherUser, "mailboxName"));
        when(mockedMailbox.getMailboxId()).thenReturn(MAILBOX_ID);
        when(mockedMailbox.getUser()).thenReturn(otherUser);
        when(mockedMailboxMapper.findMailboxById(MAILBOX_ID)).thenReturn(Mono.just(mockedMailbox));
        when(mockedMailboxMapper.findMailboxByPath(any())).thenReturn(Mono.just(mockedMailbox));

        assertThatThrownBy(() -> storeMailboxManager.getMailbox(MAILBOX_ID, mockedMailboxSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void loginShouldCreateSessionWhenGoodPassword() throws Exception {
        MailboxSession expected = storeMailboxManager.login(CURRENT_USER, CURRENT_USER_PASSWORD);

        assertThat(expected.getUser()).isEqualTo(CURRENT_USER);
    }

    @Test
    void loginShouldThrowWhenBadPassword() {
        assertThatThrownBy(() -> storeMailboxManager.login(CURRENT_USER, BAD_PASSWORD))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginAsOtherUserShouldNotCreateUserSessionWhenAdminWithBadPassword() {
        assertThatThrownBy(() -> storeMailboxManager.loginAsOtherUser(ADMIN, BAD_PASSWORD, CURRENT_USER))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginAsOtherUserShouldNotCreateUserSessionWhenNotAdmin() {
        assertThatThrownBy(() -> storeMailboxManager.loginAsOtherUser(CURRENT_USER, CURRENT_USER_PASSWORD, UNKNOWN_USER))
            .isInstanceOf(NotAdminException.class);
    }

    @Test
    void loginAsOtherUserShouldThrowBadCredentialWhenBadPasswordAndNotAdminUser() {
        assertThatThrownBy(() -> storeMailboxManager.loginAsOtherUser(CURRENT_USER, BAD_PASSWORD, CURRENT_USER))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginAsOtherUserShouldThrowBadCredentialWhenBadPasswordNotAdminUserAndUnknownUser() {
        assertThatThrownBy(() -> storeMailboxManager.loginAsOtherUser(CURRENT_USER, BAD_PASSWORD, UNKNOWN_USER))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginAsOtherUserShouldThrowBadCredentialsWhenBadPasswordAndUserDoesNotExists() {
        assertThatThrownBy(() -> storeMailboxManager.loginAsOtherUser(ADMIN, BAD_PASSWORD, UNKNOWN_USER))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginAsOtherUserShouldNotCreateUserSessionWhenDelegatedUserDoesNotExist() {
        assertThatThrownBy(() -> storeMailboxManager.loginAsOtherUser(ADMIN, ADMIN_PASSWORD, UNKNOWN_USER))
            .isInstanceOf(UserDoesNotExistException.class);
    }

    @Test
    void loginAsOtherUserShouldCreateUserSessionWhenAdminWithGoodPassword() throws Exception {
        MailboxSession expected = storeMailboxManager.loginAsOtherUser(ADMIN, ADMIN_PASSWORD, CURRENT_USER);

        assertThat(expected.getUser()).isEqualTo(CURRENT_USER);
    }

    @Test
    void getPathLikeShouldReturnUserPathLikeWhenNoPrefixDefined() {
        //Given
        MailboxSession session = MailboxSessionUtil.create(CURRENT_USER);
        MailboxQuery.Builder testee = MailboxQuery.builder()
            .expression(new PrefixedRegex(EMPTY_PREFIX, "abc", session.getPathDelimiter()));
        //When
        MailboxQuery mailboxQuery = testee.build();

        assertThat(StoreMailboxManager.toSingleUserQuery(mailboxQuery, session))
            .isEqualTo(MailboxQuery.builder()
                .namespace(MailboxConstants.USER_NAMESPACE)
                .username(Username.of("user"))
                .expression(new PrefixedRegex(EMPTY_PREFIX, "abc*", session.getPathDelimiter()))
                .build()
                .asUserBound());
    }

    @Test
    void getPathLikeShouldReturnUserPathLikeWhenPrefixDefined() {
        //Given
        MailboxSession session = MailboxSessionUtil.create(CURRENT_USER);
        MailboxQuery.Builder testee = MailboxQuery.builder()
            .expression(new PrefixedRegex("prefix.", "abc", session.getPathDelimiter()));

        //When
        MailboxQuery mailboxQuery = testee.build();

        assertThat(StoreMailboxManager.toSingleUserQuery(mailboxQuery, session))
            .isEqualTo(MailboxQuery.builder()
                .namespace(MailboxConstants.USER_NAMESPACE)
                .username(Username.of("user"))
                .expression(new PrefixedRegex("prefix.", "abc*", session.getPathDelimiter()))
                .build()
                .asUserBound());
    }
}

