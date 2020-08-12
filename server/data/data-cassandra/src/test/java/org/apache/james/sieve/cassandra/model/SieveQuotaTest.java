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

package org.apache.james.sieve.cassandra.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.junit.jupiter.api.Test;

class SieveQuotaTest {
    static final long INVALID_VALUE = -1L;
    static final QuotaSizeLimit LIMIT_LOW_VALUE = QuotaSizeLimit.size(10L);
    static final long SIZE_DIFFERENCE = 20L;
    static final int CURRENT_USAGE = 0;
    static final QuotaSizeLimit LIMIT_HIGH_VALUE = QuotaSizeLimit.size(100L);

    @Test
    void sieveQuotaShouldThrowOnNegativeCurrentValue() {
        assertThatThrownBy(() -> new SieveQuota(INVALID_VALUE, Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sieveQuotaShouldThrowOnNegativeLimitValue() {
        assertThatThrownBy(() -> new SieveQuota(0, Optional.of(QuotaSizeLimit.size(INVALID_VALUE))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkOverQuotaUponModificationShouldThrowIfLimitExceeded() {
        assertThatThrownBy(() -> new SieveQuota(CURRENT_USAGE, Optional.of(LIMIT_LOW_VALUE)).checkOverQuotaUponModification(SIZE_DIFFERENCE))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void checkOverQuotaShouldNotThrowWhenNoLimit() throws Exception {
        new SieveQuota(CURRENT_USAGE, Optional.empty()).checkOverQuotaUponModification(SIZE_DIFFERENCE);
    }

    @Test
    void checkOverQuotaUponModificationShouldNotThrowIfLimitNotExceeded() throws Exception {
        new SieveQuota(CURRENT_USAGE, Optional.of(LIMIT_HIGH_VALUE)).checkOverQuotaUponModification(SIZE_DIFFERENCE);
    }
}
