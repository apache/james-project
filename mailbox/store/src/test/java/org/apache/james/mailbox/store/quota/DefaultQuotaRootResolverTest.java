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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class DefaultQuotaRootResolverTest {

    public static final MailboxPath MAILBOX_PATH = MailboxPath.forUser("benwa", "INBOX");
    public static final SimpleMailbox MAILBOX = new SimpleMailbox(MAILBOX_PATH, 10);
    public static final MailboxPath PATH_LIKE = MailboxPath.forUser("benwa", "%");
    public static final MailboxPath MAILBOX_PATH_2 = MailboxPath.forUser("benwa", "test");
    public static final SimpleMailbox MAILBOX_2 = new SimpleMailbox(MAILBOX_PATH_2, 10);
    public static final QuotaRoot QUOTA_ROOT = QuotaRootImpl.quotaRoot("#private&benwa");

    private DefaultQuotaRootResolver testee;
    private MailboxSessionMapperFactory mockedFactory;

    @Before
    public void setUp() {
        mockedFactory = mock(MailboxSessionMapperFactory.class);
        testee = new DefaultQuotaRootResolver(mockedFactory);
    }

    @Test
    public void getQuotaRootShouldReturnUserRelatedQuotaRoot() throws Exception {
        assertThat(testee.getQuotaRoot(MAILBOX_PATH)).isEqualTo(QUOTA_ROOT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getQuotaRootShouldThrowWhenNamespaceContainsSeparator() throws Exception {
        testee.getQuotaRoot(new MailboxPath("#pr&ivate", "benwa", "INBOX"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getQuotaRootShouldThrowWhenUserContainsSeparator() throws Exception {
        testee.getQuotaRoot(MailboxPath.forUser("ben&wa", "INBOX"));
    }

    @Test
    public void retrieveAssociatedMailboxesShouldWork() throws Exception {
        final MailboxMapper mockedMapper = mock(MailboxMapper.class);
        when(mockedFactory.getMailboxMapper(null)).thenReturn(mockedMapper);
        when(mockedMapper.findMailboxWithPathLike(PATH_LIKE)).thenReturn(Lists.newArrayList(MAILBOX, MAILBOX_2));
        assertThat(testee.retrieveAssociatedMailboxes(QUOTA_ROOT, null)).containsOnly(MAILBOX_PATH, MAILBOX_PATH_2);
    }

    @Test(expected = MailboxException.class)
    public void retrieveAssociatedMailboxesShouldThrowWhenQuotaRootNotContainsSeparator2Times() throws Exception {
        testee.retrieveAssociatedMailboxes(QuotaRootImpl.quotaRoot("#private&be&nwa"), null);
    }

}
