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

import static org.apache.james.droplists.api.OwnerScope.DOMAIN;
import static org.apache.james.droplists.api.OwnerScope.GLOBAL;
import static org.apache.james.droplists.api.OwnerScope.USER;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class DropListEntry {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private OwnerScope ownerScope;
        private Optional<String> owner = Optional.empty();
        private DeniedEntityType deniedEntityType;
        private String deniedEntity;

        public Builder userOwner(MailAddress mailAddress) {
            Preconditions.checkNotNull(mailAddress);
            this.owner = Optional.of(mailAddress.toString());
            this.ownerScope = USER;
            return this;
        }

        public Builder domainOwner(Domain domain) {
            Preconditions.checkNotNull(domain);
            this.owner = Optional.of(domain.asString());
            this.ownerScope = DOMAIN;
            return this;
        }

        public Builder forAll() {
            this.ownerScope = GLOBAL;
            return this;
        }

        public Builder denyDomain(Domain domain) {
            Preconditions.checkNotNull(domain);
            this.deniedEntity = domain.asString();
            this.deniedEntityType = DeniedEntityType.DOMAIN;
            return this;
        }

        public Builder denyAddress(MailAddress mailAddress) {
            Preconditions.checkNotNull(mailAddress);
            this.deniedEntity = mailAddress.toString();
            this.deniedEntityType = DeniedEntityType.ADDRESS;
            return this;
        }

        public DropListEntry build() {
            Preconditions.checkArgument(deniedEntityType != null, "`deniedEntityType` is mandatory");
            Preconditions.checkArgument(ownerScope != null, "`ownerScope` is mandatory");
            Preconditions.checkArgument(deniedEntity != null && !deniedEntity.isBlank(), "`deniedEntity` must not be null, empty, or blank");
            return new DropListEntry(ownerScope, owner, deniedEntityType, deniedEntity);
        }
    }

    private final OwnerScope ownerScope;
    private final Optional<String> owner;
    private final DeniedEntityType deniedEntityType;
    private final String deniedEntity;

    private DropListEntry(OwnerScope ownerScope, Optional<String> owner, DeniedEntityType deniedEntityType, String deniedEntity) {
        this.ownerScope = ownerScope;
        this.owner = owner;
        this.deniedEntityType = deniedEntityType;
        this.deniedEntity = deniedEntity;
    }

    public OwnerScope getOwnerScope() {
        return ownerScope;
    }

    public String getOwner() {
        return owner.orElse("");
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
        MoreObjects.ToStringHelper result = MoreObjects.toStringHelper(this)
            .add("ownerScope", ownerScope);
        owner.ifPresent(o -> result.add("owner", o));
        result.add("deniedType", deniedEntityType)
            .add("deniedEntity", deniedEntity);
        return result.toString();
    }
}