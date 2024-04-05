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

package org.apache.james.jmap.methods;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.jmap.model.Property;

import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class JmapResponse {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Method.Response.Name responseName;
        private MethodCallId methodCallId;
        private Method.Response response;
        private Optional<? extends Set<? extends Property>> properties = Optional.empty();
        private Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> filterProvider = Optional.empty();

        private Builder() {
        }

        public Builder responseName(Method.Response.Name name) {
            this.responseName = name;
            return this;
        }

        public Builder methodCallId(MethodCallId methodCallId) {
            this.methodCallId = methodCallId;
            return this;
        }

        public Builder response(Method.Response response) {
            this.response = response;
            return this;
        }

        public Builder properties(Optional<? extends Set<? extends Property>> properties) {
            this.properties = properties.map(ImmutableSet::copyOf);
            return this;
        }

        public Builder properties(Set<? extends Property> properties) {
            return properties(Optional.ofNullable(properties));
        }

        public Builder filterProvider(Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> filterProvider) {
            this.filterProvider = filterProvider;
            return this;
        }

        public Builder error() {
            this.response = ErrorResponse.builder().build();
            this.responseName = ErrorResponse.ERROR_METHOD;
            return this;
        }

        public Builder error(ErrorResponse error) {
            this.response = error;
            this.responseName = ErrorResponse.ERROR_METHOD;
            return this;
        }


        public JmapResponse build() {
            Preconditions.checkState(methodCallId != null, "'methodCallId' needs to be specified");
            Preconditions.checkState(responseName != null, "'responseName' needs to be specified");

            return new JmapResponse(responseName, methodCallId, response, properties, filterProvider);
        }
    }

    private final Method.Response.Name method;
    private final MethodCallId methodCallId;
    private final Method.Response response;
    private final Optional<? extends Set<? extends Property>> properties;
    private final Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> filterProvider;

    private JmapResponse(Method.Response.Name method, MethodCallId methodCallId,
                         Method.Response response,
                         Optional<? extends Set<? extends Property>> properties,
                         Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> filterProvider) {
        this.method = method;
        this.methodCallId = methodCallId;
        this.response = response;
        this.properties = properties;
        this.filterProvider = filterProvider;
    }

    public Method.Response.Name getResponseName() {
        return method;
    }

    public Method.Response getResponse() {
        return response;
    }

    public MethodCallId getMethodCallId() {
        return methodCallId;
    }

    public Optional<? extends Set<? extends Property>> getProperties() {
        return properties;
    }

    public Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> getFilterProvider() {
        return filterProvider;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(method, methodCallId, response, properties, filterProvider);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof JmapResponse) {
            JmapResponse that = (JmapResponse) object;
            return Objects.equal(this.method, that.method)
                    && Objects.equal(this.methodCallId, that.methodCallId)
                    && Objects.equal(this.response, that.response)
                    && Objects.equal(this.properties, that.properties)
                    && Objects.equal(this.filterProvider, that.filterProvider);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("method", method)
                .add("response", response)
                .add("methodCallId", methodCallId)
                .add("properties", properties)
                .add("filterProvider", filterProvider)
                .toString();
    }
}
