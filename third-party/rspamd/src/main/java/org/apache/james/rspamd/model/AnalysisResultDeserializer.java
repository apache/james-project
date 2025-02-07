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

package org.apache.james.rspamd.model;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class AnalysisResultDeserializer extends StdDeserializer<AnalysisResult> {

    public AnalysisResultDeserializer() {
        this(null);
    }

    protected AnalysisResultDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public AnalysisResult deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        AnalysisResult.Action action = deserializeAction(node.get("action").asText());
        float score = node.get("score").floatValue();
        float requiredScore = node.get("required_score").floatValue();
        Optional<String> desiredRewriteSubject = deserializeRewriteSubject(node);
        Optional<String> virusNote = deserializeClamVirus(node);

        return new AnalysisResult(action,score, requiredScore, desiredRewriteSubject, virusNote);
    }

    private AnalysisResult.Action deserializeAction(String actionAsString) {
        for (AnalysisResult.Action action : AnalysisResult.Action.values()) {
            if (action.getDescription().equals(actionAsString)) {
                return action;
            }
        }
        throw new RuntimeException("There is no match deserialized action.");
    }

    private Optional<String> deserializeRewriteSubject(JsonNode node) {
        return Optional.ofNullable(node.get("subject")).map(JsonNode::asText);
    }

    private Optional<String> deserializeClamVirus(JsonNode node) {
        JsonNode clamVirusJsonNode = node.get("symbols").get("CLAM_VIRUS");
        return Optional.ofNullable(clamVirusJsonNode).map(JsonNode::toString);
    }

}
