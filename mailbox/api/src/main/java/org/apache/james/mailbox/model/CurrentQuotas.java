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

import java.util.Objects;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class CurrentQuotas {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentQuotas.class);

    public static class Sanitized extends CurrentQuotas {
        private static Sanitized of(CurrentQuotas value) {
            Preconditions.checkArgument(value.count().asLong() >= 0, "Sanitized quota shall be positive");
            Preconditions.checkArgument(value.size().asLong() >= 0, "Sanitized quota shall be positive");
            return new Sanitized(value.count(), value.size());
        }

        private static Sanitized of(QuotaCountUsage count, QuotaSizeUsage size) {
            Preconditions.checkArgument(count.asLong() >= 0, "Sanitized quota shall be positive");
            Preconditions.checkArgument(size.asLong() >= 0, "Sanitized quota shall be positive");
            return new Sanitized(count, size);
        }

        public Sanitized(QuotaCountUsage count, QuotaSizeUsage size) {
            super(count, size);
        }
    }

    private final QuotaCountUsage count;
    private final QuotaSizeUsage size;

    public static CurrentQuotas emptyQuotas() {
        return new CurrentQuotas(QuotaCountUsage.count(0L), QuotaSizeUsage.size(0L));
    }

    public static CurrentQuotas from(QuotaOperation quotaOperation) {
        return new CurrentQuotas(quotaOperation.count(), quotaOperation.size());
    }

    public CurrentQuotas(QuotaCountUsage count, QuotaSizeUsage size) {
        Preconditions.checkNotNull(count);
        Preconditions.checkNotNull(size);

        this.count = count;
        this.size = size;
    }

    public QuotaCountUsage count() {
        return count;
    }

    public QuotaSizeUsage size() {
        return size;
    }

    public CurrentQuotas increase(CurrentQuotas updateQuotas) {
        return new CurrentQuotas(
            QuotaCountUsage.count(this.count.asLong() + updateQuotas.count.asLong()),
            QuotaSizeUsage.size(this.size.asLong() + updateQuotas.size.asLong()));
    }

    public CurrentQuotas decrease(CurrentQuotas updateQuotas) {
        return new CurrentQuotas(
            QuotaCountUsage.count(this.count.asLong() - updateQuotas.count.asLong()),
            QuotaSizeUsage.size(this.size.asLong() - updateQuotas.size.asLong()));
    }

    public boolean isValid() {
        return count.isValid() && size.isValid();
    }

    public Sanitized sanitize() {
        if (!isValid()) {
            LOGGER.warn("Invalid quota usage : {}, {}", count.asLong(), size.asLong());
        }

        return Sanitized.of(count.sanitize(), size.sanitize());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CurrentQuotas) {
            CurrentQuotas currentQuotas = (CurrentQuotas) o;

            return Objects.equals(this.count, currentQuotas.count)
                && Objects.equals(this.size, currentQuotas.size);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(count, size);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("count", count)
            .add("size", size)
            .toString();
    }
}
