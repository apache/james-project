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

package org.apache.james.mailbox.quota.cassandra.dto;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.model.Quota;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

class QuotaDTO {
    public static QuotaDTO from(Quota<?> quota) {
        if (quota.getLimit().isUnlimited()) {
            return new QuotaDTO(quota.getUsed().asLong(), Optional.empty());
        }
        return new QuotaDTO(quota.getUsed().asLong(), Optional.of(quota.getLimit().asLong()));
    }

    private final long used;
    private final Optional<Long> limit;

    @JsonCreator
    private QuotaDTO(@JsonProperty("used") long used,
                     @JsonProperty("limit") Optional<Long> limit) {
        this.used = used;
        this.limit = limit;
    }

    public long getUsed() {
        return used;
    }

    public Optional<Long> getLimit() {
        return limit;
    }

    @JsonIgnore
    public Quota<QuotaSize> asSizeQuota() {
        return Quota.<QuotaSize>builder()
            .used(QuotaSize.size(used))
            .computedLimit(QuotaSize.size(limit))
            .build();
    }

    @JsonIgnore
    public Quota<QuotaCount> asCountQuota() {
        return Quota.<QuotaCount>builder()
            .used(QuotaCount.count(used))
            .computedLimit(QuotaCount.count(limit))
            .build();
    }
}
