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

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class QuotaRatio {

    public static QuotaRatio from(Quota<QuotaSizeLimit, QuotaSizeUsage> quotaSize, Quota<QuotaCountLimit, QuotaCountUsage> quotaCount) {
        return new QuotaRatio(quotaSize, quotaCount);
    }

    private final Quota<QuotaSizeLimit, QuotaSizeUsage> quotaSize;
    private final Quota<QuotaCountLimit, QuotaCountUsage> quotaCount;

    private QuotaRatio(Quota<QuotaSizeLimit, QuotaSizeUsage> quotaSize, Quota<QuotaCountLimit, QuotaCountUsage> quotaCount) {
        Preconditions.checkNotNull(quotaSize, "'quotaSize' is mandatory");
        Preconditions.checkNotNull(quotaCount, "'quotaCount' is mandatory");
        this.quotaSize = quotaSize;
        this.quotaCount = quotaCount;
    }

    public Quota<QuotaSizeLimit, QuotaSizeUsage> getQuotaSize() {
        return quotaSize;
    }

    public Quota<QuotaCountLimit, QuotaCountUsage> getQuotaCount() {
        return quotaCount;
    }

    public double max() {
        return Math.max(quotaSize.getRatio(), quotaCount.getRatio());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaRatio) {
            QuotaRatio quotaRatio = (QuotaRatio) o;

            return Objects.equals(this.quotaSize, quotaRatio.quotaSize)
                && Objects.equals(this.quotaCount, quotaRatio.quotaCount);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaSize, quotaCount);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaSize", quotaSize)
            .add("quotaCount", quotaCount)
            .toString();
    }
}
