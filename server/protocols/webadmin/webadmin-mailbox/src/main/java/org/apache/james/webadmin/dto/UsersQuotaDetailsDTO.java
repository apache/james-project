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

import org.apache.james.core.User;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class UsersQuotaDetailsDTO {

    private static final String USERNAME = "username";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;
        private QuotaDetailsDTO detail;
        
        private Builder() {
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder detail(QuotaDetailsDTO detail) {
            this.detail = detail;
            return this;
        }

        public UsersQuotaDetailsDTO build() {
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(detail);
            return new UsersQuotaDetailsDTO(user, detail);
        }
    }

    private final User user;
    private final QuotaDetailsDTO detail;

    @VisibleForTesting UsersQuotaDetailsDTO(User user, QuotaDetailsDTO detail) {
        this.user = user;
        this.detail = detail;
    }

    @JsonProperty(USERNAME)
    public String getUser() {
        return user.asString();
    }

    public QuotaDetailsDTO getDetail() {
        return detail;
    }
}
