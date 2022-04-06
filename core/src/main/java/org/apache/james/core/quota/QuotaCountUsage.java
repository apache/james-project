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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class QuotaCountUsage implements QuotaUsageValue<QuotaCountUsage, QuotaCountLimit> {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuotaCountUsage.class);

    public static class Sanitized extends QuotaCountUsage {
        private static Sanitized of(long value) {
            Preconditions.checkArgument(value >= 0, "Sanitized quota shall be positive");
            return new Sanitized(value);
        }

        private Sanitized(Long value) {
            super(value);
        }

    }

    public static QuotaCountUsage count(long value) {
        return new QuotaCountUsage(value);
    }

    private final long value;

    private QuotaCountUsage(long value) {
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

    public boolean isValid() {
        return value >= 0;
    }

    public Sanitized sanitize() {
        if (!isValid()) {
            LOGGER.warn("Invalid quota count usage : {}", value);
        }

        return Sanitized.of(Math.max(value, 0));
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
            .add("value", value)
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
