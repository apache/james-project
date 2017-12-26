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


package org.apache.james.webadmin.dto;

import org.apache.james.mailbox.model.Quota;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = QuotaDTO.Builder.class)
public class QuotaDTO {
    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private long count;
        private long size;

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public QuotaDTO build() {
            return new QuotaDTO(count, size);
        }

    }

    private final long count;
    private final long size;

    private QuotaDTO(long count, long size) {
        Preconditions.checkArgument(count >= Quota.UNLIMITED);
        Preconditions.checkArgument(size >= Quota.UNLIMITED);
        this.count = count;
        this.size = size;
    }

    public long getCount() {
        return count;
    }

    public long getSize() {
        return size;
    }
}
