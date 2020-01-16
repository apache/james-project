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

package org.apache.james.core;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

import com.google.common.base.Preconditions;

public class Domain implements Serializable {

    public static final Domain LOCALHOST = Domain.of("localhost");
    public static final int MAXIMUM_DOMAIN_LENGTH = 255;

    private static String removeBrackets(String domainName) {
        if (!(domainName.startsWith("[") && domainName.endsWith("]"))) {
            return domainName;
        }
        return domainName.substring(1, domainName.length() - 1);
    }

    public static Domain of(String domain) {
        Preconditions.checkNotNull(domain, "Domain can not be null");
        Preconditions.checkArgument(!domain.isEmpty() && !domain.contains("@") && !domain.contains("/"),
            "Domain can not be empty nor contain `@` nor `/`");
        Preconditions.checkArgument(domain.length() <= MAXIMUM_DOMAIN_LENGTH,
            "Domain name length should not exceed %s characters", MAXIMUM_DOMAIN_LENGTH);
        return new Domain(domain);
    }

    private final String domainName;
    private final String normalizedDomainName;

    protected Domain(String domainName) {
        this.domainName = domainName;
        this.normalizedDomainName = removeBrackets(domainName.toLowerCase(Locale.US));
    }

    public String name() {
        return domainName;
    }

    public String asString() {
        return normalizedDomainName;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Domain) {
            Domain domain = (Domain) o;
            return Objects.equals(normalizedDomainName, domain.normalizedDomainName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(normalizedDomainName);
    }

    @Override
    public String toString() {
        return "Domain : " + domainName;
    }

}
