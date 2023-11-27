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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class QuotaCurrentValue {

    public static class Key {

        public static Key of(QuotaComponent component, String identifier, QuotaType quotaType) {
            return new Key(component, identifier, quotaType);
        }

        private final QuotaComponent quotaComponent;
        private final String identifier;
        private final QuotaType quotaType;

        public QuotaComponent getQuotaComponent() {
            return quotaComponent;
        }

        public String getIdentifier() {
            return identifier;
        }

        public QuotaType getQuotaType() {
            return quotaType;
        }

        private Key(QuotaComponent quotaComponent, String identifier, QuotaType quotaType) {
            this.quotaComponent = quotaComponent;
            this.identifier = identifier;
            this.quotaType = quotaType;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(quotaComponent, identifier, quotaType);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Key) {
                Key other = (Key) o;
                return Objects.equals(quotaComponent, other.quotaComponent)
                    && Objects.equals(identifier, other.identifier)
                    && Objects.equals(quotaType, other.quotaType);
            }
            return false;
        }

        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("quotaComponent", quotaComponent)
                .add("identifier", identifier)
                .add("quotaType", quotaType)
                .toString();
        }
    }

    public static class Builder {
        private QuotaComponent quotaComponent;
        private String identifier;
        private QuotaType quotaType;
        private long currentValue;

        public Builder quotaComponent(QuotaComponent quotaComponent) {
            this.quotaComponent = quotaComponent;
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

        public Builder currentValue(long currentValue) {
            this.currentValue = currentValue;
            return this;
        }

        public QuotaCurrentValue build() {
            Preconditions.checkState(quotaComponent != null, "`quotaComponent` is mandatory");
            Preconditions.checkState(identifier != null, "`identifier` is mandatory");
            Preconditions.checkState(quotaType != null, "`quotaType` is mandatory");

            return new QuotaCurrentValue(quotaComponent, identifier, quotaType, currentValue);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final QuotaComponent quotaComponent;
    private final String identifier;
    private final QuotaType quotaType;
    private final long currentValue;

    private QuotaCurrentValue(QuotaComponent quotaComponent, String identifier, QuotaType quotaType, long currentValue) {
        this.quotaComponent = quotaComponent;
        this.identifier = identifier;
        this.quotaType = quotaType;
        this.currentValue = currentValue;
    }

    public QuotaComponent getQuotaComponent() {
        return quotaComponent;
    }

    public String getIdentifier() {
        return identifier;
    }

    public QuotaType getQuotaType() {
        return quotaType;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaComponent, identifier, quotaType, currentValue);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaCurrentValue) {
            QuotaCurrentValue other = (QuotaCurrentValue) o;
            return Objects.equals(quotaComponent, other.quotaComponent)
                && Objects.equals(identifier, other.identifier)
                && Objects.equals(quotaType, other.quotaType)
                && Objects.equals(currentValue, other.currentValue);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaComponent", quotaComponent)
            .add("identifier", identifier)
            .add("quotaType", quotaType)
            .add("currentValue", currentValue)
            .toString();
    }
}