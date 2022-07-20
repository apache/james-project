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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class AnalysisResultDeserializationTest {
    @Test
    void shouldBeDeserializedWellWhenEmptyDesiredRewriteSubject() throws JsonProcessingException {
        String json = "{\n" +
            "  \"is_skipped\": false,\n" +
            "  \"score\": 5.2,\n" +
            "  \"required_score\": 7,\n" +
            "  \"action\": \"add header\",\n" +
            "  \"symbols\": {\n" +
            "    \"DATE_IN_PAST\": {\n" +
            "      \"name\": \"DATE_IN_PAST\",\n" +
            "      \"score\": 0.1\n" +
            "    }\n" +
            "  },\n" +
            "  \"urls\": [\n" +
            "    \"www.example.com\"\n" +
            "  ],\n" +
            "  \"emails\": [\n" +
            "    \"user@example.com\"\n" +
            "  ],\n" +
            "  " +
            "\"message-id\": \"4E699308EFABE14EB3F18A1BB025456988527794@example\"\n" +
            "}";

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        AnalysisResult analysisResult = objectMapper.readValue(json, AnalysisResult.class);

        assertThat(analysisResult).isEqualTo(AnalysisResult.builder()
            .action(AnalysisResult.Action.ADD_HEADER)
            .score(5.2F)
            .requiredScore(7.0F)
            .build());
    }

    @Test
    void shouldBeDeserializedWellWhenDesiredRewriteSubject() throws JsonProcessingException {
        String json = "{\n" +
            "  \"is_skipped\": false,\n" +
            "  \"score\": 5.2,\n" +
            "  \"required_score\": 7,\n" +
            "  \"action\": \"rewrite subject\",\n" +
            "  \"symbols\": {\n" +
            "    \"DATE_IN_PAST\": {\n" +
            "      \"name\": \"DATE_IN_PAST\",\n" +
            "      \"score\": 0.1\n" +
            "    }\n" +
            "  },\n" +
            "  \"urls\": [\n" +
            "    \"www.example.com\"\n" +
            "  ],\n" +
            "  \"emails\": [\n" +
            "    \"user@example.com\"\n" +
            "  ],\n" +
            "  \"message-id\": \"4E699308EFABE14EB3F18A1BB025456988527794@example\",\n" +
            "  \"subject\": \"A rewritten ham subject" +
            "\"\n" +
            "}";

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        AnalysisResult analysisResult = objectMapper.readValue(json, AnalysisResult.class);

        assertThat(analysisResult).isEqualTo(AnalysisResult.builder()
            .action(AnalysisResult.Action.REWRITE_SUBJECT)
            .score(5.2F)
            .requiredScore(7.0F)
            .desiredRewriteSubject("A rewritten ham subject")
            .build());
    }
}
