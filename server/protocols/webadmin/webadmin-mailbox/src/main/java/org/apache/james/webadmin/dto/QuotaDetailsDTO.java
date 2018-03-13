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

import org.apache.james.mailbox.model.Quota;

public class QuotaDetailsDTO {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<QuotaDTO> global;
        private Optional<QuotaDTO> user;
        private Optional<QuotaDTO> computed;

        private Builder() {
            global = Optional.empty();
            user = Optional.empty();
            computed = Optional.empty();
        }

        public Builder global(QuotaDTO global) {
            this.global = Optional.of(global);
            return this;
        }

        public Builder user(QuotaDTO user) {
            this.user = Optional.of(user);
            return this;
        }

        public Builder computed(QuotaDTO computed) {
            this.computed = Optional.of(computed);
            return this;
        }

        public Builder valueForScope(Quota.Scope scope, QuotaDTO value) {
            switch (scope) {
                case Global:
                    return global(value);
                case User:
                    return user(value);
            }
            return this;
        }

        public QuotaDetailsDTO build() {
            return new QuotaDetailsDTO(global, user, computed);
        }
    }

    private final Optional<QuotaDTO> global;
    private final Optional<QuotaDTO> user;
    private final Optional<QuotaDTO> computed;

    private QuotaDetailsDTO(Optional<QuotaDTO> global, Optional<QuotaDTO> user, Optional<QuotaDTO> computed) {
        this.global = global;
        this.user = user;
        this.computed = computed;
    }

    public Optional<QuotaDTO> getGlobal() {
        return global;
    }

    public Optional<QuotaDTO> getUser() {
        return user;
    }

    public Optional<QuotaDTO> getComputed() {
        return computed;
    }
}
