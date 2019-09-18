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

import org.apache.james.jmap.exceptions.MalformedContinuationTokenException;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = AccessTokenRequest.Builder.class)
public class AccessTokenRequest {

    public static final String UNIQUE_JSON_PATH = "/token";

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private ContinuationToken token;
        private String method;
        private String password;

        private Builder() {

        }

        public Builder token(String token) throws MalformedContinuationTokenException {
            this.token = ContinuationToken.fromString(token);
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public AccessTokenRequest build() {
            return new AccessTokenRequest(token, method, password);
        }
    }

    private final ContinuationToken token;
    private final String method;
    private final String password;

    private AccessTokenRequest(ContinuationToken token, String method, String password) {
        this.token = token;
        this.method = method;
        this.password = password;
    }

    public ContinuationToken getToken() {
        return token;
    }

    public String getMethod() {
        return method;
    }

    public String getPassword() {
        return password;
    }
}
