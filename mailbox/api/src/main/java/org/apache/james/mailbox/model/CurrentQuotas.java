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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class CurrentQuotas {
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
