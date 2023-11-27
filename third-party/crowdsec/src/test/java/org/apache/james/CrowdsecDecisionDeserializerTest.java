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

package org.apache.james;

import java.time.Duration;
import java.util.List;

import org.apache.james.model.CrowdsecDecision;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class CrowdsecDecisionDeserializerTest {
    @Test
    void deserializeCrowdsecDecisionTest() throws Exception {
        String json = "[\n{\n  \"duration\": \"3h47m14.654059061s\",\n  \"id\": 3,\n  \"origin\": \"cscli\",\n  \"scenario\": \"manual 'ban' from 'localhost'\",\n  \"scope\": \"Ip\",\n  \"type\": \"ban\",\n  \"value\": \"1.1.2.54\"\n}\n]";
        ObjectMapper objectMapper = (new ObjectMapper()).registerModule(new Jdk8Module());
        List<CrowdsecDecision> decisions = objectMapper.readValue(json, new TypeReference<>() {});

        Assertions.assertThat(decisions.get(0))
            .isEqualTo(CrowdsecDecision.builder()
                .duration(Duration.parse("PT3h47m14.654059061s"))
                .id(3L)
                .origin("cscli")
                .scenario("manual 'ban' from 'localhost'")
                .scope("Ip")
                .value("1.1.2.54")
                .type("ban")
                .build());
    }
}