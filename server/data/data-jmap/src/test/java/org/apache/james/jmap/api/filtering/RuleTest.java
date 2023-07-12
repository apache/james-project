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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RuleTest {

    private static final List<String> ACTION_MAILBOXIDS = Arrays.asList("id-01");
    private static final String CONDITION_COMPARATOR = "contains";
    private static final String CONDITION_FIELD = "cc";
    private static final String NAME = "a name";
    private static final Rule.Condition CONDITION = Rule.Condition.of(Rule.Condition.Field.of(CONDITION_FIELD), Rule.Condition.Comparator.of(CONDITION_COMPARATOR), "something");
    private static final Rule.Action ACTION = Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ACTION_MAILBOXIDS));
    private static final Rule.Id UNIQUE_ID = Rule.Id.of("uniqueId");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Rule.class)
            .verify();
    }

    @Test
    void innerClassConditionShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rule.Condition.class)
            .verify();
    }

    @Test
    void innerClassActionShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rule.Action.class)
            .verify();
    }

    @Test
    void innerClassIdShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rule.Id.class)
            .verify();
    }

    @Test
    void innerClassAppendInMailboxesShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rule.Action.AppendInMailboxes.class)
            .verify();
    }

    @Test
    void idShouldThrowOnNull() {
        assertThatThrownBy(() -> Rule.Id.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idShouldThrowOnEmpty() {
        assertThatThrownBy(() -> Rule.Id.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idShouldThrowOnBlank() {
        assertThatThrownBy(() -> Rule.Id.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idShouldBeMandatory() {
        assertThatThrownBy(() ->
            Rule.builder()
                .name(NAME)
                .conditions(Arrays.asList(CONDITION))
                .action(ACTION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nameShouldBeMandatory() {
        assertThatThrownBy(() ->
            Rule.builder()
                .id(UNIQUE_ID)
                .conditions(Arrays.asList(CONDITION))
                .action(ACTION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenNameEmpty() {
        assertThatThrownBy(() ->
            Rule.builder()
                .id(UNIQUE_ID)
                .name("")
                .conditions(Arrays.asList(CONDITION))
                .action(ACTION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenNameBlank() {
        assertThatThrownBy(() ->
            Rule.builder()
                .id(UNIQUE_ID)
                .name("    ")
                .conditions(Arrays.asList(CONDITION))
                .action(ACTION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenNameNull() {
        assertThatThrownBy(() ->
            Rule.builder()
                .id(UNIQUE_ID)
                .name(null)
                .conditions(Arrays.asList(CONDITION))
                .action(ACTION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conditionShouldBeMandatory() {
        assertThatThrownBy(() ->
            Rule.builder()
                .id(UNIQUE_ID)
                .name(NAME)
                .action(ACTION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actionShouldBeMandatory() {
        assertThatThrownBy(() ->
            Rule.builder()
                .id(UNIQUE_ID)
                .name(NAME)
                .conditions(Arrays.asList(CONDITION))
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldPreserveCondition() {
        Rule rule = Rule.builder()
            .id(UNIQUE_ID)
            .name(NAME)
            .conditions(Arrays.asList(CONDITION))
            .action(ACTION)
            .build();

        assertThat(rule.getConditions().get(0)).isEqualTo(CONDITION);
    }

    @Test
    void builderShouldPreserveAction() {
        Rule rule = Rule.builder()
            .id(UNIQUE_ID)
            .name(NAME)
            .conditions(Arrays.asList(CONDITION))
            .action(ACTION)
            .build();

        assertThat(rule.getAction()).isEqualTo(ACTION);
    }

    @Test
    void buildConditionShouldConserveField() {
        assertThat(CONDITION.getField().asString()).isEqualTo(CONDITION_FIELD);
    }

    @Test
    void buildConditionShouldConserveComparator() {
        assertThat(CONDITION.getComparator().asString()).isEqualTo(CONDITION_COMPARATOR);
    }

    @Test
    void buildActionShouldConserveMailboxIdsList() {
        assertThat(ACTION.getAppendInMailboxes().getMailboxIds()).isEqualTo(ACTION_MAILBOXIDS);
    }

}
