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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraQuotaCurrentValueDaoTest {

    private static final CassandraQuotaCurrentValueDao.QuotaCurrentValueKey QUOTA_KEY
        = CassandraQuotaCurrentValueDao.QuotaCurrentValueKey.of(QuotaComponent.MAILBOX, Username.of("james@abc.com"), QuotaType.SIZE);

    private CassandraQuotaCurrentValueDao cassandraQuotaCurrentValueDao;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(CassandraQuotaModule.MODULE));

    @BeforeEach
    private void setup() {
        cassandraQuotaCurrentValueDao = GuiceUtils.testInjector(cassandraCluster.getCassandraCluster()).getInstance(CassandraQuotaCurrentValueDao.class);
        resetCounterToZero();
    }

    private void resetCounterToZero() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 0).block();
        QuotaCurrentValue quotaCurrentValue = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        cassandraQuotaCurrentValueDao.decrease(QUOTA_KEY, quotaCurrentValue.getCurrentValue()).block();
        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual.getCurrentValue()).isEqualTo(0l);
    }

    @Test
    void increaseQuotaCurrentValueShouldCreateNewRowSuccessfully() {
        QuotaCurrentValue expected = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(100l).build();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100l).block();

        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void increaseQuotaCurrentValueShouldCreateNewRowSuccessfullyWhenIncreaseAmountIsZero() {
        QuotaCurrentValue expected = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(0l).build();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 0l).block();

        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void increaseQuotaCurrentValueShouldIncreaseValueSuccessfully() {
        QuotaCurrentValue expected = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(200l).build();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100l).block();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100l).block();

        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void increaseQuotaCurrentValueShouldDecreaseValueSuccessfullyWhenIncreaseAmountIsNegative() {
        QuotaCurrentValue expected = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(100l).build();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 200l).block();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, -100l).block();

        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void decreaseQuotaCurrentValueShouldDecreaseValueSuccessfully() {
        QuotaCurrentValue expected = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(100l).build();
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 200l).block();
        cassandraQuotaCurrentValueDao.decrease(QUOTA_KEY, 100l).block();

        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void deleteQuotaCurrentValueShouldDeleteSuccessfully() {
        cassandraQuotaCurrentValueDao.increase(QUOTA_KEY, 100l).block();
        cassandraQuotaCurrentValueDao.deleteQuotaCurrentValue(QUOTA_KEY).block();

        QuotaCurrentValue actual = cassandraQuotaCurrentValueDao.getQuotaCurrentValue(QUOTA_KEY).block();
        assertThat(actual).isNull();
    }

}