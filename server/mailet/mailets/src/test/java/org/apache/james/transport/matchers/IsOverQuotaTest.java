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
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
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
    private UsersRepository usersRepository;

    @Before
    public void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();

        quotaRootResolver = resources.getDefaultUserQuotaRootResolver();
        maxQuotaManager = resources.getMaxQuotaManager();

        usersRepository = mock(UsersRepository.class);
        testee = new IsOverQuota(quotaRootResolver, resources.getMailboxManager().getQuotaComponents().getQuotaManager(), usersRepository);

        testee.init(FakeMatcherConfig.builder().matcherName("IsOverQuota").build());

        when(usersRepository.getUsername(MailAddressFixture.ANY_AT_JAMES)).thenReturn(Username.of(MailAddressFixture.ANY_AT_JAMES.getLocalPart()));
        when(usersRepository.getUsername(MailAddressFixture.OTHER_AT_JAMES)).thenReturn(Username.of(MailAddressFixture.OTHER_AT_JAMES.getLocalPart()));
    }

    @Test
    public void matchShouldAcceptMailWhenNoQuota() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .size(1000)
            .build();

        assertThat(testee.match(mail))
            .isEmpty();
    }

    @Test
    public void matchShouldKeepAddressesWithTooBigSize() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(100));

        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .size(1000)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void matchShouldReturnEmptyAtSizeQuotaLimit() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1000));

        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .size(1000)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEmpty();
    }

    @Test
    public void matchShouldKeepAddressesWithTooMuchMessages() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(0));

        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void matchShouldReturnEmptyOnMessageLimit() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(1));

        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).isEmpty();
    }

    @Test
    public void matchShouldNotIncludeRecipientNotOverQuota() throws Exception {
        Username username = Username.of(MailAddressFixture.ANY_AT_JAMES.getLocalPart());
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(MailboxPath.inbox(username));
        maxQuotaManager.setMaxStorage(quotaRoot, QuotaSizeLimit.size(100));

        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .size(150)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void matchShouldSupportVirtualHosting() throws Exception {
        when(usersRepository.getUsername(MailAddressFixture.ANY_AT_JAMES)).thenReturn(Username.of(MailAddressFixture.ANY_AT_JAMES.asString()));
        when(usersRepository.getUsername(MailAddressFixture.OTHER_AT_JAMES)).thenReturn(Username.of(MailAddressFixture.OTHER_AT_JAMES.asString()));
        Username username = Username.of(MailAddressFixture.ANY_AT_JAMES.asString());
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(MailboxPath.inbox(username));
        maxQuotaManager.setMaxStorage(quotaRoot, QuotaSizeLimit.size(100));

        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .size(150)
            .build();
        Collection<MailAddress> result = testee.match(fakeMail);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

}
