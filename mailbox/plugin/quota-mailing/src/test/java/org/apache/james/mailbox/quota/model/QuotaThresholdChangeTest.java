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

package org.apache.james.mailbox.quota.model;

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.ONE_HOUR_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.THREE_HOURS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.TWO_HOURS_AGO;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class QuotaThresholdChangeTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaThresholdChange.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    void isAfterShouldReturnTrueWhenRecent() {
        QuotaThresholdChange change = new QuotaThresholdChange(_75, TWO_HOURS_AGO);
        assertThat(change.isAfter(THREE_HOURS_AGO)).isTrue();
    }

    @Test
    void isAfterShouldReturnFalseWhenOld() {
        QuotaThresholdChange change = new QuotaThresholdChange(_75, TWO_HOURS_AGO);

        assertThat(change.isAfter(ONE_HOUR_AGO)).isFalse();
    }

    @Test
    void isAfterShouldReturnFalseWhenSameInstant() {
        QuotaThresholdChange change = new QuotaThresholdChange(_75, TWO_HOURS_AGO);

        assertThat(change.isAfter(TWO_HOURS_AGO)).isFalse();
    }
}