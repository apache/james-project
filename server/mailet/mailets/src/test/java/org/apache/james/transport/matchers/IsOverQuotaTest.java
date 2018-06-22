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

package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

public class IsOverQuotaTest {
    private IsOverQuota testee;
    private InMemoryPerUserMaxQuotaManager maxQuotaManager;
    private DefaultUserQuotaRootResolver quotaRootResolver;
    private StoreMailboxManager mailboxManager;
    private UsersRepository usersRepository;

    @Before
    public void setUp() throws Exception {
        mailboxManager = new InMemoryIntegrationResources().createMailboxManager(new SimpleGroupMembershipResolver());

        quotaRootResolver = new DefaultUserQuotaRootResolver(mailboxManager.getMapperFactory());
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        CurrentQuotaCalculator quotaCalculator = new CurrentQuotaCalculator(mailboxManager.getMapperFactory(), quotaRootResolver);
        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(quotaCalculator, mailboxManager);
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        usersRepository = mock(UsersRepository.class);
        testee = new IsOverQuota(quotaRootResolver, quotaManager, mailboxManager, usersRepository);

        mailboxManager.setQuotaRootResolver(quotaRootResolver);
        mailboxManager.setQuotaManager(quotaManager);

        testee.init(FakeMatcherConfig.builder().matcherName("IsOverQuota").build());

        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenReturn(MailAddressFixture.ANY_AT_JAMES.getLocalPart());
        when(usersRepository.getUser(MailAddressFixture.OTHER_AT_JAMES)).thenReturn(MailAddressFixture.OTHER_AT_JAMES.getLocalPart());
    }

    @Test
    public void matchShouldAcceptMailWhenNoQuota() throws Exception {
        FakeMail mail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .size(1000)
            .build();

        assertThat(testee.match(mail))
            .isEmpty();
    }

    @Test
    public void matchShouldKeepAddressesWithTooBigSize() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(100));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .size(1000)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void matchShouldReturnEmptyAtSizeQuotaLimit() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(1000));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .size(1000)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEmpty();
    }

    @Test
    public void matchShouldKeepAddressesWithTooMuchMessages() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(0));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void matchShouldReturnEmptyOnMessageLimit() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(1));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEmpty();
    }

    @Test
    public void matchShouldNotIncludeRecipientNotOverQuota() throws Exception {
        String username = MailAddressFixture.ANY_AT_JAMES.getLocalPart();
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(MailboxPath.inbox(mailboxManager.createSystemSession(username)));
        maxQuotaManager.setMaxStorage(quotaRoot, QuotaSize.size(100));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .size(150)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void matchShouldSupportVirtualHosting() throws Exception {
        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenReturn(MailAddressFixture.ANY_AT_JAMES.asString());
        when(usersRepository.getUser(MailAddressFixture.OTHER_AT_JAMES)).thenReturn(MailAddressFixture.OTHER_AT_JAMES.asString());
        String username = MailAddressFixture.ANY_AT_JAMES.asString();
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(MailboxPath.inbox(mailboxManager.createSystemSession(username)));
        maxQuotaManager.setMaxStorage(quotaRoot, QuotaSize.size(100));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .size(150)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

}
