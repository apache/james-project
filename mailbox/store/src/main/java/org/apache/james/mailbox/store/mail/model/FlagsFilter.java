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
package org.apache.james.mailbox.store.mail.model;

import java.util.Optional;
import java.util.function.Predicate;

import jakarta.mail.Flags;

public class FlagsFilter {

    public static Builder builder() {
        return new Builder();
    }

    public static FlagsFilter noFilter() {
        return builder().build();
    }

    public static class Builder {
        private Optional<SystemFlagFilter> systemFlagFilter;
        private Optional<UserFlagFilter> userFlagFilter;

        private Builder() {
            systemFlagFilter = Optional.empty();
            userFlagFilter = Optional.empty();
        }

        public Builder systemFlagFilter(SystemFlagFilter filter) {
            this.systemFlagFilter = Optional.of(filter);
            return this;
        }

        public Builder userFlagFilter(UserFlagFilter filter) {
            this.userFlagFilter = Optional.of(filter);
            return this;
        }

        public FlagsFilter build() {
            return new FlagsFilter(
                systemFlagFilter.orElse((Flags.Flag f) -> true),
                userFlagFilter.orElse((String s) -> true)
                );
        }

    }

    @FunctionalInterface
    public interface SystemFlagFilter extends Predicate<Flags.Flag> {
    }

    @FunctionalInterface
    public interface UserFlagFilter extends Predicate<String> {
    }

    private final SystemFlagFilter systemFlagFilter;
    private final UserFlagFilter userFlagFilter;

    private FlagsFilter(SystemFlagFilter systemFlagFilter, UserFlagFilter userFlagFilter) {
        this.systemFlagFilter = systemFlagFilter;
        this.userFlagFilter = userFlagFilter;
    }

    public SystemFlagFilter getSystemFlagFilter() {
        return systemFlagFilter;
    }

    public UserFlagFilter getUserFlagFilter() {
        return userFlagFilter;
    }
}
