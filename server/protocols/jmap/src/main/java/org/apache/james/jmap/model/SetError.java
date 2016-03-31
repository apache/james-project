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

package org.apache.james.jmap.model;

import java.util.Optional;
import java.util.Set;

import org.apache.james.jmap.model.MessageProperties.MessageProperty;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@JsonDeserialize(builder = SetError.Builder.class)
public class SetError {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String type;
        private String description;
        private Optional<ImmutableSet<MessageProperty>> properties = Optional.empty();

        private Builder() {
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder properties(Set<MessageProperty> properties) {
            this.properties = Optional.of(Sets.union(
                    this.properties.orElse(ImmutableSet.of()),
                    Optional.ofNullable(properties).orElse(ImmutableSet.of()))
                    .immutableCopy());
            return this;
        }

        public SetError build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(type), "'type' is mandatory");
            return new SetError(type, Optional.ofNullable(description), properties);
        }
    }

    private final String type;
    private final Optional<String> description;
    private final Optional<ImmutableSet<MessageProperty>> properties;


    @VisibleForTesting SetError(String type, Optional<String> description, Optional<ImmutableSet<MessageProperty>> properties) {
        this.type = type;
        this.description = description;
        this.properties = properties;
    }

    @JsonSerialize
    public String getType() {
        return type;
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
}
