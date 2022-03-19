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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class Username {
    public static final int MAXIMUM_MAIL_ADDRESS_LENGTH = 255;

    public static Username of(String username) {
        Preconditions.checkArgument(username != null, "username should not be null or empty");
        Preconditions.checkArgument(!username.isBlank(), "username should not be null or empty after being trimmed");
        Preconditions.checkArgument(username.length() <= MAXIMUM_MAIL_ADDRESS_LENGTH,
            "username length should not be longer than %s characters", MAXIMUM_MAIL_ADDRESS_LENGTH);

        int atPosition = username.indexOf('@');
        if (atPosition < 0) {
            return fromLocalPartWithoutDomain(username);
        }
        String userPart = username.substring(0, atPosition);
        String domainPart = username.substring(atPosition + 1);
        return fromLocalPartWithDomain(userPart, domainPart);
    }

    public static Username fromLocalPartWithDomain(String localPart, String domain) {
        Preconditions.checkNotNull(domain);

        return fromLocalPartWithDomain(localPart, Domain.of(domain));
    }

    public static Username fromLocalPartWithDomain(String localPart, Domain domain) {
        Preconditions.checkNotNull(domain);

        return new Username(localPart, Optional.of(domain));
    }

    public static Username fromMailAddress(MailAddress address) {
        Preconditions.checkNotNull(address);

        return new Username(address.getLocalPart(), Optional.of(address.getDomain()));
    }

    public static Username fromLocalPartWithoutDomain(String localPart) {
        return from(localPart,
            Optional.empty());
    }

    public static Username from(String localPart, Optional<String> domain) {
       return new Username(localPart, domain.map(Domain::of));
    }

    private final String localPart;
    private final Optional<Domain> domainPart;

    private Username(String localPart, Optional<Domain> domainPart) {
        Preconditions.checkNotNull(localPart);
        Preconditions.checkArgument(!localPart.isEmpty(), "username local part should not be empty");
        Preconditions.checkArgument(!localPart.contains("@"), "username local part can not contain domain delimiter, got %s", localPart);

        this.localPart = localPart.toLowerCase(Locale.US);
        this.domainPart = domainPart;
    }

    public String getLocalPart() {
        return localPart;
    }

    public Optional<Domain> getDomainPart() {
        return domainPart;
    }

    public Username withOtherDomain(Optional<Domain> domain) {
        return new Username(localPart, domain);
    }

    public Username withOtherDomain(Domain domain) {
        return withOtherDomain(Optional.of(domain));
    }

    public boolean hasDomainPart() {
        return domainPart.isPresent();
    }

    public Username withDefaultDomain(Optional<Domain> defaultDomain) {
        if (hasDomainPart()) {
            return this;
        }
        return new Username(localPart, defaultDomain);
    }

    public Username withDefaultDomain(Domain defaultDomain) {
        return withDefaultDomain(Optional.of(defaultDomain));
    }

    public Username withDefaultDomainFromUser(Username other) {
        return withDefaultDomain(other.domainPart);
    }

    public String asString() {
        return domainPart.map(domain -> localPart + "@" + domain.asString())
            .orElse(localPart);
    }

    public MailAddress asMailAddress() throws AddressException {
        Preconditions.checkState(hasDomainPart());
        return new MailAddress(localPart, domainPart.get());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Username) {
            Username username = (Username) o;

            return Objects.equals(this.localPart, username.localPart)
                && Objects.equals(this.domainPart, username.domainPart);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return localPart.hashCode() + 31 * domainPart.hashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("localPart", localPart)
            .add("domainPart", domainPart)
            .toString();
    }
}
