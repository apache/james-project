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

import java.util.Objects;
import java.util.Optional;

public class ErrorResponse implements Method.Response {
    public static final Method.Response.Name ERROR_METHOD = Method.Response.name("error");
    public static final String DEFAULT_ERROR_MESSAGE = "Error while processing";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<String> type = Optional.empty();
        private Optional<String> description = Optional.empty();

        private Builder() {
        }

        public Builder type(String type) {
            this.type = Optional.ofNullable(type);
            return this;
        }

        public Builder description(String description) {
            this.description = Optional.ofNullable(description);
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(type.orElse(DEFAULT_ERROR_MESSAGE), description);
        }
    }

    private final String type;
    private final Optional<String> description;

    public ErrorResponse(String type, Optional<String> description) {
        this.type = type;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public Optional<String> getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ErrorResponse that = (ErrorResponse) o;

        return Objects.equals(this.type, that.type)
            && Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description);
    }
}
