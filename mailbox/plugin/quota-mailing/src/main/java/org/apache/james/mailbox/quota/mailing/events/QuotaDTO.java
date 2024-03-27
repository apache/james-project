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

package org.apache.james.mailbox.quota.mailing.events;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record QuotaDTO(@JsonProperty("used") long used,
                       @JsonProperty("limit") Optional<Long> limit) {

    @JsonIgnore
    public static QuotaDTO from(Quota<?, ?> quota) {
        if (quota.getLimit().isUnlimited()) {
            return new QuotaDTO(quota.getUsed().asLong(), Optional.empty());
        }
        return new QuotaDTO(quota.getUsed().asLong(), Optional.of(quota.getLimit().asLong()));
    }

    @JsonIgnore
    public Quota<QuotaSizeLimit, QuotaSizeUsage> asSizeQuota() {
        return Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(used))
            .computedLimit(QuotaSizeLimit.size(limit))
            .build();
    }

    @JsonIgnore
    public Quota<QuotaCountLimit, QuotaCountUsage> asCountQuota() {
        return Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(used))
            .computedLimit(QuotaCountLimit.count(limit))
            .build();
    }
}