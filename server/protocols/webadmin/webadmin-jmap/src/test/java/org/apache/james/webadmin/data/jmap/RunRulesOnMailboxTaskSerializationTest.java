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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.NOT_EXACTLY_EQUALS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.FixedField.FROM;
import static org.apache.james.jmap.api.filtering.Rule.Condition.FixedField.SUBJECT;
import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.webadmin.validation.MailboxName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class RunRulesOnMailboxTaskSerializationTest {
    private RunRulesOnMailboxService runRulesOnMailboxService;
    private static final Username USERNAME = Username.of("bob@domain.tld");
    private static final MailboxName MAILBOX_NAME = new MailboxName("mbx1");
    private static final Rule RULE = Rule.builder()
        .id(Rule.Id.of("1"))
        .name("rule 1")
            .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(SUBJECT, CONTAINS, "plop"),
                Rule.Condition.of(FROM, NOT_EXACTLY_EQUALS, "bob@example.com")))
        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of("mbx2"))))
        .build();
    private static final Rules RULES = new Rules(ImmutableList.of(RULE), Version.INITIAL);

    @BeforeEach
    void setUp() {
        runRulesOnMailboxService = mock(RunRulesOnMailboxService.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RunRulesOnMailboxTaskDTO.module(runRulesOnMailboxService))
            .bean(new RunRulesOnMailboxTask(USERNAME, MAILBOX_NAME, RULES, runRulesOnMailboxService))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/runRulesOnMailbox.task.json"))
            .verify();
    }
}
