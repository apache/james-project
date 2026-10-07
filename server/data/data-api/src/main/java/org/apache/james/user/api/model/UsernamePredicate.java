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

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;

import com.google.common.base.Preconditions;

/**
 * Typed criteria for searching users, allowing implementations to translate them into native queries.
 */
public sealed interface UsernamePredicate extends Predicate<Username> {
    /**
     * Matches users of the given domain.
     */
    record DomainPredicate(Domain domain) implements UsernamePredicate {
        public DomainPredicate {
            Objects.requireNonNull(domain);
        }

        @Override
        public boolean test(Username username) {
            return username.getDomainPart()
                .map(domain::equals)
                .orElse(false);
        }
    }

    /**
     * Matches users whose username starts (case insensitively) with the given prefix.
     */
    record UsernamePrefixPredicate(String prefix) implements UsernamePredicate {
        public UsernamePrefixPredicate {
            Preconditions.checkArgument(prefix != null && !prefix.isEmpty(), "'prefix' must not be empty");
            prefix = prefix.toLowerCase(Locale.US);
        }

        @Override
        public boolean test(Username username) {
            return username.asString().toLowerCase(Locale.US).startsWith(prefix);
        }
    }

    static DomainPredicate domain(Domain domain) {
        return new DomainPredicate(domain);
    }

    static UsernamePrefixPredicate prefix(String prefix) {
        return new UsernamePrefixPredicate(prefix);
    }
}
