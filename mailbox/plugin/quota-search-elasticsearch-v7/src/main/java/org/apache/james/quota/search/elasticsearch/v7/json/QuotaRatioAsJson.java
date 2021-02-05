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
package org.apache.james.quota.search.elasticsearch.v7.json;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.mailbox.model.QuotaRatio;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class QuotaRatioAsJson {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String user;
        private Optional<String> domain;
        private QuotaRatio quotaRatio;

        private Builder() {
            domain = Optional.empty();
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder domain(Optional<String> domain) {
            this.domain = domain;
            return this;
        }

        public Builder quotaRatio(QuotaRatio quotaRatio) {
            this.quotaRatio = quotaRatio;
            return this;
        }

        public QuotaRatioAsJson build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(user), "'user' is mandatory");
            Preconditions.checkNotNull(quotaRatio, "'quotaRatio' is mandatory");

            return new QuotaRatioAsJson(user, domain, quotaRatio);
        }
    }

    private final String user;
    private final Optional<String> domain;
    private final QuotaRatio quotaRatio;

    private QuotaRatioAsJson(String user, Optional<String> domain, QuotaRatio quotaRatio) {
        this.user = user;
        this.domain = domain;
        this.quotaRatio = quotaRatio;
    }

    @JsonProperty(JsonMessageConstants.USER)
    public String getUser() {
        return user;
    }

    @JsonProperty(JsonMessageConstants.DOMAIN)
    public Optional<String> getDomain() {
        return domain;
    }

    @JsonProperty(JsonMessageConstants.QUOTA_RATIO)
    public double getMaxQuotaRatio() {
        return quotaRatio.max();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaRatioAsJson) {
            QuotaRatioAsJson that = (QuotaRatioAsJson) o;

            return Objects.equals(this.quotaRatio, that.quotaRatio)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.domain, that.domain);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(user, domain, quotaRatio);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaRatio", quotaRatio)
            .add("user", user)
            .add("domain", domain)
            .toString();
    }
}
