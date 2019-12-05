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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class DefaultUserQuotaRootResolverTest {

    static final Username BENWA = Username.of("benwa");
    static final MailboxPath MAILBOX_PATH = MailboxPath.inbox(BENWA);
    static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, 10);
    static final MailboxPath MAILBOX_PATH_2 = MailboxPath.forUser(BENWA, "test");
    static final Mailbox MAILBOX_2 = new Mailbox(MAILBOX_PATH_2, 10);
    static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&benwa", Optional.empty());
    static final MailboxId MAILBOX_ID = TestId.of(42);
    static final MailboxSession MAILBOX_SESSION = null;

    DefaultUserQuotaRootResolver testee;
    MailboxSessionMapperFactory mockedFactory;

    @BeforeEach
    void setUp() {
        mockedFactory = mock(MailboxSessionMapperFactory.class);
        testee = new DefaultUserQuotaRootResolver(mock(SessionProvider.class), mockedFactory);
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
    void getQuotaRootShouldThrowWhenUserContainsSeparator() {
        assertThatThrownBy(() -> testee.getQuotaRoot(MailboxPath.forUser(Username.of("ben&wa"), "INBOX")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQuotaRootShouldWorkWhenUserIsNull() {
        QuotaRoot quotaRoot = testee.getQuotaRoot(new MailboxPath("#private", null, "INBOX"));

        assertThat(quotaRoot).isEqualTo(QuotaRoot.quotaRoot("#private", Optional.empty()));
    }

    @Test
    void retrieveAssociatedMailboxesShouldWork() throws Exception {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(MAILBOX_SESSION)).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxWithPathLike(any())).thenReturn(Lists.newArrayList(MAILBOX, MAILBOX_2));

        assertThat(testee.retrieveAssociatedMailboxes(QUOTA_ROOT, MAILBOX_SESSION)).containsOnly(MAILBOX, MAILBOX_2);
    }

    @Test
    void retrieveAssociatedMailboxesShouldThrowWhenQuotaRootContainsSeparator2Times() throws Exception {
        assertThatThrownBy(() -> testee.retrieveAssociatedMailboxes(
                QuotaRoot.quotaRoot("#private&be&nwa", Optional.empty()), MAILBOX_SESSION))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    void getQuotaRootShouldReturnUserValueWhenCalledWithMailboxId() throws Exception {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(any())).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxById(MAILBOX_ID)).thenReturn(MAILBOX);

        assertThat(testee.getQuotaRoot(MAILBOX_ID)).isEqualTo(QUOTA_ROOT);
    }

}
