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

package org.apache.james.quota.search.elasticsearch;

import static org.apache.james.core.CoreFixture.Domains.SIMPSON_COM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.quota.search.Limit;
import org.apache.james.quota.search.Offset;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.quota.search.QuotaSearcherContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ElasticSearchQuotaSearchTestSystemExtension.class)
class ElasticSearchQuotaSearcherTest implements QuotaSearcherContract {
    @Test
    void searchShouldNotBeLimitedByElasticSearchDefaultSearchLimit(QuotaSearchTestSystem testSystem) throws Exception {
        int userCount = 11;
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSize.size(100));

        IntStream.range(0, userCount)
            .boxed()
            .map(i -> User.fromLocalPartWithDomain("user" + i, SIMPSON_COM))
            .forEach(user -> provisionUser(testSystem, user));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .withLimit(Limit.unlimited())
                    .build()))
            .hasSize(userCount);
    }

    @Test
    void searchShouldNotBeLimitedByElasticSearchDefaultSearchLimitWhenUsingOffset(QuotaSearchTestSystem testSystem) throws Exception {
        int userCount = 12;
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSize.size(100));

        IntStream.range(0, userCount)
            .boxed()
            .map(i -> User.fromLocalPartWithDomain("user" + i, SIMPSON_COM))
            .forEach(user -> provisionUser(testSystem, user));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .withLimit(Limit.unlimited())
                    .withOffset(Offset.of(1))
                    .build()))
            .hasSize(userCount - 1);
    }

    private void provisionUser(QuotaSearchTestSystem testSystem, User user) {
        try {
            testSystem.getUsersRepository().addUser(user.asString(), PASSWORD);
            appendMessage(testSystem, user, withSize(49));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
