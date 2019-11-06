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
        private Optional<ValidatedQuotaDTO> global;
        private Optional<ValidatedQuotaDTO> domain;
        private Optional<ValidatedQuotaDTO> computed;

        private Builder() {
            global = Optional.empty();
            computed = Optional.empty();
        }

        public Builder global(ValidatedQuotaDTO.Builder global) {
            this.global = Optional.of(global.build());
            return this;
        }

        public Builder domain(ValidatedQuotaDTO.Builder domain) {
            this.domain = Optional.of(domain.build());
            return this;
        }

        public Builder computed(ValidatedQuotaDTO.Builder computed) {
            this.computed = Optional.of(computed.build());
            return this;
        }

        public QuotaDomainDTO build() {
            return new QuotaDomainDTO(global, domain, computed);
        }
    }

    private final Optional<ValidatedQuotaDTO> global;
    private final Optional<ValidatedQuotaDTO> domain;
    private final Optional<ValidatedQuotaDTO> computed;

    private QuotaDomainDTO(Optional<ValidatedQuotaDTO> global, Optional<ValidatedQuotaDTO> domain, Optional<ValidatedQuotaDTO> computed) {
        this.global = global;
        this.domain = domain;
        this.computed = computed;
    }

    public Optional<ValidatedQuotaDTO> getGlobal() {
        return global;
    }

    public Optional<ValidatedQuotaDTO> getDomain() {
        return domain;
    }

    public Optional<ValidatedQuotaDTO> getComputed() {
        return computed;
    }
}
