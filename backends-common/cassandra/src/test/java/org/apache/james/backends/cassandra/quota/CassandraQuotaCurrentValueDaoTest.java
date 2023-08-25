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

package org.apache.james.backends.cassandra.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao.QuotaKey;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraQuotaCurrentValueDaoTest {
    private static final QuotaKey QUOTA_KEY = QuotaKey.of(QuotaComponent.MAILBOX, "james@abc.com", QuotaType.SIZE);

    private CassandraQuotaCurrentValueDao cassandraQuotaCurrentValueDao;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMutualizedQuotaModule.MODULE);

    @BeforeEach
    void setup() {
        cassandraQuotaCurrentValueDao = new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf());
    }

    @Test
    void increaseQuotaCurrentValueShouldCreateNewRowSuccessfully() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100L).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue()).isEqualTo(100L);
    }

    @Test
    void increaseQuotaCurrentValueShouldCreateNewRowSuccessfullyWhenIncreaseAmountIsZero() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 0L).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue()).isEqualTo(0L);
    }

    @Test
    void increaseQuotaCurrentValueShouldIncreaseValueSuccessfully() {
        cassandraCluster.getCassandraCluster().getConf().printStatements();
        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block()).isNull();

        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100L).block();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100L).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue()).isEqualTo(200L);
        cassandraCluster.getCassandraCluster().getConf().stopPrintingStatements();
    }

    @Test
    void increaseQuotaCurrentValueShouldDecreaseValueSuccessfullyWhenIncreaseAmountIsNegative() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 200L).block();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, -100L).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue()).isEqualTo(100L);
    }

    @Test
    void decreaseQuotaCurrentValueShouldDecreaseValueSuccessfully() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 200L).block();
        cassandraQuotaCurrentValueDao.decrease(QUOTA_KEY, 100L).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue()).isEqualTo(100L);
    }

    @Test
    void deleteQuotaCurrentValueShouldDeleteSuccessfully() {
        QuotaKey quotaKey = QuotaKey.of(QuotaComponent.MAILBOX, "andre@abc.com", QuotaType.SIZE);
        cassandraQuotaCurrentValueDao.increase(quotaKey, 100L).block();
        cassandraQuotaCurrentValueDao.deleteQuotaCurrentValue(quotaKey).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(quotaKey).block()).isNull();
    }

    @Disabled("Cassandra counter deletion is reversed once counter is incremented")
    @Test
    void deleteQuotaCurrentValueShouldResetCounterForever() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100L).block();
        cassandraQuotaCurrentValueDao.deleteQuotaCurrentValue(QUOTA_KEY).block();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100L).block();

        assertThat(cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue()).isEqualTo(100L);
    }

    @Test
    void increaseQuotaCurrentValueShouldNotThrowExceptionWhenQueryExecutorThrowException() {
        cassandraCluster.pause();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100L).block();
        cassandraCluster.unpause();
    }

    @Test
    void decreaseQuotaCurrentValueShouldNotThrowExceptionWhenQueryExecutorThrowException() {
        cassandraCluster.pause();
        cassandraQuotaCurrentValueDao.decrease(QUOTA_KEY, 100L).block();
        cassandraCluster.unpause();
    }

    @Test
    void getQuotasByComponentShouldGetAllQuotaTypesSuccessfully() {
        QuotaKey countQuotaKey = QuotaKey.of(QuotaComponent.MAILBOX, "james@abc.com", QuotaType.COUNT);

        QuotaCurrentValue expectedQuotaSize = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(100l).build();
        QuotaCurrentValue expectedQuotaCount = QuotaCurrentValue.builder().quotaComponent(countQuotaKey.getQuotaComponent())
            .identifier(countQuotaKey.getIdentifier()).quotaType(countQuotaKey.getQuotaType()).currentValue(56l).build();

        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100l).block();
        cassandraQuotaCurrentValueDao.increase(countQuotaKey, 56l).block();

        List<QuotaCurrentValue> actual = cassandraQuotaCurrentValueDao.getQuotasByComponent(QUOTA_KEY.getQuotaComponent(), QUOTA_KEY.getIdentifier())
            .collectList()
            .block();

        assertThat(actual).containsExactlyInAnyOrder(expectedQuotaSize, expectedQuotaCount);
    }
}