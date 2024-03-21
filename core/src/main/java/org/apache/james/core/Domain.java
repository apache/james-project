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

import jakarta.annotation.Nonnull;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;

public class Domain implements Serializable {
    private static final CharMatcher DASH_MATCHER = CharMatcher.anyOf("-_");
    private static final CharMatcher DIGIT_MATCHER = CharMatcher.inRange('0', '9');
    private static final CharMatcher PART_CHAR_MATCHER = new CharMatcher() {
        @Override
        public boolean matches(char c) {
            return Character.isLetterOrDigit(c) || '-' == c || '_' == c || '.' == c;
        }
    };

    public static final Domain LOCALHOST = Domain.of("localhost");
    public static final int MAXIMUM_DOMAIN_LENGTH = 253;

    private static String removeBrackets(String domainName) {
        if (!(domainName.startsWith("[") && domainName.endsWith("]"))) {
            return domainName;
        }
        return domainName.substring(1, domainName.length() - 1);
    }

    public static Domain of(String domain) {
        Preconditions.checkNotNull(domain, "Domain can not be null");
        Preconditions.checkArgument(domain.length() <= MAXIMUM_DOMAIN_LENGTH,
            "Domain name length should not exceed %s characters", MAXIMUM_DOMAIN_LENGTH);

        String domainWithoutBrackets = removeBrackets(domain);
        Preconditions.checkArgument(PART_CHAR_MATCHER.matchesAllOf(domainWithoutBrackets), "Domain parts ASCII chars must be a-z A-Z 0-9 - or _");

        int pos = 0;
        int nextDot = domainWithoutBrackets.indexOf('.');

        while (nextDot > -1) {
            if (pos + 1 > domainWithoutBrackets.length()) {
                throw new IllegalArgumentException("Last domain part should not be empty");
            }
            Domain.assertValidPart(domainWithoutBrackets, pos, nextDot);
            pos = nextDot + 1;
            nextDot = domainWithoutBrackets.indexOf('.', pos);
        }
        Domain.assertValidPart(domainWithoutBrackets, pos, domainWithoutBrackets.length());
        assertValidLastPart(domainWithoutBrackets, pos);
        return new Domain(domainWithoutBrackets);
    }

    private static void assertValidPart(String domainPart, int begin, int end) {
        Preconditions.checkArgument(begin != end, "Domain part should not be empty");
        Preconditions.checkArgument(!DASH_MATCHER.matches(domainPart.charAt(begin)), "Domain part should not start with '-' or '_'");
        Preconditions.checkArgument(!DASH_MATCHER.matches(domainPart.charAt(end - 1)), "Domain part should not end with '-' or '_'");
        Preconditions.checkArgument(end - begin <= 63, "Domain part should not not exceed 63 characters");
    }

    private static void assertValidLastPart(String domainPart, int pos) {
        boolean onlyDigits = DIGIT_MATCHER.matches(domainPart.charAt(pos));
        boolean invalid = onlyDigits && !validIPAddress(domainPart);
        Preconditions.checkArgument(!invalid, "The last domain part must not start with 0-9");
    }

    private static boolean validIPAddress(String value) {
        try {
            InetAddresses.forString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Nonnull
    private final String domainName;
    @Nonnull
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
        return normalizedDomainName.hashCode();
    }

    @Override
    public String toString() {
        return "Domain : " + domainName;
    }

}
