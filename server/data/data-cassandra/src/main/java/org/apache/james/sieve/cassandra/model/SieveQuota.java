/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.sieve.cassandra.model;

import java.util.Optional;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;

import com.google.common.base.Preconditions;

public class SieveQuota {

    private final long currentUsage;
    private final Optional<QuotaSizeLimit> limit;

    public SieveQuota(long currentUsage, Optional<QuotaSizeLimit> limit) {
        Preconditions.checkArgument(currentUsage >= 0, "Current usage should be positive or equal to zero");
        limit.ifPresent(limitValue -> Preconditions.checkArgument(limitValue.asLong() >= 0, "Limit value should be positive or equal to zero"));
        this.currentUsage = currentUsage;
        this.limit = limit;
    }

    public void checkOverQuotaUponModification(long sizeDifference) throws QuotaExceededException {
        if (isExceededUponModification(sizeDifference)) {
            throw new QuotaExceededException();
        }
    }

    public boolean isExceededUponModification(long sizeDifference) {
        return limit.map(limitContent ->
            QuotaSizeUsage.size(currentUsage)
                .add(sizeDifference)
                .exceedLimit(limitContent))
            .orElse(false);
    }
}
