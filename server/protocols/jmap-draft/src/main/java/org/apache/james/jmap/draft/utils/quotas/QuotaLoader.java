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
package org.apache.james.jmap.draft.utils.quotas;

import java.util.Optional;

import org.apache.james.core.quota.QuotaLimitValue;
import org.apache.james.core.quota.QuotaUsageValue;
import org.apache.james.jmap.draft.model.mailbox.Quotas;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;

import reactor.core.publisher.Mono;

public abstract class QuotaLoader {

    public abstract Mono<Quotas> getQuotas(MailboxPath mailboxPath);

    protected <T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> Quotas.Value<T, U> quotaToValue(Quota<T, U> quota) {
        return new Quotas.Value<>(
            quotaValueUsageToNumber(quota.getUsed()),
            quotaLimitValueToOptionalNumber(quota.getLimit()));
    }

    protected Number quotaValueToNumber(QuotaLimitValue<?> value) {
        return Number.BOUND_SANITIZING_FACTORY.from(value.asLong());
    }

    protected Number quotaValueUsageToNumber(QuotaUsageValue<?, ?> value) {
        return Number.BOUND_SANITIZING_FACTORY.from(value.asLong());
    }

    protected Optional<Number> quotaLimitValueToOptionalNumber(QuotaLimitValue<?> value) {
        if (value.isUnlimited()) {
            return Optional.empty();
        }
        return Optional.of(quotaValueToNumber(value));
    }
}
