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
package org.apache.james.mailbox.quota;

import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaLimitValue;
import org.apache.james.core.quota.QuotaSizeLimit;

public class QuotaCodec {

    private static final long INFINITE = -1;
    private static final long NO_RIGHT = 0L;

    public static Long quotaValueToLong(QuotaLimitValue<?> value) {
        if (value.isUnlimited()) {
            return INFINITE;
        }
        return value.asLong();
    }

    public static Optional<QuotaSizeLimit> longToQuotaSize(Long value) {
        return longToQuotaValue(value, QuotaSizeLimit.unlimited(), QuotaSizeLimit::size);
    }

    public static Optional<QuotaCountLimit> longToQuotaCount(Long value) {
        return longToQuotaValue(value, QuotaCountLimit.unlimited(), QuotaCountLimit::count);
    }

    private static <T extends QuotaLimitValue<T>> Optional<T> longToQuotaValue(Long value, T infiniteValue, Function<Long, T> quotaFactory) {
        if (value == null) {
            return Optional.empty();
        }
        if (value == INFINITE) {
            return Optional.of(infiniteValue);
        }
        if (isInvalid(value)) {
            return Optional.of(quotaFactory.apply(NO_RIGHT));
        }
        return Optional.of(quotaFactory.apply(value));
    }

    private static boolean isInvalid(Long value) {
        return value < -1;
    }
}
