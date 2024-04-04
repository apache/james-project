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

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class DropListEntry {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<OwnerScope> ownerScope = Optional.empty();
        private Optional<String> owner = Optional.empty();
        private Optional<String> deniedEntityType = Optional.empty();
        private Optional<String> deniedEntity = Optional.empty();

        public Builder ownerScope(OwnerScope ownerScope) {
            Preconditions.checkNotNull(ownerScope);
            this.ownerScope = Optional.of(ownerScope);
            return this;
        }

        public Builder owner(String owner) {
            Preconditions.checkNotNull(owner);
            this.owner = Optional.of(owner);
            return this;
        }

        public Builder deniedEntityType(String deniedEntityType) {
            Preconditions.checkNotNull(deniedEntityType);
            this.deniedEntityType = Optional.of(deniedEntityType);
            return this;
        }

        public Builder deniedEntity(String deniedEntity) {
            Preconditions.checkNotNull(deniedEntity);
            this.deniedEntity = Optional.of(deniedEntity);
            return this;
        }

        public Builder copy(DropListEntry dropListEntry) {
            this.ownerScope = dropListEntry.getOwnerScope();
            this.owner = dropListEntry.getOwner();
            this.deniedEntityType = dropListEntry.getDeniedEntityType();
            this.deniedEntity = dropListEntry.getDeniedEntity();
            return this;
        }

        public DropListEntry build() {
            return new DropListEntry(ownerScope, owner, deniedEntityType, deniedEntity);
        }
    }

    private Optional<OwnerScope> ownerScope;
    private Optional<String> owner;
    private Optional<String> deniedEntityType;
    private Optional<String> deniedEntity;

    public DropListEntry(Optional<OwnerScope> ownerScope, Optional<String> owner, Optional<String> deniedEntityType, Optional<String> deniedEntity) {
        this.ownerScope = ownerScope;
        this.owner = owner;
        this.deniedEntityType = deniedEntityType;
        this.deniedEntity = deniedEntity;
    }

    public Optional<OwnerScope> getOwnerScope() {
        return ownerScope;
    }

    public Optional<String> getOwner() {
        return owner;
    }

    public Optional<String> getDeniedEntityType() {
        return deniedEntityType;
    }

    public Optional<String> getDeniedEntity() {
        return deniedEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DropListEntry that = (DropListEntry) o;
        return ownerScope == that.ownerScope &&
            Objects.equals(owner, that.owner) &&
            Objects.equals(deniedEntityType, that.deniedEntityType) &&
            Objects.equals(deniedEntity, that.deniedEntity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerScope, owner, deniedEntityType, deniedEntity);
    }
}
