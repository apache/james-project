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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.mail.internet.AddressException;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class User {
    public static User fromUsername(String username) {
        Preconditions.checkNotNull(username);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(username));

        List<String> parts = ImmutableList.copyOf(Splitter.on('@').split(username));
        switch (parts.size()) {
            case 1:
                return fromLocalPartWithoutDomain(username);
            case 2:
                return fromLocalPartWithDomain(parts.get(0), parts.get(1));
        }
        throw new IllegalArgumentException("The username should not contain multiple domain delimiter.");
    }

    public static User fromLocalPartWithDomain(String localPart, String domain) {
        Preconditions.checkNotNull(domain);

        return fromLocalPartWithDomain(localPart, Domain.of(domain));
    }

    public static User fromLocalPartWithDomain(String localPart, Domain domain) {
        Preconditions.checkNotNull(domain);

        return new User(localPart, Optional.of(domain));
    }

    public static User fromMailAddress(MailAddress address) {
        Preconditions.checkNotNull(address);

        return new User(address.getLocalPart(), Optional.of(address.getDomain()));
    }

    public static User fromLocalPartWithoutDomain(String localPart) {
        return from(localPart,
            Optional.empty());
    }

    public static User from(String localPart, Optional<String> domain) {
       return new User(localPart, domain.map(Domain::of));
    }

    private final String localPart;
    private final Optional<Domain> domainPart;

    private User(String localPart, Optional<Domain> domainPart) {
        Preconditions.checkNotNull(localPart);
        Preconditions.checkArgument(!localPart.isEmpty(), "username should not be empty");
        Preconditions.checkArgument(!localPart.contains("@"), "username can not contain domain delimiter");

        this.localPart = localPart;
        this.domainPart = domainPart;
    }

    public String getLocalPart() {
        return localPart;
    }

    public Optional<Domain> getDomainPart() {
        return domainPart;
    }

    public boolean hasDomainPart() {
        return domainPart.isPresent();
    }

    public User withDefaultDomain(Optional<Domain> defaultDomain) {
        if (hasDomainPart()) {
            return this;
        }
        return new User(localPart, defaultDomain);
    }

    public User withDefaultDomain(Domain defaultDomain) {
        return withDefaultDomain(Optional.of(defaultDomain));
    }

    public User withDefaultDomainFromUser(User other) {
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
        if (o instanceof User) {
            User user = (User) o;

            return Objects.equals(this.localPart, user.localPart)
                && Objects.equals(this.domainPart, user.domainPart);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(localPart, domainPart);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("localPart", localPart)
            .add("domainPart", domainPart)
            .toString();
    }
}
