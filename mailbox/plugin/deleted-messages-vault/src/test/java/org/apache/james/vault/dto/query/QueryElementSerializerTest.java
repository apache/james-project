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

package org.apache.james.vault.dto.query;

import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.mailet.base.MailAddressFixture.SENDER;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.james.vault.search.FieldName;
import org.apache.james.vault.search.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;

class QueryElementSerializerTest {

    private QueryElementSerializer queryElementSerializer;

    @BeforeEach
    void beforeEach() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        queryElementSerializer = new QueryElementSerializer(objectMapper);
    }

    @Test
    void shouldSerializeNestedStructure() throws Exception {

        QueryDTO queryDTO = QueryDTO.and(
            QueryDTO.and(
                CriterionDTO.from(FieldName.SUBJECT, Operator.CONTAINS, SUBJECT),
                CriterionDTO.from(FieldName.SENDER, Operator.EQUALS, SENDER.asString())),
            CriterionDTO.from(FieldName.HAS_ATTACHMENT, Operator.EQUALS, "true")
        );

        JsonAssertions.assertThatJson(queryElementSerializer.serialize(queryDTO))
            .isEqualTo("{  " +
                "  \"combinator\": \"and\",  " +
                "  \"limit\": null,  " +
                "  \"criteria\": [  " +
                "    {  " +
                "      \"combinator\": \"and\",  " +
                "      \"limit\": null,  " +
                "      \"criteria\": [  " +
                "        {\"fieldName\": \"subject\", \"operator\": \"contains\", \"value\": \"" + SUBJECT + "\"}," +
                "        {\"fieldName\": \"sender\", \"operator\": \"equals\", \"value\": \"" + SENDER.asString() + "\"}" +
                "      ]  " +
                "    },  " +
                "    {\"fieldName\": \"hasAttachment\", \"operator\": \"equals\", \"value\": \"true\"}" +
                "  ]  " +
                "}  ");
    }

    @Test
    void shouldSerializeFlattenStructure() throws Exception {

        QueryDTO queryDTO = QueryDTO.and(1L,
            CriterionDTO.from(FieldName.SUBJECT, Operator.CONTAINS, SUBJECT),
            CriterionDTO.from(FieldName.SENDER, Operator.EQUALS, SENDER.asString()),
            CriterionDTO.from(FieldName.HAS_ATTACHMENT, Operator.EQUALS, "true")
        );

        JsonAssertions.assertThatJson(queryElementSerializer.serialize(queryDTO))
            .isEqualTo("{  " +
                "  \"combinator\": \"and\",  " +
                "  \"limit\": 1,  " +
                "  \"criteria\": [  " +
                "    {\"fieldName\": \"subject\", \"operator\": \"contains\", \"value\": \"" + SUBJECT + "\"}," +
                "    {\"fieldName\": \"sender\", \"operator\": \"equals\", \"value\": \"" + SENDER.asString() + "\"}," +
                "    {\"fieldName\": \"hasAttachment\", \"operator\": \"equals\", \"value\": \"true\"}" +
                "  ]  " +
                "}  "
            );
    }
}