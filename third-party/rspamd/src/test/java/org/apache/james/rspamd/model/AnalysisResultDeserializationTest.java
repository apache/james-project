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
            .hasVirus(false)
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
            .hasVirus(false)
            .build());
    }

    @Test
    void shouldBeDeserializedWellWhenHasClamVirusSymbol() throws JsonProcessingException {
        String json = "{\n" +
            "  \"is_skipped\": false,\n" +
            "  \"score\": 3.500000,\n" +
            "  \"required_score\": 14.0,\n" +
            "  \"action\": \"no action\",\n" +
            "  \"symbols\": {\n" +
            "    \"ARC_NA\": {\n" +
            "      \"name\": \"ARC_NA\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"ARC signature absent\"\n" +
            "    },\n" +
            "    \"FROM_HAS_DN\": {\n" +
            "      \"name\": \"FROM_HAS_DN\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"From header has a display name\"\n" +
            "    },\n" +
            "    \"MIME_GOOD\": {\n" +
            "      \"name\": \"MIME_GOOD\",\n" +
            "      \"score\": -0.100000,\n" +
            "      \"metric_score\": -0.100000,\n" +
            "      \"description\": \"Known content-type\",\n" +
            "      \"options\": [\n" +
            "        \"multipart/mixed\",\n" +
            "        \"text/plain\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"TO_DN_NONE\": {\n" +
            "      \"name\": \"TO_DN_NONE\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"None of the recipients have display names\"\n" +
            "    },\n" +
            "    \"RCPT_COUNT_ONE\": {\n" +
            "      \"name\": \"RCPT_COUNT_ONE\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"One recipient\",\n" +
            "      \"options\": [\n" +
            "        \"1\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"CLAM_VIRUS\": {\n" +
            "      \"name\": \"CLAM_VIRUS\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"options\": [\n" +
            "        \"Eicar-Signature\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"RCVD_COUNT_ZERO\": {\n" +
            "      \"name\": \"RCVD_COUNT_ZERO\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"Message has no Received headers\",\n" +
            "      \"options\": [\n" +
            "        \"0\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"TO_EQ_FROM\": {\n" +
            "      \"name\": \"TO_EQ_FROM\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"To address matches the From address\"\n" +
            "    },\n" +
            "    \"R_DKIM_NA\": {\n" +
            "      \"name\": \"R_DKIM_NA\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"description\": \"Missing DKIM signature\"\n" +
            "    },\n" +
            "    \"DATE_IN_PAST\": {\n" +
            "      \"name\": \"DATE_IN_PAST\",\n" +
            "      \"score\": 1.0,\n" +
            "      \"metric_score\": 1.0,\n" +
            "      \"description\": \"Message date is in past\",\n" +
            "      \"options\": [\n" +
            "        \"51163\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"MIME_TRACE\": {\n" +
            "      \"name\": \"MIME_TRACE\",\n" +
            "      \"score\": 0.0,\n" +
            "      \"metric_score\": 0.0,\n" +
            "      \"options\": [\n" +
            "        \"0:+\",\n" +
            "        \"1:+\",\n" +
            "        \"2:+\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"HFILTER_HOSTNAME_UNKNOWN\": {\n" +
            "      \"name\": \"HFILTER_HOSTNAME_UNKNOWN\",\n" +
            "      \"score\": 2.500000,\n" +
            "      \"metric_score\": 2.500000,\n" +
            "      \"description\": \"Unknown client hostname (PTR or FCrDNS verification failed)\"\n" +
            "    },\n" +
            "    \"DMARC_POLICY_SOFTFAIL\": {\n" +
            "      \"name\": \"DMARC_POLICY_SOFTFAIL\",\n" +
            "      \"score\": 0.100000,\n" +
            "      \"metric_score\": 0.100000,\n" +
            "      \"description\": \"DMARC failed\",\n" +
            "      \"options\": [\n" +
            "        \"linagora.com : No valid SPF, No valid DKIM\",\n" +
            "        \"none\"\n" +
            "      ]\n" +
            "    }\n" +
            "  },\n" +
            "  \"messages\": {\n" +
            "\n" +
            "  },\n" +
            "  \"message-id\": \"befd8cab-9c9c-5537-4e77-937f32326087@any.com\",\n" +
            "  \"time_real\": 0.381197,\n" +
            "  \"milter\": {\n" +
            "    \"remove_headers\": {\n" +
            "      \"X-Spam\": 0\n" +
            "    }\n" +
            "  }\n" +
            "}";

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        AnalysisResult analysisResult = objectMapper.readValue(json, AnalysisResult.class);

        assertThat(analysisResult).isEqualTo(AnalysisResult.builder()
            .action(AnalysisResult.Action.NO_ACTION)
            .score(3.5F)
            .requiredScore(14.0F)
            .virusNote("{\"name\":\"CLAM_VIRUS\",\"score\":0.0,\"metric_score\":0.0,\"options\":[\"Eicar-Signature\"]}")
            .build());
    }
}
