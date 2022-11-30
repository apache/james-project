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

package org.apache.james.jwt.userinfo;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

public class UserinfoResponse {
    private final String sub;
    private final Optional<String> preferredUsername;
    private final Optional<String> email;

    private final JsonNode json;

    public UserinfoResponse(JsonNode json) {
        this.json = json;
        this.preferredUsername = Optional.ofNullable(json.get("preferred_username"))
            .map(JsonNode::asText);

        this.sub = Optional.ofNullable(json.get("sub"))
            .map(JsonNode::asText).orElse(null);

        this.email = Optional.ofNullable(json.get("email"))
            .map(JsonNode::asText);
    }

    public String getSub() {
        return sub;
    }

    public Optional<String> getPreferredUsername() {
        return preferredUsername;
    }

    public Optional<String> getEmail() {
        return email;
    }

    public Optional<String> claimByPropertyName(String propertyName) {
        return Optional.ofNullable(json.get(propertyName))
            .map(JsonNode::asText);
    }
}
