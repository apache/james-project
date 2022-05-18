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
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;

public class TokenIntrospectionResponse {
    public static TokenIntrospectionResponse parse(JsonNode json) {
        return new TokenIntrospectionResponse(json);
    }

    private final boolean active;
    private final Optional<String> scope;
    private final JsonNode json;

    public TokenIntrospectionResponse(JsonNode json) {
        Preconditions.checkNotNull(json);
        JsonNode activeNode = json.get("active");
        Preconditions.checkArgument(activeNode instanceof BooleanNode, "Missing / invalid boolean 'active' parameter");
        this.active = activeNode.asBoolean();
        this.scope = Optional.ofNullable(json.get("scope"))
            .filter(jsonNode -> jsonNode instanceof TextNode)
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
}
