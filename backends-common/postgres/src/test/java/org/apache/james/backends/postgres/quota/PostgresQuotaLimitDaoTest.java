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

package org.apache.james.backends.postgres.quota;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgresQuotaLimitDaoTest {

    private PostgresQuotaLimitDAO postgresQuotaLimitDao;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresQuotaModule.MODULE);

    @BeforeEach
    void setup() {
        postgresQuotaLimitDao = new PostgresQuotaLimitDAO(postgresExtension.getPostgresExecutor());
    }

    @Test
    void getQuotaLimitsShouldGetSomeQuotaLimitsSuccessfully() {
        QuotaLimit expectedOne = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(200l).build();
        QuotaLimit expectedTwo = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.SIZE).quotaLimit(100l).build();
        postgresQuotaLimitDao.setQuotaLimit(expectedOne).block();
        postgresQuotaLimitDao.setQuotaLimit(expectedTwo).block();

        assertThat(postgresQuotaLimitDao.getQuotaLimits(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A").collectList().block())
            .containsExactlyInAnyOrder(expectedOne, expectedTwo);
    }

    @Test
    void setQuotaLimitShouldSaveObjectSuccessfully() {
        QuotaLimit expected = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(100l).build();
        postgresQuotaLimitDao.setQuotaLimit(expected).block();

        assertThat(postgresQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block())
            .isEqualTo(expected);
    }

    @Test
    void setQuotaLimitShouldSaveObjectSuccessfullyWhenLimitIsMinusOne() {
        QuotaLimit expected = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(-1l).build();
        postgresQuotaLimitDao.setQuotaLimit(expected).block();

        assertThat(postgresQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block())
            .isEqualTo(expected);
    }

    @Test
    void deleteQuotaLimitShouldDeleteObjectSuccessfully() {
        QuotaLimit quotaLimit = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(100l).build();
        postgresQuotaLimitDao.setQuotaLimit(quotaLimit).block();
        postgresQuotaLimitDao.deleteQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block();

        assertThat(postgresQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block())
            .isNull();
    }

}