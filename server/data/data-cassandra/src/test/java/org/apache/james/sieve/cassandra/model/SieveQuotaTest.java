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

import java.util.Optional;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.junit.Test;

public class SieveQuotaTest {

    public static final long INVALID_VALUE = -1L;
    public static final QuotaSizeLimit LIMIT_LOW_VALUE = QuotaSizeLimit.size(10L);
    public static final long SIZE_DIFFERENCE = 20L;
    public static final int CURRENT_USAGE = 0;
    public static final QuotaSizeLimit LIMIT_HIGH_VALUE = QuotaSizeLimit.size(100L);

    @Test(expected = IllegalArgumentException.class)
    public void sieveQuotaShouldThrowOnNegativeCurrentValue() {
        new SieveQuota(INVALID_VALUE, Optional.empty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sieveQuotaShouldThrowOnNegativeLimitValue() {
        new SieveQuota(0, Optional.of(QuotaSizeLimit.size(INVALID_VALUE)));
    }

    @Test(expected = QuotaExceededException.class)
    public void checkOverQuotaUponModificationShouldThrowIfLimitExceeded() throws Exception {
        new SieveQuota(CURRENT_USAGE, Optional.of(LIMIT_LOW_VALUE)).checkOverQuotaUponModification(SIZE_DIFFERENCE);
    }

    @Test
    public void checkOverQuotaShouldNotThrowWhenNoLimit() throws Exception {
        new SieveQuota(CURRENT_USAGE, Optional.empty()).checkOverQuotaUponModification(SIZE_DIFFERENCE);
    }

    @Test
    public void checkOverQuotaUponModificationShouldNotThrowIfLimitNotExceeded() throws Exception {
        new SieveQuota(CURRENT_USAGE, Optional.of(LIMIT_HIGH_VALUE)).checkOverQuotaUponModification(SIZE_DIFFERENCE);
    }
}
