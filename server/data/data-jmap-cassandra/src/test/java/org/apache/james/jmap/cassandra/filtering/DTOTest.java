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

import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_FROM;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_RECIPIENT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_SUBJECT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_TO;
import static org.apache.james.jmap.cassandra.filtering.FilteringRuleSetDefineDTOModules.FILTERING_RULE_SET_DEFINED;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class DTOTest {
    static final String EVENT_JSON = ClassLoaderUtils.getSystemResourceAsString("json/event.json");
    static final String EVENT_EMPTY_JSON = ClassLoaderUtils.getSystemResourceAsString("json/eventEmpty.json");
    static final String EVENT_COMPLEX_JSON = ClassLoaderUtils.getSystemResourceAsString("json/eventComplex.json");
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

    @Test
    void shouldSerializeRule() throws Exception {
        JsonSerializationVerifier.dtoModule(FILTERING_RULE_SET_DEFINED)
            .bean(EMPTY_RULE).json(EVENT_EMPTY_JSON)
            .bean(SIMPLE_RULE).json(EVENT_JSON)
            .bean(COMPLEX_RULE).json(EVENT_COMPLEX_JSON)
            .verify();
    }
}
