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

package org.apache.james.mailbox.store.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DefaultUserQuotaRootResolverTest {
    static final Username BENWA = Username.of("benwa");
    static final MailboxId MAILBOX_ID = TestId.of(42);
    static final MailboxPath MAILBOX_PATH = MailboxPath.inbox(BENWA);
    static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UidValidity.of(10), MAILBOX_ID);
    static final MailboxPath MAILBOX_PATH_2 = MailboxPath.forUser(BENWA, "test");
    static final Mailbox MAILBOX_2 = new Mailbox(MAILBOX_PATH_2, UidValidity.of(10), MAILBOX_ID);
    static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&benwa", Optional.empty());
    static final MailboxSession MAILBOX_SESSION = null;

    DefaultUserQuotaRootResolver testee;
    MailboxSessionMapperFactory mockedFactory;

    SessionProvider mockSessionProvider;

    @BeforeEach
    void setUp() {
        mockedFactory = mock(MailboxSessionMapperFactory.class);
        mockSessionProvider = mock(SessionProvider.class);
        testee = new DefaultUserQuotaRootResolver(mockSessionProvider, mockedFactory);
    }

    @Test
    void getQuotaRootShouldReturnUserRelatedQuotaRoot() {
        assertThat(testee.getQuotaRoot(MAILBOX_PATH)).isEqualTo(QUOTA_ROOT);
    }

    @Test
    void getQuotaRootShouldThrowWhenNamespaceContainsSeparator() {
        assertThatThrownBy(() -> testee.getQuotaRoot(new MailboxPath("#pr&ivate", BENWA, "INBOX")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void associatedUsernameShouldNoopWhenUsernameWithAnd() {
        Username originalUsername = Username.of("ben&wa");
        Username resolvedUsername = testee.associatedUsername(testee.getQuotaRoot(MailboxPath.inbox(originalUsername)));

        assertThat(resolvedUsername).isEqualTo(originalUsername);
    }

    @Test
    void getQuotaRootShouldNoopWhenUsernameWithAnd() {
        QuotaRoot originalQuotaRoot = QuotaRoot.quotaRoot("#private&ben&wa", Optional.empty());
        QuotaRoot resolvedQuotaRoot = testee.getQuotaRoot(MailboxPath.inbox(testee.associatedUsername(originalQuotaRoot)));

        assertThat(resolvedQuotaRoot).isEqualTo(originalQuotaRoot);
    }

    @Test
    void fromStringShouldNoopWhenUsernameWithAnd() throws Exception {
        QuotaRoot originalQuotaRoot = QuotaRoot.quotaRoot("#private&ben&wa", Optional.empty());
        QuotaRoot parsedQuotaRoot = testee.fromString(originalQuotaRoot.getValue());
        assertThat(parsedQuotaRoot).isEqualTo(originalQuotaRoot);
    }

    @Test
    void getQuotaRootShouldWorkWhenUserIsNull() {
        QuotaRoot quotaRoot = testee.getQuotaRoot(new MailboxPath("#private", null, "INBOX"));

        assertThat(quotaRoot).isEqualTo(QuotaRoot.quotaRoot("#private", Optional.empty()));
    }

    @Test
    void retrieveAssociatedMailboxesShouldWork() {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(any())).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxWithPathLike(any())).thenReturn(Flux.just(MAILBOX, MAILBOX_2));

        assertThat(testee.retrieveAssociatedMailboxes(QUOTA_ROOT, MAILBOX_SESSION).collectList().block()).containsOnly(MAILBOX, MAILBOX_2);
    }

    @Test
    void retrieveAssociatedMailboxesShouldSupportQuotaRootContainsSeparator2Times() {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(any())).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxWithPathLike(any())).thenReturn(Flux.just(MAILBOX, MAILBOX_2));

        assertThat(testee.retrieveAssociatedMailboxes(
                QuotaRoot.quotaRoot("#private&be&nwa", Optional.empty()), MAILBOX_SESSION)
            .collectList().block())
            .containsOnly(MAILBOX, MAILBOX_2);
    }

    @Test
    void getQuotaRootShouldReturnUserValueWhenCalledWithMailboxId() {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(any())).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxById(MAILBOX_ID)).thenReturn(Mono.just(MAILBOX));

        assertThat(testee.getQuotaRoot(MAILBOX_ID)).isEqualTo(QUOTA_ROOT);
    }

    @Test
    void listAllAccessibleQuotaRootsShouldReturnQuotaRootOfDelegatedMailboxes() {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        MailboxSession mockSession = mock(MailboxSession.class);
        when(mockSessionProvider.createSystemSession(BENWA)).thenReturn(mockSession);
        when(mockedFactory.getMailboxMapper(mockSession)).thenReturn(mockedMapper);

        Mailbox delegatedMailbox = new Mailbox(MailboxPath.forUser(Username.of("delegated"), "test"),
            UidValidity.of(11), TestId.of(1));
        when(mockedMapper.findNonPersonalMailboxes(any(), any())).thenReturn(Flux.just(delegatedMailbox));

        assertThat(Flux.from(testee.listAllAccessibleQuotaRoots(BENWA)).collectList().block())
            .containsExactlyInAnyOrder(QUOTA_ROOT, testee.getQuotaRoot(delegatedMailbox));
    }

}
