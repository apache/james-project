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
package org.apache.james.core.quota;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class QuotaCountUsage implements QuotaUsageValue<QuotaCountUsage, QuotaCountLimit> {

    public static QuotaCountUsage count(long value) {
        return new QuotaCountUsage(value);
    }

    private final Long value;

    private QuotaCountUsage(Long value) {
        this.value = value;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public QuotaCountUsage add(long additionalValue) {
        return new QuotaCountUsage(value + additionalValue);
    }

    @Override
    public QuotaCountUsage add(QuotaCountUsage additionalValue) {
        return new QuotaCountUsage(value + additionalValue.asLong());
    }

    @Override
    public boolean exceedLimit(QuotaCountLimit limit) {
        if (limit.isLimited()) {
            return value > limit.asLong();
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value.toString())
            .toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaCountUsage) {
            QuotaCountUsage that = (QuotaCountUsage) o;
            return Objects.equal(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(value);
    }

}
