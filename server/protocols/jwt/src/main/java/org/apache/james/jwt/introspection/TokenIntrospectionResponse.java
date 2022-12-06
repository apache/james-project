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

package org.apache.james.jwt.introspection;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.google.common.base.Preconditions;

/**
 * https://datatracker.ietf.org/doc/html/rfc7662#section-2.2
 */
public class TokenIntrospectionResponse {
    public static TokenIntrospectionResponse parse(JsonNode json) {
        return new TokenIntrospectionResponse(json);
    }

    private final boolean active;
    private final Optional<String> scope;
    private final Optional<String> clientId;
    private final Optional<String> username;
    private final Optional<String> tokenType;
    private final Optional<Integer> exp;
    private final Optional<Integer> iat;
    private final Optional<Integer> nbf;
    private final Optional<String> sub;
    private final Optional<String> aud;
    private final Optional<String> iss;
    private final Optional<String> jti;
    private final JsonNode json;

    public TokenIntrospectionResponse(JsonNode json) {
        Preconditions.checkNotNull(json);
        JsonNode activeNode = json.get("active");
        Preconditions.checkArgument(activeNode instanceof BooleanNode, "Missing / invalid boolean 'active' parameter");
        this.active = activeNode.asBoolean();
        this.scope = Optional.ofNullable(json.get("scope"))
            .map(JsonNode::asText);
        this.clientId = Optional.ofNullable(json.get("client_id"))
            .map(JsonNode::asText);
        this.username = Optional.ofNullable(json.get("username"))
            .map(JsonNode::asText);
        this.tokenType = Optional.ofNullable(json.get("token_type"))
            .map(JsonNode::asText);
        this.exp = Optional.ofNullable(json.get("exp"))
            .map(JsonNode::asInt);
        this.iat = Optional.ofNullable(json.get("iat"))
            .map(JsonNode::asInt);
        this.nbf = Optional.ofNullable(json.get("nbf"))
            .map(JsonNode::asInt);
        this.sub = Optional.ofNullable(json.get("sub"))
            .map(JsonNode::asText);
        this.aud = Optional.ofNullable(json.get("aud"))
            .map(JsonNode::asText);
        this.iss = Optional.ofNullable(json.get("iss"))
            .map(JsonNode::asText);
        this.jti = Optional.ofNullable(json.get("jti"))
            .map(JsonNode::asText);
        this.json = json;
    }

    public boolean active() {
        return active;
    }

    public Optional<String> scope() {
        return scope;
    }

    public JsonNode json() {
        return json;
    }

    public Optional<String> clientId() {
        return clientId;
    }

    public Optional<String> username() {
        return username;
    }

    public Optional<String> tokenType() {
        return tokenType;
    }

    public Optional<Integer> exp() {
        return exp;
    }

    public Optional<Integer> iat() {
        return iat;
    }

    public Optional<Integer> nbf() {
        return nbf;
    }

    public Optional<String> sub() {
        return sub;
    }

    public Optional<String> aud() {
        return aud;
    }

    public Optional<String> iss() {
        return iss;
    }

    public Optional<String> jti() {
        return jti;
    }

    public Optional<String> claimByPropertyName(String propertyName) {
        return Optional.ofNullable(json.get(propertyName))
            .map(JsonNode::asText);
    }
}
