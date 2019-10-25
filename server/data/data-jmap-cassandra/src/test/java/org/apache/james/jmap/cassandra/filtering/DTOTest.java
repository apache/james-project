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

package org.apache.james.jmap.cassandra.filtering;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_FROM;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_RECIPIENT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_SUBJECT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_TO;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;
import org.apache.james.util.ClassLoaderUtils;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

public class DTOTest {
    static final String EVENT_JSON = ClassLoaderUtils.getSystemResourceAsString("json/event.json");
    static final String EVENT_EMPTY_JSON = ClassLoaderUtils.getSystemResourceAsString("json/eventEmpty.json");
    static final String EVENT_COMPLEX_JSON = ClassLoaderUtils.getSystemResourceAsString("json/eventComplex.json");
    static final FilteringRuleSetDefinedDTO SIMPLE_DTO = FilteringRuleSetDefinedDTO.from(
            new RuleSetDefined(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of(RULE_1, RULE_2)));
    static final FilteringRuleSetDefinedDTO EMPTY_DTO = FilteringRuleSetDefinedDTO.from(
            new RuleSetDefined(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of()));
    static final FilteringRuleSetDefinedDTO COMPLEX_DTO = FilteringRuleSetDefinedDTO.from(
            new RuleSetDefined(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of(RULE_FROM, RULE_RECIPIENT, RULE_SUBJECT, RULE_TO)));

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new GuavaModule());
        objectMapper.registerSubtypes(
            new NamedType(FilteringRuleSetDefinedDTO.class, "filtering-rule-set-defined"));

    }

    @Test
    void shouldSerializeDTO() throws Exception {
        String serializedEvent = objectMapper.writeValueAsString(SIMPLE_DTO);

        assertThatJson(serializedEvent).isEqualTo(EVENT_JSON);
    }

    @Test
    void shouldDeserializeDTO() throws Exception {
        FilteringRuleSetDefinedDTO dto = objectMapper.readValue(EVENT_JSON, FilteringRuleSetDefinedDTO.class);

        assertThat(dto).isEqualTo(SIMPLE_DTO);
    }

    @Test
    void shouldSerializeEmptyDTO() throws Exception {
        String serializedEvent = objectMapper.writeValueAsString(EMPTY_DTO);

        assertThatJson(serializedEvent).isEqualTo(EVENT_EMPTY_JSON);
    }

    @Test
    void shouldDeserializeEmptyDTO() throws Exception {
        FilteringRuleSetDefinedDTO dto = objectMapper.readValue(EVENT_EMPTY_JSON, FilteringRuleSetDefinedDTO.class);

        assertThat(dto).isEqualTo(EMPTY_DTO);
    }

    @Test
    void shouldSerializeComplexDTO() throws Exception {
        String serializedEvent = objectMapper.writeValueAsString(COMPLEX_DTO);

        assertThatJson(serializedEvent).isEqualTo(EVENT_COMPLEX_JSON);
    }

    @Test
    void shouldDeserializeComplexDTO() throws Exception {
        FilteringRuleSetDefinedDTO dto = objectMapper.readValue(EVENT_COMPLEX_JSON, FilteringRuleSetDefinedDTO.class);

        assertThat(dto).isEqualTo(COMPLEX_DTO);
    }
}
