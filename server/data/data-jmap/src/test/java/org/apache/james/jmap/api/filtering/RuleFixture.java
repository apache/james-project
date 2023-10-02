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

package org.apache.james.jmap.api.filtering;

import com.google.common.collect.ImmutableList;

public interface RuleFixture {
    String NAME = "a name";
    Rule.Condition CONDITION = Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "something");
    Rule.Action ACTION = Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("id-01"), true, true, true, ImmutableList.of("abc"));
    Rule.Builder RULE_BUILDER = Rule.builder().name(NAME).conditionGroup(CONDITION).action(ACTION);
    Rule RULE_1 = RULE_BUILDER.id(Rule.Id.of("1")).build();
    Rule RULE_1_MODIFIED = Rule.builder()
        .conditionGroup(CONDITION)
        .action(ACTION)
        .id(Rule.Id.of("1"))
        .name("newname")
        .build();
    Rule RULE_2 = RULE_BUILDER.id(Rule.Id.of("2")).build();
    Rule RULE_3 = RULE_BUILDER.id(Rule.Id.of("3")).build();

    Rule RULE_TO = Rule.builder()
        .id(Rule.Id.of("id-to"))
        .name(NAME)
        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("mbx1")))
        .conditionGroup(Rule.Condition.of(
            Rule.Condition.Field.TO,
            Rule.Condition.Comparator.EXACTLY_EQUALS,
            "A value to match 1"))
        .build();

    Rule RULE_SUBJECT = Rule.builder()
        .id(Rule.Id.of("id-subject"))
        .name(NAME)
        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("mbx1")))
        .conditionGroup(Rule.Condition.of(
            Rule.Condition.Field.SUBJECT,
            Rule.Condition.Comparator.NOT_CONTAINS,
            "A value to match 2"))
        .build();

    Rule RULE_RECIPIENT = Rule.builder()
        .id(Rule.Id.of("id-rcpt"))
        .name(NAME)
        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("mbx1")))
        .conditionGroup(Rule.Condition.of(
            Rule.Condition.Field.RECIPIENT,
            Rule.Condition.Comparator.NOT_EXACTLY_EQUALS,
            "A value to match 3"))
        .build();

    Rule RULE_FROM = Rule.builder()
        .id(Rule.Id.of("id-from"))
        .name(NAME)
        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("mbx1")))
        .conditionGroup(Rule.Condition.of(
            Rule.Condition.Field.FROM,
            Rule.Condition.Comparator.CONTAINS,
            "A value to match 4"))
        .build();

}
