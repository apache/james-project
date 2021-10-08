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

package org.apache.james;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.james.core.Username;

import com.google.common.collect.ImmutableSet;

public interface UserEntityValidator {
    class ValidationFailure {
        private final String errorMessage;

        public ValidationFailure(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }

    class EntityType {
        public static final EntityType GROUP = new EntityType("group");
        public static final EntityType ALIAS = new EntityType("alias");
        public static final EntityType USER = new EntityType("user");

        private final String type;

        public EntityType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EntityType) {
                EntityType other = (EntityType) obj;
                return Objects.equals(this.type, other.type);
            }
            return false;
        }
    }

    UserEntityValidator NOOP = (username, ignoredTypes) -> Optional.empty();

    static UserEntityValidator aggregate(UserEntityValidator... validators) {
        return new AggregateUserEntityValidator(ImmutableSet.copyOf(validators));
    }

    Optional<ValidationFailure> canCreate(Username username, Set<EntityType> ignoredTypes) throws Exception;

    default Optional<ValidationFailure> canCreate(Username username) throws Exception {
        return canCreate(username, ImmutableSet.of());
    }
}
