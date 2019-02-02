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

import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.core.quota.QuotaValue;

public class QuotaCodec {

    private static final long INFINITE = -1;

    static Long quotaValueToLong(QuotaValue<?> value) {
        if (value.isUnlimited()) {
            return INFINITE;
        }
        return value.asLong();
    }

    static Optional<QuotaSize> longToQuotaSize(Long value) {
        return longToQuotaValue(value, QuotaSize.unlimited(), QuotaSize::size);
    }

    static Optional<QuotaCount> longToQuotaCount(Long value) {
        return longToQuotaValue(value, QuotaCount.unlimited(), QuotaCount::count);
    }

    private static <T extends QuotaValue<T>> Optional<T> longToQuotaValue(Long value, T infiniteValue, Function<Long, T> quotaFactory) {
        if (value == null) {
            return Optional.empty();
        }
        if (value == INFINITE) {
            return Optional.of(infiniteValue);
        }
        return Optional.of(quotaFactory.apply(value));
    }
}
