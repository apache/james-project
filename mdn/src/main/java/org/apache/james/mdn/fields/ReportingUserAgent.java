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

package org.apache.james.mdn.fields;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

/**
 * Implements optional Reporting-UA header field
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.1
 */
public class ReportingUserAgent implements Field {
    private static final String FIELD_NAME = "Reporting-UA";
    public static final Predicate<String> IS_EMPTY = String::isEmpty;
    private final String userAgentName;
    private final Optional<String> userAgentProduct;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String userAgentName;
        private Optional<String> userAgentProduct;

        private Builder() {
            userAgentProduct = Optional.empty();
        }

        public Builder userAgentName(String userAgentName) {
            this.userAgentName = userAgentName;
            return this;
        }

        public Builder userAgentProduct(String userAgentProduct) {
            this.userAgentProduct = Optional.of(userAgentProduct);
            return this;
        }

        public ReportingUserAgent build() {
            Preconditions.checkNotNull(userAgentName);
            Preconditions.checkNotNull(userAgentProduct);
            Preconditions.checkState(!userAgentName.contains("\n"), "Name should not contain line break");
            String trimmedName = userAgentName.trim();
            Preconditions.checkState(!trimmedName.isEmpty(), "Name should not be empty");

            return new ReportingUserAgent(trimmedName, userAgentProduct);
        }
    }

    private ReportingUserAgent(String userAgentName, Optional<String> userAgentProduct) {
        this.userAgentName = userAgentName;
        this.userAgentProduct = userAgentProduct
            .map(String::trim)
            .filter(IS_EMPTY.negate());
    }

    public String getUserAgentName() {
        return userAgentName;
    }

    public Optional<String> getUserAgentProduct() {
        return userAgentProduct;
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + fieldValue();
    }

    public String fieldValue() {
        return Joiner.on("; ").skipNulls().join(userAgentName, userAgentProduct.orElse(null));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ReportingUserAgent) {
            ReportingUserAgent that = (ReportingUserAgent) o;

            return Objects.equals(this.userAgentName, that.userAgentName)
                && Objects.equals(this.userAgentProduct, that.userAgentProduct);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(userAgentName, userAgentProduct);
    }

    @Override
    public String toString() {
        return formattedValue();
    }
}
