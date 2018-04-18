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

package org.apache.james.rrt.lib;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import javax.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingSource implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingSource.class);
    private static final String WILDCARD = "*";

    private enum WildCard {
        WildCard
    }

    public static MappingSource fromDomain(Domain domain) {
        if (domain.asString().equals(WILDCARD)) {
            return wildCard();
        }
        return new MappingSource(Optional.of(domain), Optional.empty(), Optional.empty());
    }

    public static MappingSource fromUser(String localPart, String domain) {
        return fromUser(localPart, Domain.of(domain));
    }

    public static MappingSource fromUser(String localPart, Domain domain) {
        User user = User.fromLocalPartWithDomain(localPart, domain);
        return fromUser(user);
    }

    public static MappingSource fromUser(User user) {
        if (user.getLocalPart().equals(WILDCARD)) {
            return MappingSource.fromDomain(user.getDomainPart().get());
        }
        return new MappingSource(Optional.empty(), Optional.of(user), Optional.empty());
    }

    public static MappingSource wildCard() {
        return new MappingSource(Optional.empty(), Optional.empty(), Optional.of(WildCard.WildCard));
    }

    public static MappingSource parse(String mappingSource) {
        switch (mappingSource) {
            case WILDCARD:
                return wildCard();
            default:
                if (mappingSource.startsWith(WILDCARD + "@")) {
                    return fromDomain(Domain.of(mappingSource.substring(2, mappingSource.length())));
                }
                return fromUser(User.fromUsername(mappingSource));
        }
    }

    private final Optional<Domain> domain;
    private final Optional<User> user;
    private final Optional<WildCard> wildcard;

    private MappingSource(Optional<Domain> domain, Optional<User> user, Optional<WildCard> wildcard) {
        this.domain = domain;
        this.user = user;
        this.wildcard = wildcard;
    }

    public Optional<Domain> asDomain() {
        return domain;
    }

    public Optional<MailAddress> asMailAddress() {
        return user.flatMap(user -> {
            try {
                return Optional.of(user.asMailAddress());
            } catch (AddressException e) {
                LOGGER.warn("Ignoring failing MappingSource to MailAddress conversion for user {}", user, e);
                return Optional.empty();
            }
        });
    }

    public String asString() {
        return OptionalUtils.orSuppliers(
                () -> wildcard.map(x -> "*"),
                () -> user.map(User::asString),
                () -> domain.map(Domain::asString))
            .orElseThrow(IllegalStateException::new);
    }

    public String getFixedUser() {
        return user.map(User::getLocalPart)
            .orElse(WILDCARD);
    }

    public String getFixedDomain() {
        return OptionalUtils.or(
                user.flatMap(User::getDomainPart).map(Domain::asString),
                domain.map(Domain::asString))
            .orElse(WILDCARD);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MappingSource) {
            MappingSource that = (MappingSource) o;

            return Objects.equals(this.domain, that.domain)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.wildcard, that.wildcard);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(domain, user, wildcard);
    }
}
