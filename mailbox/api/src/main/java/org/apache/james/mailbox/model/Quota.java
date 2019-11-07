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
package org.apache.james.mailbox.model;

import java.util.Map;

import org.apache.james.core.quota.QuotaLimitValue;
import org.apache.james.core.quota.QuotaUsageValue;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class Quota<T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> {

    public enum Scope {
        Domain,
        Global,
        User
    }

    public static <T extends QuotaLimitValue<T>,  U extends QuotaUsageValue<U, T>> Builder<T, U> builder() {
        return new Builder<>();
    }

    public static class Builder<T extends QuotaLimitValue<T>,  U extends QuotaUsageValue<U, T>> {

        private final ImmutableMap.Builder<Scope, T> limitsByScope;
        private T computedLimit;
        private U used;

        private Builder() {
            limitsByScope = ImmutableMap.builder();
        }

        public Builder<T, U> computedLimit(T limit) {
            this.computedLimit = limit;
            return this;
        }

        public Builder<T, U> used(U used) {
            this.used = used;
            return this;
        }

        public Builder<T, U> limitsByScope(Map<Scope, T> limits) {
            limitsByScope.putAll(limits);
            return this;
        }

        public Builder<T, U> limitForScope(T limit, Scope scope) {
            limitsByScope.put(scope, limit);
            return this;
        }

        public Quota<T, U> build() {
            Preconditions.checkState(used != null);
            Preconditions.checkState(computedLimit != null);
            return new Quota<T, U>(used, computedLimit, limitsByScope.build());
        }

    }

    private final T limit;
    private final ImmutableMap<Scope, T> limitByScope;
    private final U used;

    private Quota(U used, T max, ImmutableMap<Scope, T> limitByScope) {
        this.used = used;
        this.limit = max;
        this.limitByScope = limitByScope;
    }

    public T getLimit() {
        return limit;
    }

    public U getUsed() {
        return used;
    }

    public double getRatio() {
        if (limit.isUnlimited()) {
            return 0;
        }
        return Double.valueOf(used.asLong()) / Double.valueOf(limit.asLong());
    }

    public ImmutableMap<Scope, T> getLimitByScope() {
        return limitByScope;
    }

    public Quota<T, U> addValueToQuota(U value) {
        return new Quota<T, U>(used.add(value), limit, limitByScope);
    }

    /**
     * Tells us if the quota is reached
     *
     * @return True if the user over uses the resource of this quota
     */
    public boolean isOverQuota() {
        return isOverQuotaWithAdditionalValue(0);
    }

    public boolean isOverQuotaWithAdditionalValue(long additionalValue) {
        Preconditions.checkArgument(additionalValue >= 0);
        return limit.isLimited() && used.add(additionalValue).exceedLimit(limit);
    }

    @Override
    public String toString() {
        return used + "/" + limit;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || ! (o instanceof  Quota)) {
            return false;
        }
        Quota<?, ?> other = (Quota<?, ?>) o;
        return Objects.equal(used, other.getUsed())
            && Objects.equal(limit,other.getLimit());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(used, limit);
    }

}