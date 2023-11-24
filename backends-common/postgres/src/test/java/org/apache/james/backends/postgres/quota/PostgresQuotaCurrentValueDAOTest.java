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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresQuotaCurrentValueDAOTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresQuotaModule.MODULE);

    private static final QuotaCurrentValue.Key QUOTA_KEY = QuotaCurrentValue.Key.of(QuotaComponent.MAILBOX, "james@abc.com", QuotaType.SIZE);

    private PostgresQuotaCurrentValueDAO postgresQuotaCurrentValueDAO;

    @BeforeEach
    void setup() {
        postgresQuotaCurrentValueDAO = new PostgresQuotaCurrentValueDAO(postgresExtension.getPostgresExecutor());
    }

    @Test
    void increaseQuotaCurrentValueShouldCreateNewRowSuccessfully() {
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isEqualTo(100L);
    }

    @Test
    void increaseQuotaCurrentValueShouldCreateNewRowSuccessfullyWhenIncreaseAmountIsZero() {
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 0L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isZero();
    }

    @Test
    void increaseQuotaCurrentValueShouldIncreaseValueSuccessfully() {
        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block()).isNull();

        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isEqualTo(200L);
    }

    @Test
    void increaseQuotaCurrentValueShouldDecreaseValueSuccessfullyWhenIncreaseAmountIsNegative() {
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 200L).block();
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, -100L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isEqualTo(100L);
    }

    @Test
    void decreaseQuotaCurrentValueShouldDecreaseValueSuccessfully() {
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 200L).block();
        postgresQuotaCurrentValueDAO.decrease(QUOTA_KEY, 100L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isEqualTo(100L);
    }

    @Test
    void decreaseQuotaCurrentValueDownToNegativeShouldAllowNegativeValue() {
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();
        postgresQuotaCurrentValueDAO.decrease(QUOTA_KEY, 1000L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isEqualTo(-900L);
    }

    @Test
    void decreaseQuotaCurrentValueWhenNoRecordYetShouldNotFailAndSetValueToZero() {
        postgresQuotaCurrentValueDAO.decrease(QUOTA_KEY, 1000L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isZero();
    }

    @Test
    void deleteQuotaCurrentValueShouldDeleteSuccessfully() {
        QuotaCurrentValue.Key quotaKey = QuotaCurrentValue.Key.of(QuotaComponent.MAILBOX, "andre@abc.com", QuotaType.SIZE);
        postgresQuotaCurrentValueDAO.increase(quotaKey, 100L).block();
        postgresQuotaCurrentValueDAO.deleteQuotaCurrentValue(quotaKey).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(quotaKey).block())
            .isNull();
    }

    @Test
    void deleteQuotaCurrentValueShouldResetCounterForever() {
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();
        postgresQuotaCurrentValueDAO.deleteQuotaCurrentValue(QUOTA_KEY).block();
        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();

        assertThat(postgresQuotaCurrentValueDAO.getQuotaCurrentValue(QUOTA_KEY).block().getCurrentValue())
            .isEqualTo(100L);
    }

    @Test
    void getQuotasByComponentShouldGetAllQuotaTypesSuccessfully() {
        QuotaCurrentValue.Key countQuotaKey = QuotaCurrentValue.Key.of(QuotaComponent.MAILBOX, "james@abc.com", QuotaType.COUNT);

        QuotaCurrentValue expectedQuotaSize = QuotaCurrentValue.builder().quotaComponent(QUOTA_KEY.getQuotaComponent())
            .identifier(QUOTA_KEY.getIdentifier()).quotaType(QUOTA_KEY.getQuotaType()).currentValue(100L).build();
        QuotaCurrentValue expectedQuotaCount = QuotaCurrentValue.builder().quotaComponent(countQuotaKey.getQuotaComponent())
            .identifier(countQuotaKey.getIdentifier()).quotaType(countQuotaKey.getQuotaType()).currentValue(56L).build();

        postgresQuotaCurrentValueDAO.increase(QUOTA_KEY, 100L).block();
        postgresQuotaCurrentValueDAO.increase(countQuotaKey, 56L).block();

        List<QuotaCurrentValue> actual = postgresQuotaCurrentValueDAO.getQuotaCurrentValues(QUOTA_KEY.getQuotaComponent(), QUOTA_KEY.getIdentifier())
            .collectList()
            .block();

        assertThat(actual).containsExactlyInAnyOrder(expectedQuotaSize, expectedQuotaCount);
    }
}
