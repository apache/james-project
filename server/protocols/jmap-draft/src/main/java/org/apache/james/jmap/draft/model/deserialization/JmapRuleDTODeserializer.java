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

package org.apache.james.jmap.draft.model.deserialization;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.jmap.draft.model.JmapRuleDTO;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

public class JmapRuleDTODeserializer extends JsonDeserializer<JmapRuleDTO> {

    @Override
    public JmapRuleDTO deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jp.readValueAsTree();

        JsonNode idNode = node.get("id");
        Preconditions.checkArgument(!idNode.isNull(), "`id` is mandatory");
        Preconditions.checkArgument(StringUtils.isNotBlank(idNode.asText()), "`id` is mandatory");

        JsonNode nameNode = node.get("name");
        Preconditions.checkArgument(!nameNode.isNull(), "`name` is mandatory");
        Preconditions.checkArgument(StringUtils.isNotBlank(nameNode.asText()), "`name` is mandatory");

        JsonNode conditionNode = node.get("condition");
        Preconditions.checkArgument(!conditionNode.isNull(), "`condition` is mandatory");
        JmapRuleDTO.ConditionDTO conditionDTO = jp.getCodec().treeToValue(conditionNode, JmapRuleDTO.ConditionDTO.class);

        JsonNode actionNode = node.get("action");
        Preconditions.checkArgument(!actionNode.isNull(), "`action` is mandatory");
        JmapRuleDTO.ActionDTO actionDTO = jp.getCodec().treeToValue(actionNode, JmapRuleDTO.ActionDTO.class);

        return new JmapRuleDTO(node.get("id").asText(), node.get("name").asText(), conditionDTO, actionDTO);
    }
}
