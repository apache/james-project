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

package org.apache.james.mailbox.cassandra.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraQuotaLimitDaoTest {

    private CassandraQuotaLimitDao cassandraQuotaLimitDao;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(CassandraQuotaModule.MODULE));

    @BeforeEach
    private void setup() {
        cassandraQuotaLimitDao = GuiceUtils.testInjector(cassandraCluster.getCassandraCluster()).getInstance(CassandraQuotaLimitDao.class);
    }

    @Test
    void getQuotaLimitsShouldGetSomeQuotaLimitsSuccessfully() {
        QuotaLimit expectedOne = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(200l).build();
        QuotaLimit expectedTwo = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.SIZE).quotaLimit(100l).build();
        cassandraQuotaLimitDao.setQuotaLimit(expectedOne).block();
        cassandraQuotaLimitDao.setQuotaLimit(expectedTwo).block();

        assertThat(cassandraQuotaLimitDao.getQuotaLimits(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A").collectList().block())
            .containsExactlyInAnyOrder(expectedOne, expectedTwo);
    }

    @Test
    void setQuotaLimitShouldSaveObjectSuccessfully() {
        QuotaLimit expected = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(100l).build();
        cassandraQuotaLimitDao.setQuotaLimit(expected).block();

        assertThat(cassandraQuotaLimitDao.getQuotaLimit(CassandraQuotaLimitDao.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block())
            .isEqualTo(expected);
    }

    @Test
    void setQuotaLimitShouldSaveObjectSuccessfullyWhenLimitIsMinusOne() {
        QuotaLimit expected = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(-1l).build();
        cassandraQuotaLimitDao.setQuotaLimit(expected).block();

        assertThat(cassandraQuotaLimitDao.getQuotaLimit(CassandraQuotaLimitDao.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block())
            .isEqualTo(expected);
    }

    @Test
    void deleteQuotaLimitShouldDeleteObjectSuccessfully() {
        QuotaLimit quotaLimit = QuotaLimit.builder().quotaComponent(QuotaComponent.MAILBOX).quotaScope(QuotaScope.DOMAIN).identifier("A").quotaType(QuotaType.COUNT).quotaLimit(100l).build();
        cassandraQuotaLimitDao.setQuotaLimit(quotaLimit).block();
        cassandraQuotaLimitDao.deleteQuotaLimit(CassandraQuotaLimitDao.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block();

        assertThat(cassandraQuotaLimitDao.getQuotaLimit(CassandraQuotaLimitDao.QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, "A", QuotaType.COUNT)).block())
            .isNull();
    }

}