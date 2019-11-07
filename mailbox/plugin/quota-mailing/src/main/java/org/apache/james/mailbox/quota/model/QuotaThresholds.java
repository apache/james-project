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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.james.mailbox.model.Quota;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class QuotaThresholds {
    private final ImmutableList<QuotaThreshold> quotaThresholds;

    public QuotaThresholds(QuotaThreshold... thresholds) {
        this(Arrays.asList(thresholds));
    }

    public QuotaThresholds(List<QuotaThreshold> quotaThresholds) {
        this.quotaThresholds = quotaThresholds.stream()
            .sorted(Comparator.comparing(QuotaThreshold::getQuotaOccupationRatio).reversed())
            .collect(Guavate.toImmutableList());
    }

    public QuotaThreshold highestExceededThreshold(Quota<?, ?> quota) {
        return quotaThresholds.stream()
            .filter(quotaLevel -> quotaLevel.isExceeded(quota))
            .findFirst()
            .orElse(QuotaThreshold.ZERO);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThresholds) {
            QuotaThresholds that = (QuotaThresholds) o;

            return Objects.equals(this.quotaThresholds, that.quotaThresholds);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaThresholds);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaThresholds", quotaThresholds)
            .toString();
    }
}
