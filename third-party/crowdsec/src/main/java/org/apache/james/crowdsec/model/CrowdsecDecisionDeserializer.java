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

package org.apache.james.crowdsec.model;

import java.io.IOException;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class CrowdsecDecisionDeserializer extends StdDeserializer<CrowdsecDecision> {
    public CrowdsecDecisionDeserializer() {
        this(null);
    }

    protected CrowdsecDecisionDeserializer(Class<?> vc) {
        super(vc);
    }

    public CrowdsecDecision deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        return CrowdsecDecision.builder()
            .duration(Duration.parse("PT" + node.get("duration").asText()))
            .id(node.get("id").asLong())
            .origin(node.get("origin").asText())
            .scenario(node.get("scenario").asText())
            .scope(node.get("scope").asText())
            .type(node.get("type").asText())
            .value(node.get("value").asText())
            .build();
    }
}
