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

package org.apache.james.mailbox.model;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Domain;

import com.google.common.base.MoreObjects;

/**
 * Represents RFC 2087 Quota root
 */
public class QuotaRoot {

    public static QuotaRoot quotaRoot(String value, Optional<Domain> domain) {
        return new QuotaRoot(value, domain);
    }

    private final String value;
    private final Optional<Domain> domain;

    private QuotaRoot(String value, Optional<Domain> domain) {
        this.value = value;
        this.domain = domain;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaRoot) {
            QuotaRoot quotaRoot = (QuotaRoot) o;

            return Objects.equals(this.value, quotaRoot.value)
                && Objects.equals(this.domain, quotaRoot.domain);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value, domain);
    }

    public String getValue() {
        return value;
    }

    public Optional<Domain> getDomain() {
        return domain;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("value", value)
                .add("domain", domain)
                .toString();
    }

    public String asString() {
        return domain.map(domainValue -> value + "@" + domainValue.asString())
            .orElse(value);
    }
}
