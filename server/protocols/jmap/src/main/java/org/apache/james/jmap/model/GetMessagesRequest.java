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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.util.streams.Collectors;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

@JsonDeserialize(builder = GetMessagesRequest.Builder.class)
public class GetMessagesRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }
    
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        
        private Optional<String> accountId;
        private final ImmutableList.Builder<MessageId> ids;
        private ImmutableSet.Builder<String> propertiesBuilder;

        private Builder() {
            accountId = Optional.empty();
            ids = ImmutableList.builder();
            propertiesBuilder = null;
        }
        
        public Builder accountId(String accountId) {
            this.accountId = Optional.of(accountId);
            return this;
        }

        public Builder ids(MessageId... ids) {
            this.ids.addAll(Arrays.asList(ids));
            return this;
        }

        public Builder properties(List<String> properties) {
            if (propertiesBuilder == null) {
                propertiesBuilder = ImmutableSet.builder();
            }
            this.propertiesBuilder.addAll(properties);
            return this;
        }
        
        public GetMessagesRequest build() {
            return new GetMessagesRequest(accountId, ids.build(), messageProperties(propertiesBuilder), messageHeaderProperties(propertiesBuilder));
        }

        private Optional<ImmutableSet<MessageProperty>> messageProperties(ImmutableSet.Builder<String> messageProperties) {
            if (messageProperties == null) {
                return Optional.empty();
            }
            return toOptional(messageProperties.build().stream()
                        .filter(property -> !isHeaderProperty(property))
                        .map(MessageProperty::valueOf)
                        .collect(Collectors.toImmutableSet()),
                    MessageProperty.class);
        }

        private Optional<ImmutableSet<MessageHeaderProperty>> messageHeaderProperties(ImmutableSet.Builder<String> headerProperties) {
            if (headerProperties == null) {
                return Optional.empty();
            }
            return toOptional(headerProperties.build().stream()
                        .filter(this::isHeaderProperty)
                        .map(MessageHeaderProperty::valueOf)
                        .collect(Collectors.toImmutableSet()),
                    MessageHeaderProperty.class);
        }

        private boolean isHeaderProperty(String property) {
            return property.startsWith(MessageHeaderProperty.HEADER_PROPERTY_PREFIX);
        }

        private <T extends Property> Optional<ImmutableSet<T>> toOptional(ImmutableSet<T> set, Class<T> clazz) {
            return Optional.of(set);
        }
    }

    private final Optional<String> accountId;
    private final ImmutableList<MessageId> ids;
    private final Optional<ImmutableSet<MessageProperty>> properties;
    private final Optional<ImmutableSet<MessageHeaderProperty>> headerProperties;

    public GetMessagesRequest(Optional<String> accountId, ImmutableList<MessageId> ids, 
            Optional<ImmutableSet<MessageProperty>> properties, Optional<ImmutableSet<MessageHeaderProperty>> headerProperties) {
        this.accountId = accountId;
        this.ids = ids;
        this.properties = properties;
        this.headerProperties = headerProperties;
    }
    
    public Optional<String> getAccountId() {
        return accountId;
    }
    
    public ImmutableList<MessageId> getIds() {
        return ids;
    }
    
    public Optional<ImmutableSet<MessageProperty>> getProperties() {
        return properties;
    }

    public Optional<ImmutableSet<MessageHeaderProperty>> getHeaderProperties() {
        return headerProperties;
    }
}
