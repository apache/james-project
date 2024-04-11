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
package org.apache.james.droplists.api;

import static org.apache.james.core.Domain.MAXIMUM_DOMAIN_LENGTH;
import static org.apache.james.droplists.api.OwnerScope.GLOBAL;

import java.util.Objects;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class DropListEntry {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<OwnerScope> ownerScope = Optional.empty();
        private String owner;
        private DeniedEntityType deniedEntityType;
        private String deniedEntity;

        public Builder ownerScope(OwnerScope ownerScope) {
            this.ownerScope = Optional.ofNullable(ownerScope);
            return this;
        }

        public Builder owner(String owner) {
            Preconditions.checkNotNull(owner);
            this.owner = owner;
            return this;
        }

        public Builder deniedEntityType(DeniedEntityType deniedEntityType) {
            Preconditions.checkNotNull(deniedEntityType);
            this.deniedEntityType = deniedEntityType;
            return this;
        }

        public Builder deniedEntity(String deniedEntity) {
            Preconditions.checkNotNull(deniedEntity);
            this.deniedEntity = deniedEntity;
            return this;
        }

        public DropListEntry build() throws AddressException {
            OwnerScope scope = ownerScope.orElse(GLOBAL);
            Preconditions.checkArgument(owner != null && !owner.trim().isBlank(), "owner must not be null, empty, or blank");
            Preconditions.checkArgument(owner.length() <= MAXIMUM_DOMAIN_LENGTH,
                "owner length should not be longer than %s characters", MAXIMUM_DOMAIN_LENGTH);
            Preconditions.checkArgument(deniedEntityType != null, "`deniedEntityType` is mandatory");
            Preconditions.checkArgument(deniedEntity != null && !deniedEntity.isBlank(), "`deniedEntity` must not be null, empty, or blank");
            Preconditions.checkArgument(deniedEntity.length() <= MAXIMUM_DOMAIN_LENGTH,
                "deniedEntity length should not be longer than %s characters", MAXIMUM_DOMAIN_LENGTH);
            if (deniedEntityType.equals(DeniedEntityType.DOMAIN)) {
                deniedEntity = Domain.of(deniedEntity).asString();
            } else {
                deniedEntity = new MailAddress(deniedEntity).toString();
            }
            return new DropListEntry(scope, owner, deniedEntityType, deniedEntity);
        }
    }

    private final OwnerScope ownerScope;
    private final String owner;
    private final DeniedEntityType deniedEntityType;
    private final String deniedEntity;

    private DropListEntry(OwnerScope ownerScope, String owner, DeniedEntityType deniedEntityType, String deniedEntity) {
        this.ownerScope = ownerScope;
        this.owner = owner;
        this.deniedEntityType = deniedEntityType;
        this.deniedEntity = deniedEntity;
    }

    public OwnerScope getOwnerScope() {
        return ownerScope;
    }

    public String getOwner() {
        return owner;
    }

    public DeniedEntityType getDeniedEntityType() {
        return deniedEntityType;
    }

    public String getDeniedEntity() {
        return deniedEntity;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DropListEntry dropListEntry) {
            return Objects.equals(ownerScope, dropListEntry.ownerScope) &&
                Objects.equals(owner, dropListEntry.owner) &&
                Objects.equals(deniedEntityType, dropListEntry.deniedEntityType) &&
                Objects.equals(deniedEntity, dropListEntry.deniedEntity);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(ownerScope, owner, deniedEntityType, deniedEntity);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("ownerScope", ownerScope)
            .add("owner", owner)
            .add("deniedType", deniedEntityType)
            .add("deniedEntity", deniedEntity)
            .toString();
    }
}