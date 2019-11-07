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

import java.util.Objects;
import java.util.Optional;

import org.apache.james.mailbox.model.Quota;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class QuotaThreshold implements Comparable<QuotaThreshold> {

    public static final QuotaThreshold ZERO = new QuotaThreshold(0.);

    private final double quotaOccupationRatio;

    public QuotaThreshold(double quotaOccupationRatio) {
        Preconditions.checkArgument(quotaOccupationRatio >= 0., "Threshold should be contained in [0., 1.] range");
        Preconditions.checkArgument(quotaOccupationRatio <= 1., "Threshold should be contained in [0., 1.] range");
        this.quotaOccupationRatio = quotaOccupationRatio;
    }

    public double getQuotaOccupationRatio() {
        return quotaOccupationRatio;
    }

    public int getQuotaOccupationRatioAsPercent() {
        return Double.valueOf(quotaOccupationRatio * 100).intValue();
    }

    public boolean isExceeded(Quota<?, ?> quota) {
        if (quota.getLimit().isUnlimited()) {
            return false;
        }
        double used = toDouble(quota.getUsed().asLong());
        double limit = toDouble(quota.getLimit().asLong());

        double ratio = (used / limit);

        return ratio > quotaOccupationRatio;
    }

    public Optional<QuotaThreshold> nonZero() {
        if (this.equals(ZERO)) {
            return Optional.empty();
        }
        return Optional.of(this);
    }

    @Override
    public int compareTo(QuotaThreshold o) {
        return Double.compare(this.quotaOccupationRatio, o.quotaOccupationRatio);
    }

    private double toDouble(long aLong) {
        return Long.valueOf(aLong).doubleValue();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThreshold) {
            QuotaThreshold that = (QuotaThreshold) o;

            return Objects.equals(this.quotaOccupationRatio, that.quotaOccupationRatio);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaOccupationRatio);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaOccupationInPercent", quotaOccupationRatio)
            .toString();
    }
}
