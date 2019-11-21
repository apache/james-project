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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class DefaultUserQuotaRootResolverTest {

    private static final Username BENWA = Username.of("benwa");
    private static final MailboxPath MAILBOX_PATH = MailboxPath.inbox(BENWA);
    public static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, 10);
    private static final MailboxPath PATH_LIKE = MailboxPath.forUser(BENWA, "%");
    private static final MailboxPath MAILBOX_PATH_2 = MailboxPath.forUser(BENWA, "test");
    private static final Mailbox MAILBOX_2 = new Mailbox(MAILBOX_PATH_2, 10);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&benwa", Optional.empty());
    private static final MailboxId MAILBOX_ID = TestId.of(42);
    public static final MailboxSession MAILBOX_SESSION = null;

    private DefaultUserQuotaRootResolver testee;
    private MailboxSessionMapperFactory mockedFactory;

    @Before
    public void setUp() {
        mockedFactory = mock(MailboxSessionMapperFactory.class);
        testee = new DefaultUserQuotaRootResolver(mock(SessionProvider.class), mockedFactory);
    }

    @Test
    public void getQuotaRootShouldReturnUserRelatedQuotaRoot() {
        assertThat(testee.getQuotaRoot(MAILBOX_PATH)).isEqualTo(QUOTA_ROOT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getQuotaRootShouldThrowWhenNamespaceContainsSeparator() {
        testee.getQuotaRoot(new MailboxPath("#pr&ivate", BENWA, "INBOX"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getQuotaRootShouldThrowWhenUserContainsSeparator() {
        testee.getQuotaRoot(MailboxPath.forUser(Username.of("ben&wa"), "INBOX"));
    }

    @Test
    public void getQuotaRootShouldWorkWhenUserIsNull() {
        QuotaRoot quotaRoot = testee.getQuotaRoot(new MailboxPath("#private", null, "INBOX"));

        assertThat(quotaRoot).isEqualTo(QuotaRoot.quotaRoot("#private", Optional.empty()));
    }

    @Test
    public void retrieveAssociatedMailboxesShouldWork() throws Exception {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(MAILBOX_SESSION)).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxWithPathLike(any())).thenReturn(Lists.newArrayList(MAILBOX, MAILBOX_2));

        assertThat(testee.retrieveAssociatedMailboxes(QUOTA_ROOT, MAILBOX_SESSION)).containsOnly(MAILBOX, MAILBOX_2);
    }

    @Test(expected = MailboxException.class)
    public void retrieveAssociatedMailboxesShouldThrowWhenQuotaRootContainsSeparator2Times() throws Exception {
        testee.retrieveAssociatedMailboxes(QuotaRoot.quotaRoot("#private&be&nwa", Optional.empty()), MAILBOX_SESSION);
    }

    @Test
    public void getQuotaRootShouldReturnUserValueWhenCalledWithMailboxId() throws Exception {
        MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(any())).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxById(MAILBOX_ID)).thenReturn(MAILBOX);

        assertThat(testee.getQuotaRoot(MAILBOX_ID)).isEqualTo(QUOTA_ROOT);
    }

}
