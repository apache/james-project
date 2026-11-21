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

package org.apache.james.webadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.webadmin.dto.UsersQuotaDetailsDTO;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

class UserQuotaServiceTest {
    private static final Username VALID = Username.of("valid@domain.tld");
    private static final Username INVALID = Username.of("..@domain.tld");

    @Test
    void getUsersQuotaShouldSkipEntriesWhoseQuotaCannotBeRetrieved() {
        QuotaSearcher quotaSearcher = mock(QuotaSearcher.class);
        when(quotaSearcher.search(any(QuotaQuery.class))).thenReturn(ImmutableList.of(INVALID, VALID));

        QuotaRoot quotaRoot = QuotaRoot.quotaRoot("#private&valid@domain.tld", Optional.of(Domain.of("domain.tld")));
        UserQuotaRootResolver quotaRootResolver = mock(UserQuotaRootResolver.class);
        when(quotaRootResolver.forMailAddress(INVALID)).thenThrow(new IllegalArgumentException("Local address starts with dot"));
        when(quotaRootResolver.forMailAddress(VALID)).thenReturn(quotaRoot);

        QuotaManager quotaManager = mock(QuotaManager.class);
        when(quotaManager.getQuotasReactive(quotaRoot)).thenReturn(Mono.just(new QuotaManager.Quotas(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(1)).computedLimit(QuotaCountLimit.unlimited()).build(),
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(1)).computedLimit(QuotaSizeLimit.unlimited()).build())));

        UserQuotaService testee = new UserQuotaService(new InMemoryPerUserMaxQuotaManager(), quotaManager, quotaRootResolver, quotaSearcher);

        assertThat(testee.getUsersQuota(QuotaQuery.builder().build()))
            .extracting(UsersQuotaDetailsDTO::getUsername)
            .containsExactly(VALID.asString());
    }
}
