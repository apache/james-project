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

package org.apache.james.core.quota;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class QuotaLimit {

    public static class Builder {
        private QuotaComponent quotaComponent;
        private QuotaScope quotaScope;
        private String identifier;
        private QuotaType quotaType;
        private Long quotaLimit;

        public Builder quotaComponent(QuotaComponent quotaComponent) {
            this.quotaComponent = quotaComponent;
            return this;
        }

        public Builder quotaScope(QuotaScope quotaScope) {
            this.quotaScope = quotaScope;
            return this;
        }

        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder quotaType(QuotaType quotaType) {
            this.quotaType = quotaType;
            return this;
        }

        public Builder quotaLimit(Long quotaLimit) {
            this.quotaLimit = quotaLimit;
            return this;
        }

        public QuotaLimit build() {
            Preconditions.checkState(quotaComponent != null, "`quotaComponent` is mandatory");
            Preconditions.checkState(quotaScope != null, "`quotaScope` is mandatory");
            Preconditions.checkState(identifier != null, "`identifier` is mandatory");
            Preconditions.checkState(quotaType != null, "`quotaType` is mandatory");

            return new QuotaLimit(quotaComponent, quotaScope, identifier, quotaType, quotaLimit);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final QuotaComponent quotaComponent;
    private final QuotaScope quotaScope;
    private final String identifier;
    private final QuotaType quotaType;
    private final Optional<Long> quotaLimit;

    private QuotaLimit(QuotaComponent quotaComponent, QuotaScope quotaScope, String identifier, QuotaType quotaType, Long quotaLimit) {
        this.quotaComponent = quotaComponent;
        this.quotaScope = quotaScope;
        this.identifier = identifier;
        this.quotaType = quotaType;
        this.quotaLimit = Optional.ofNullable(quotaLimit);
    }

    public QuotaComponent getQuotaComponent() {
        return quotaComponent;
    }

    public QuotaScope getQuotaScope() {
        return quotaScope;
    }

    public String getIdentifier() {
        return identifier;
    }

    public QuotaType getQuotaType() {
        return quotaType;
    }

    public Optional<Long> getQuotaLimit() {
        return quotaLimit;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaComponent, quotaScope, identifier, quotaType, quotaLimit);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaLimit) {
            QuotaLimit other = (QuotaLimit) o;
            return Objects.equals(quotaComponent, other.quotaComponent)
                && Objects.equals(quotaScope, other.quotaScope)
                && Objects.equals(identifier, other.identifier)
                && Objects.equals(quotaType, other.quotaType)
                && Objects.equals(quotaLimit, other.quotaLimit);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaComponent", quotaComponent)
            .add("quotaScope", quotaScope)
            .add("identifier", identifier)
            .add("quotaType", quotaType)
            .add("quotaLimit", quotaLimit)
            .toString();
    }
}