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

package org.apache.james.webadmin.vault.routes.query;

import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName;
import org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class QueryElementDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void beforeEach() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldDeserializeNestedStructure() throws Exception {
        String queryJson = 
            "{  " +
            "  \"combinator\": \"and\",  " +
            "  \"criteria\": [  " +
            "    {  " +
            "      \"combinator\": \"and\",  " +
            "      \"criteria\": [  " +
            "        {\"fieldName\": \"subject\", \"operator\": \"contains\", \"value\": \"" + SUBJECT + "\"}," +
            "        {\"fieldName\": \"sender\", \"operator\": \"equals\", \"value\": \"" + SENDER.asString() + "\"}" +
            "      ]  " +
            "    },  " +
            "    {\"fieldName\": \"hasAttachment\", \"operator\": \"equals\", \"value\": \"true\"}" +
            "  ]  " +
            "}  ";

        QueryDTO queryDTO = objectMapper.readValue(queryJson, QueryDTO.class);
        assertThat(queryDTO)
            .isEqualTo(QueryDTO.and(
                QueryDTO.and(
                    CriterionDTO.from(FieldName.SUBJECT, Operator.CONTAINS, SUBJECT),
                    CriterionDTO.from(FieldName.SENDER, Operator.EQUALS, SENDER.asString())),
                CriterionDTO.from(FieldName.HAS_ATTACHMENT, Operator.EQUALS, "true")
            ));
    }

    @Test
    void shouldDeserializeFlattenStructure() throws Exception {
        String queryJson =
            "{  " +
            "  \"combinator\": \"and\",  " +
            "  \"criteria\": [  " +
            "    {\"fieldName\": \"subject\", \"operator\": \"contains\", \"value\": \"" + SUBJECT + "\"}," +
            "    {\"fieldName\": \"sender\", \"operator\": \"equals\", \"value\": \"" + SENDER.asString() + "\"}," +
            "    {\"fieldName\": \"hasAttachment\", \"operator\": \"equals\", \"value\": \"true\"}" +
            "  ]  " +
            "}  ";

        QueryDTO queryDTO = objectMapper.readValue(queryJson, QueryDTO.class);
        assertThat(queryDTO)
            .isEqualTo(QueryDTO.and(
                CriterionDTO.from(FieldName.SUBJECT, Operator.CONTAINS, SUBJECT),
                CriterionDTO.from(FieldName.SENDER, Operator.EQUALS, SENDER.asString()),
                CriterionDTO.from(FieldName.HAS_ATTACHMENT, Operator.EQUALS, "true")
            ));
    }
}