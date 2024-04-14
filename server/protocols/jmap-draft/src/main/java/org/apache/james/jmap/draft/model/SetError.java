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

package org.apache.james.jmap.draft.model;

import java.util.Optional;
import java.util.Set;

import org.apache.james.jmap.model.MessageProperties.MessageProperty;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@JsonDeserialize(builder = SetError.Builder.class)
public class SetError {

    public enum Type {
        INVALID_ARGUMENTS("invalidArguments"),
        INVALID_PROPERTIES("invalidProperties"),
        ERROR("anErrorOccurred"),
        MAX_QUOTA_REACHED("maxQuotaReached"),
        MAILBOX_HAS_CHILD("mailboxHasChild"),
        NOT_FOUND("notFound");

        private final String stringValue;

        Type(String stringValue) {
            this.stringValue = stringValue;
        }

        public String asString() {
            return stringValue;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private Type type;
        private String description;
        private Optional<ImmutableSet<MessageProperty>> properties = Optional.empty();

        protected Builder() {
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder properties(MessageProperty... properties) {
            return properties(ImmutableSet.copyOf(properties));
        }

        public Builder properties(Set<MessageProperty> properties) {
            this.properties = Optional.of(Sets.union(
                    this.properties.orElse(ImmutableSet.of()),
                    Optional.ofNullable(properties).orElse(ImmutableSet.of()))
                    .immutableCopy());
            return this;
        }

        public SetError build() {
            Preconditions.checkState(type != null, "'type' is mandatory");
            return new SetError(type, Optional.ofNullable(description), properties);
        }
    }

    private final Type type;
    private final Optional<String> description;
    private final Optional<ImmutableSet<MessageProperty>> properties;


    @VisibleForTesting SetError(Type type, Optional<String> description, Optional<ImmutableSet<MessageProperty>> properties) {
        this.type = type;
        this.description = description;
        this.properties = properties;
    }

    protected SetError(SetError setError) {
        this.type = setError.type;
        this.description = setError.description;
        this.properties = setError.properties;
    }

    
    @JsonSerialize
    public String getType() {
        return type.asString();
    }

    @JsonSerialize
    public Optional<String> getDescription() {
        return description;
    }

    @JsonSerialize
    public Optional<ImmutableSet<MessageProperty>> getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SetError) {
            SetError other = (SetError) obj;
            return Objects.equal(this.type, other.type)
                && Objects.equal(this.description, other.description)
                && Objects.equal(this.properties, other.properties);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, description, properties);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("description", description)
                .add("type", type)
                .add("properties", properties)
                .toString();
    }
}
