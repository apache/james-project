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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;

public class ContinuationTokenResponse {

    public enum AuthenticationMethod {
        PASSWORD("password"),
        EXTERNAL("external"),
        PROMPT("prompt");

        private final String value;

        AuthenticationMethod(String value) {
            this.value = value;
        }

        @JsonValue
        public String toString() {
            return value;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String continuationToken;
        private ImmutableList<AuthenticationMethod> methods;
        private String prompt;

        private Builder() {

        }

        public Builder continuationToken(ContinuationToken continuationToken) {
            this.continuationToken = continuationToken.serialize();
            return this;
        }

        public Builder methods(List<AuthenticationMethod> methods) {
            this.methods = ImmutableList.copyOf(methods);
            return this;
        }

        public Builder methods(AuthenticationMethod... methods) {
            this.methods = ImmutableList.copyOf(methods);
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public ContinuationTokenResponse build() {
            return new ContinuationTokenResponse(continuationToken, methods, prompt);
        }
    }

    private final String continuationToken;
    private final ImmutableList<AuthenticationMethod> methods;
    private final String prompt;

    private ContinuationTokenResponse(String continuationToken, ImmutableList<AuthenticationMethod> methods, String prompt) {
        this.continuationToken = continuationToken;
        this.methods = methods;
        this.prompt = prompt;
    }

    public String getContinuationToken() {
        return continuationToken;
    }

    public List<AuthenticationMethod> getMethods() {
        return methods;
    }

    public String getPrompt() {
        return prompt;
    }
}