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

package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.Optional;

import org.apache.james.jmap.api.filtering.Rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class JmapRuleDTO {

    public static class ConditionDTO {

        public static ConditionDTO from(Rule.Condition condition) {
            return new ConditionDTO(
                condition.getField().asString(),
                condition.getComparator().asString(),
                condition.getValue());
        }

        private final String field;
        private final String comparator;
        private final String value;

        @JsonCreator
        public ConditionDTO(@JsonProperty("field") String field,
                            @JsonProperty("comparator") String comparator,
                            @JsonProperty("value") String value) {
            this.field = field;
            this.comparator = comparator;
            this.value = value;
        }

        public String getField() {
            return field;
        }

        public String getComparator() {
            return comparator;
        }

        public String getValue() {
            return value;
        }

        public Rule.Condition toCondition() {
            return Rule.Condition.of(
                    Rule.Condition.Field.of(field),
                    Rule.Condition.Comparator.of(comparator),
                    value);
        }
    }

    public static class ActionDTO {

        public static class AppendInMailboxesDTO {

            public static AppendInMailboxesDTO from(Rule.Action.AppendInMailboxes appendInMailboxes) {
                return new AppendInMailboxesDTO(appendInMailboxes.getMailboxIds());
            }

            @JsonCreator
            public AppendInMailboxesDTO(@JsonProperty("mailboxIds") List<String> mailboxIds) {
                this.mailboxIds = ImmutableList.copyOf(mailboxIds);
            }

            private final List<String> mailboxIds;

            public List<String> getMailboxIds() {
                return mailboxIds;
            }

            public Rule.Action.AppendInMailboxes toAppendInMailboxes() {
                return Rule.Action.AppendInMailboxes.withMailboxIds(mailboxIds);
            }
        }

        public static ActionDTO from(Rule.Action action) {
            return new ActionDTO(AppendInMailboxesDTO.from(action.getAppendInMailboxes()));
        }

        @JsonCreator
        public ActionDTO(@JsonProperty("appendIn") AppendInMailboxesDTO appendIn) {
            this.appendIn = appendIn;
        }

        private final AppendInMailboxesDTO appendIn;

        public AppendInMailboxesDTO getAppendIn() {
            return appendIn;
        }

        public Rule.Action toAction() {
            return Rule.Action.of(appendIn.toAppendInMailboxes());
        }
    }

    public static ImmutableList<Rule> toRules(List<JmapRuleDTO> ruleDTOList) {
        Preconditions.checkNotNull(ruleDTOList);
        return ruleDTOList.stream()
                .map(JmapRuleDTO::toRule)
                .collect(ImmutableList.toImmutableList());
    }

    public static ImmutableList<JmapRuleDTO> from(List<Rule> rules) {
        Preconditions.checkNotNull(rules);
        return rules.stream()
            .map(JmapRuleDTO::from)
            .collect(ImmutableList.toImmutableList());
    }

    public static JmapRuleDTO from(Rule rule) {
        return new JmapRuleDTO(Optional.of(rule.getId().asString()),
                Optional.of(rule.getName()),
                Optional.of(ConditionDTO.from(rule.getCondition())),
                Optional.of(ActionDTO.from(rule.getAction())));
    }

    private final Optional<String> id;
    private final Optional<String> name;
    private final Optional<ConditionDTO> conditionDTO;
    private final Optional<ActionDTO> actionDTO;

    @JsonCreator
    public JmapRuleDTO(@JsonProperty("id") Optional<String> id,
                       @JsonProperty("name") Optional<String> name,
                       @JsonProperty("condition") Optional<ConditionDTO> conditionDTO,
                       @JsonProperty("action") Optional<ActionDTO> actionDTO) {
        this.name = name;
        this.conditionDTO = conditionDTO;
        this.actionDTO = actionDTO;
        this.id = id;
    }

    public Optional<String> getId() {
        return id;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<ConditionDTO> getCondition() {
        return conditionDTO;
    }

    public Optional<ActionDTO> getAction() {
        return actionDTO;
    }

    public Rule toRule() {
        Preconditions.checkState(conditionDTO.isPresent(), "`condition` is mandatory");
        Preconditions.checkState(actionDTO.isPresent(), "`action` is mandatory");
        Preconditions.checkState(name.isPresent(), "`name` is mandatory");
        Preconditions.checkState(!name.get().isEmpty(), "`name` is mandatory");
        Preconditions.checkState(id.isPresent(), "`id` is mandatory");
        Preconditions.checkState(!id.get().isEmpty(), "`id` is mandatory");

        return Rule.builder()
            .id(Rule.Id.of(id.get()))
            .name(name.get())
            .condition(conditionDTO.get().toCondition())
            .action(actionDTO.get().toAction())
            .build();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
