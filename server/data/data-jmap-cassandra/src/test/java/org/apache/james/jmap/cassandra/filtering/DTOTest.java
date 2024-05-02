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

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.apache.james.jmap.api.filtering.FilteringRuleSetDefineDTOModules.FILTERING_INCREMENT;
import static org.apache.james.jmap.api.filtering.FilteringRuleSetDefineDTOModules.FILTERING_RULE_SET_DEFINED;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_4;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_FROM;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_FROM_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_RECIPIENT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_RECIPIENT_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_SUBJECT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_SUBJECT_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_TO;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_TO_2;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.FilteringIncrementalRuleChangeDTO;
import org.apache.james.jmap.api.filtering.FilteringRuleSetDefinedDTO;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.IncrementalRuleChange;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class DTOTest {
    static final String EVENT_JSON = ClassLoaderUtils.getSystemResourceAsString("json/event.json");
    static final String EVENT_JSON_2 = ClassLoaderUtils.getSystemResourceAsString("json/event-v2.json");
    static final String EVENT_JSON_3 = ClassLoaderUtils.getSystemResourceAsString("json/event-v3.json");
    static final String EVENT_JSON_4 = ClassLoaderUtils.getSystemResourceAsString("json/event-v4.json");
    static final String EVENT_EMPTY_JSON = ClassLoaderUtils.getSystemResourceAsString("json/eventEmpty.json");
    static final String EVENT_COMPLEX_JSON = ClassLoaderUtils.getSystemResourceAsString("json/eventComplex.json");
    static final String EVENT_COMPLEX_JSON_2 = ClassLoaderUtils.getSystemResourceAsString("json/eventComplex-v2.json");
    static final String EVENT_COMPLEX_JSON_3 = ClassLoaderUtils.getSystemResourceAsString("json/eventComplex-v3.json");
    static final String EVENT_COMPLEX_JSON_4 = ClassLoaderUtils.getSystemResourceAsString("json/eventComplex-v4.json");

    static final RuleSetDefined SIMPLE_RULE = new RuleSetDefined(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of(RULE_1, RULE_2));
    static final RuleSetDefined EMPTY_RULE = new RuleSetDefined(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of());
    static final RuleSetDefined COMPLEX_RULE =  new RuleSetDefined(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of(RULE_FROM, RULE_RECIPIENT, RULE_SUBJECT, RULE_TO));
    static final IncrementalRuleChange INCREMENT =  new IncrementalRuleChange(
                    new FilteringAggregateId(Username.of("Bart")),
                    EventId.first(),
                    ImmutableList.of(RULE_FROM, RULE_TO),
                    ImmutableList.of(RULE_RECIPIENT),
                    ImmutableSet.of(Rule.Id.of("abdcd")),
                    ImmutableList.of(RULE_SUBJECT));

    static final RuleSetDefined SIMPLE_RULE_2 = new RuleSetDefined(
        new FilteringAggregateId(Username.of("Bart")),
        EventId.first(),
        ImmutableList.of(RULE_4));
    static final RuleSetDefined COMPLEX_RULE_2 =  new RuleSetDefined(
        new FilteringAggregateId(Username.of("Bart")),
        EventId.first(),
        ImmutableList.of(RULE_FROM_2, RULE_RECIPIENT_2, RULE_SUBJECT_2, RULE_TO_2));
    static final IncrementalRuleChange INCREMENT_2 =  new IncrementalRuleChange(
        new FilteringAggregateId(Username.of("Bart")),
        EventId.first(),
        ImmutableList.of(RULE_FROM_2, RULE_TO_2),
        ImmutableList.of(RULE_RECIPIENT_2),
        ImmutableSet.of(Rule.Id.of("abdcd")),
        ImmutableList.of(RULE_SUBJECT_2));

    @Test
    void shouldSerializeRule() throws Exception {
        JsonSerializationVerifier.dtoModule(FILTERING_RULE_SET_DEFINED)
            .testCase(EMPTY_RULE, EVENT_EMPTY_JSON)
            .testCase(SIMPLE_RULE_2, EVENT_JSON_4)
            .testCase(COMPLEX_RULE_2, EVENT_COMPLEX_JSON_4)
            .verify();
    }

    @Test
    void shouldDeserializeV3() {
        JsonGenericSerializer<RuleSetDefined, FilteringRuleSetDefinedDTO> serializer = JsonGenericSerializer
            .forModules(FILTERING_RULE_SET_DEFINED)
            .withoutNestedType();

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(serializer.deserialize(EVENT_JSON_3)).usingRecursiveComparison(recursiveComparisonConfiguration)
                .isEqualTo(SIMPLE_RULE);
            softly.assertThat(serializer.deserialize(EVENT_COMPLEX_JSON_3)).usingRecursiveComparison(recursiveComparisonConfiguration)
                .isEqualTo(COMPLEX_RULE);
        }));
    }

    @Test
    void shouldDeserializeV2() {
        JsonGenericSerializer<RuleSetDefined, FilteringRuleSetDefinedDTO> serializer = JsonGenericSerializer
            .forModules(FILTERING_RULE_SET_DEFINED)
            .withoutNestedType();

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(serializer.deserialize(EVENT_JSON_2)).usingRecursiveComparison(recursiveComparisonConfiguration)
                .isEqualTo(SIMPLE_RULE);
            softly.assertThat(serializer.deserialize(EVENT_COMPLEX_JSON_2)).usingRecursiveComparison(recursiveComparisonConfiguration)
                .isEqualTo(COMPLEX_RULE);
        }));
    }

    @Test
    void shouldDeserializeV1() {
        JsonGenericSerializer<RuleSetDefined, FilteringRuleSetDefinedDTO> serializer = JsonGenericSerializer
            .forModules(FILTERING_RULE_SET_DEFINED)
            .withoutNestedType();

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(serializer.deserialize(EVENT_JSON)).usingRecursiveComparison(recursiveComparisonConfiguration)
                .isEqualTo(SIMPLE_RULE);
            softly.assertThat(serializer.deserialize(EVENT_COMPLEX_JSON)).usingRecursiveComparison(recursiveComparisonConfiguration)
                .isEqualTo(COMPLEX_RULE);
        }));
    }

    @Test
    void shouldSerializeIncrements() throws Exception {
        JsonSerializationVerifier.dtoModule(FILTERING_INCREMENT)
            .testCase(INCREMENT_2, ClassLoaderUtils.getSystemResourceAsString("json/increment-v4.json"))
            .verify();
    }

    @Test
    void shouldDeserializeV3ForIncrements() throws Exception {
        JsonGenericSerializer<IncrementalRuleChange, FilteringIncrementalRuleChangeDTO> serializer = JsonGenericSerializer
            .forModules(FILTERING_INCREMENT)
            .withoutNestedType();

        assertThat(serializer.deserialize(ClassLoaderUtils.getSystemResourceAsString("json/increment-v3.json")))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(INCREMENT);
    }

    @Test
    void shouldDeserializeV2ForIncrements() throws Exception {
        JsonGenericSerializer<IncrementalRuleChange, FilteringIncrementalRuleChangeDTO> serializer = JsonGenericSerializer
            .forModules(FILTERING_INCREMENT)
            .withoutNestedType();

        assertThat(serializer.deserialize(ClassLoaderUtils.getSystemResourceAsString("json/increment-v2.json")))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(INCREMENT);
    }

    @Test
    void shouldDeserializeV1ForIncrements() throws Exception {
        JsonGenericSerializer<IncrementalRuleChange, FilteringIncrementalRuleChangeDTO> serializer = JsonGenericSerializer
            .forModules(FILTERING_INCREMENT)
            .withoutNestedType();

        assertThat(serializer.deserialize(ClassLoaderUtils.getSystemResourceAsString("json/increment.json")))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(INCREMENT);
    }
}
