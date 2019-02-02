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

package org.apache.james.mailbox.quota.model;

import java.time.Instant;
import java.util.Objects;

import com.google.common.base.MoreObjects;

public class QuotaThresholdChange {
    private final QuotaThreshold quotaThreshold;
    private final Instant instant;

    public QuotaThresholdChange(QuotaThreshold quotaThreshold, Instant instant) {
        this.quotaThreshold = quotaThreshold;
        this.instant = instant;
    }

    public boolean isAfter(Instant instant) {
        return this.instant.isAfter(instant);
    }

    public QuotaThreshold getQuotaThreshold() {
        return quotaThreshold;
    }

    public Instant getInstant() {
        return instant;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThresholdChange) {
            QuotaThresholdChange that = (QuotaThresholdChange) o;

            return Objects.equals(this.quotaThreshold, that.quotaThreshold)
                && Objects.equals(this.instant, that.instant);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaThreshold, instant);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaThreshold", quotaThreshold)
            .add("instant", instant)
            .toString();
    }
}
