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
package org.apache.james.jmap.draft.model.mailbox;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaLimitValue;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.core.quota.QuotaUsageValue;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.model.QuotaRoot;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;

public class Quotas {

    private final Map<QuotaId, Quota> quotas;

    public static Quotas from(ImmutableMap<QuotaId, Quota> quotas) {
        return new Quotas(quotas);
    }

    public static Quotas from(QuotaId quotaId, Quota quota) {
        return new Quotas(ImmutableMap.of(quotaId, quota));
    }

    private Quotas(ImmutableMap<QuotaId, Quota> quotas) {
        this.quotas = quotas;
    }

    @JsonValue
    public Map<QuotaId, Quota> getQuotas() {
        return quotas;
    }

    public static class QuotaId {
        private final QuotaRoot quotaRoot;

        public static QuotaId fromQuotaRoot(QuotaRoot quotaRoot) {
            return new QuotaId(quotaRoot);
        }
        
        private QuotaId(QuotaRoot quotaRoot) {
            this.quotaRoot = quotaRoot;
        }
        
        @JsonValue
        public String getName() {
            return quotaRoot.getValue();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof QuotaId) {
                QuotaId other = (QuotaId) o;
                return Objects.equals(quotaRoot, other.quotaRoot);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(quotaRoot);
        }
    }

    public static class Quota {
        private final Map<Type, Value<?, ?>> quota;

        public static Quota from(ImmutableMap<Type, Value<?, ?>> quota) {
            return new Quota(quota);
        }

        public static Quota from(Value<QuotaSizeLimit, QuotaSizeUsage> storage, Value<QuotaCountLimit, QuotaCountUsage> message) {
            return new Quota(ImmutableMap.of(Type.STORAGE, storage,
                Type.MESSAGE, message));
        }

        private Quota(ImmutableMap<Type, Value<?, ?>> quota) {
            this.quota = quota;
        }

        @JsonValue
        public Map<Type, Value<?, ?>> getQuota() {
            return quota;
        }
    }

    public static enum Type {
        STORAGE,
        MESSAGE;
    }

    public static class Value<T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> {
        private final Number used;
        private final Optional<Number> max;
        
        public Value(Number used, Optional<Number> max) {
            this.used = used;
            this.max = max;
        }

        public Number getUsed() {
            return used;
        }

        public Optional<Number> getMax() {
            return max;
        }
    }
}
