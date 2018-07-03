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

import java.util.Optional;

public class QuotaDomainDTO {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<QuotaDTO> global;
        private Optional<QuotaDTO> domain;
        private Optional<QuotaDTO> computed;

        private Builder() {
            global = Optional.empty();
            computed = Optional.empty();
        }

        public Builder global(QuotaDTO.Builder global) {
            this.global = Optional.of(global.build());
            return this;
        }

        public Builder domain(QuotaDTO.Builder domain) {
            this.domain = Optional.of(domain.build());
            return this;
        }

        public Builder computed(QuotaDTO.Builder computed) {
            this.computed = Optional.of(computed.build());
            return this;
        }

        public QuotaDomainDTO build() {
            return new QuotaDomainDTO(global, domain, computed);
        }
    }

    private final Optional<QuotaDTO> global;
    private final Optional<QuotaDTO> domain;
    private final Optional<QuotaDTO> computed;

    private QuotaDomainDTO(Optional<QuotaDTO> global, Optional<QuotaDTO> domain, Optional<QuotaDTO> computed) {
        this.global = global;
        this.domain = domain;
        this.computed = computed;
    }

    public Optional<QuotaDTO> getGlobal() {
        return global;
    }

    public Optional<QuotaDTO> getDomain() {
        return domain;
    }

    public Optional<QuotaDTO> getComputed() {
        return computed;
    }
}
