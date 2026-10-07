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

package org.apache.james.user.api.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * Helpers shared by the {@link org.apache.james.user.api.UsersRepository#searchPaginated} implementations.
 */
public final class UsersPaginationHelpers {
    public static final Comparator<Username> USERNAME_ALPHABETICAL_ORDER = Comparator.comparing(Username::asString);

    private UsersPaginationHelpers() {
    }

    public static Optional<Domain> domain(List<UsernamePredicate> predicates) {
        return predicates.stream()
            .filter(UsernamePredicate.DomainPredicate.class::isInstance)
            .map(UsernamePredicate.DomainPredicate.class::cast)
            .map(UsernamePredicate.DomainPredicate::domain)
            .findFirst();
    }

    public static Flux<Username> paginate(Publisher<Username> users, List<UsernamePredicate> predicates, Optional<Username> anchor, Optional<Integer> limit) {
        Flux<Username> sortedUsers = Flux.from(users)
            .filter(username -> matchesAll(username, predicates))
            .filter(username -> isAfter(username, anchor))
            .sort(USERNAME_ALPHABETICAL_ORDER);
        return limit.map(value -> sortedUsers.take(value, true))
            .orElse(sortedUsers);
    }

    public static boolean matchesAll(Username username, List<UsernamePredicate> predicates) {
        return predicates.stream().allMatch(predicate -> predicate.test(username));
    }

    public static boolean isAfter(Username username, Optional<Username> anchor) {
        return anchor.map(a -> USERNAME_ALPHABETICAL_ORDER.compare(username, a) > 0)
            .orElse(true);
    }
}
