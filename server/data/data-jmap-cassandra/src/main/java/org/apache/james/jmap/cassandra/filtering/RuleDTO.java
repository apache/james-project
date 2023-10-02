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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.api.filtering.Rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class RuleDTO {

    public static class ConditionGroupDTO {

        private final Rule.ConditionCombiner conditionCombiner;
        private final List<ConditionDTO> conditionDTOs;

        @JsonCreator
        public ConditionGroupDTO(@JsonProperty("conditionCombiner") Rule.ConditionCombiner conditionCombiner,
                                 @JsonProperty("conditions") List<ConditionDTO> conditionDTOs) {
            this.conditionCombiner = conditionCombiner;
            this.conditionDTOs = conditionDTOs;
        }

        public Rule.ConditionCombiner getConditionCombiner() {
            return conditionCombiner;
        }

        public List<ConditionDTO> getConditions() {
            return conditionDTOs;
        }

        public Rule.ConditionGroup toConditionGroup() {
            return Rule.ConditionGroup.of(conditionCombiner, conditionDTOs.stream().map(ConditionDTO::toCondition).collect(ImmutableList.toImmutableList()));
        }

        public static ConditionGroupDTO from(Rule.ConditionGroup conditionGroup) {
            return new ConditionGroupDTO(conditionGroup.getConditionCombiner(), conditionGroup.getConditions().stream().map(ConditionDTO::from).collect(ImmutableList.toImmutableList()));
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ConditionGroupDTO) {
                ConditionGroupDTO other = (ConditionGroupDTO) o;
                return Objects.equals(conditionCombiner, other.conditionCombiner)
                    && Objects.equals(conditionDTOs, other.conditionDTOs);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(conditionCombiner, conditionDTOs);
        }
    }

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

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ConditionDTO) {
                ConditionDTO other = (ConditionDTO) o;

                return Objects.equals(this.field, other.field)
                        && Objects.equals(this.comparator, other.comparator)
                        && Objects.equals(this.value, other.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(field, comparator, value);
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

            @Override
            public final boolean equals(Object o) {
                if (o instanceof AppendInMailboxesDTO) {
                    AppendInMailboxesDTO that = (AppendInMailboxesDTO) o;

                    return Objects.equals(this.mailboxIds, that.mailboxIds);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(mailboxIds);
            }
        }

        public static ActionDTO from(Rule.Action action) {
            return new ActionDTO(AppendInMailboxesDTO.from(action.getAppendInMailboxes()),
                action.isMarkAsSeen(), action.isMarkAsImportant(), action.isReject(), ImmutableList.copyOf(action.getWithKeywords()));
        }

        @JsonCreator
        public ActionDTO(@JsonProperty("appendIn") AppendInMailboxesDTO appendIn,
                         @JsonProperty("seen") boolean seen,
                         @JsonProperty("important") boolean important,
                         @JsonProperty("reject") boolean reject,
                         @JsonProperty("keywords") List<String> keyworkds) {
            this.appendIn = appendIn;
            this.keyworkds = keyworkds;
            this.seen = seen;
            this.important = important;
            this.reject = reject;
        }

        private final AppendInMailboxesDTO appendIn;
        private final boolean seen;
        private final boolean important;
        private final boolean reject;
        private final List<String> keyworkds;

        public AppendInMailboxesDTO getAppendIn() {
            return appendIn;
        }

        public boolean isSeen() {
            return seen;
        }

        public boolean isImportant() {
            return important;
        }

        public boolean isReject() {
            return reject;
        }

        public List<String> getKeyworkds() {
            return keyworkds;
        }

        public Rule.Action toAction() {
            return Rule.Action.of(appendIn.toAppendInMailboxes(), isSeen(), isImportant(), isReject(), getKeyworkds());
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ActionDTO) {
                ActionDTO actionDTO = (ActionDTO) o;

                return Objects.equals(this.appendIn, actionDTO.appendIn)
                    && Objects.equals(this.seen, actionDTO.seen)
                    && Objects.equals(this.important, actionDTO.important)
                    && Objects.equals(this.reject, actionDTO.reject)
                    && Objects.equals(this.keyworkds, actionDTO.keyworkds);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(appendIn, reject, seen, important, keyworkds);
        }
    }

    public static ImmutableList<Rule> toRules(List<RuleDTO> ruleDTOList) {
        Preconditions.checkNotNull(ruleDTOList);
        return ruleDTOList.stream()
                .map(RuleDTO::toRule)
                .collect(ImmutableList.toImmutableList());
    }

    public static ImmutableList<RuleDTO> from(List<Rule> rules) {
        Preconditions.checkNotNull(rules);
        return rules.stream()
            .map(RuleDTO::from)
            .collect(ImmutableList.toImmutableList());
    }

    public static RuleDTO from(Rule rule) {
        return new RuleDTO(rule.getId().asString(),
                rule.getName(),
                ConditionGroupDTO.from(rule.getConditionGroup()),
                ActionDTO.from(rule.getAction()));
    }

    private final String id;
    private final String name;
    private final ConditionGroupDTO conditionGroupDTO;
    private final ActionDTO actionDTO;

    public RuleDTO(String id,
                   String name,
                   ConditionGroupDTO conditionGroupDTO,
                   ActionDTO actionDTO) {
        Preconditions.checkNotNull(id);

        this.name = name;
        this.conditionGroupDTO = conditionGroupDTO;
        this.actionDTO = actionDTO;

        this.id = id;
    }

    @JsonCreator
    public RuleDTO(@JsonProperty("id") String id,
                   @JsonProperty("name") String name,
                   @JsonProperty("condition") Optional<ConditionDTO> conditionDTO,
                   @JsonProperty("conditionGroup") Optional<ConditionGroupDTO> conditionGroupDTO,
                   @JsonProperty("action") ActionDTO actionDTO) {
        Preconditions.checkNotNull(id);

        this.name = name;
        if (conditionGroupDTO.isPresent()) {
            this.conditionGroupDTO = conditionGroupDTO.orElseThrow();
        } else {
            this.conditionGroupDTO = new ConditionGroupDTO(Rule.ConditionCombiner.AND,
                ImmutableList.of(conditionDTO.orElseThrow(() -> new RuntimeException("Condition field in the rule with id " + id + " is missing"))));
        }
        this.actionDTO = actionDTO;

        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ConditionGroupDTO getConditionGroup() {
        return conditionGroupDTO;
    }

    public ActionDTO getAction() {
        return actionDTO;
    }

    public Rule toRule() {
        return Rule.builder()
            .id(Rule.Id.of(id))
            .name(name)
            .conditionGroup(conditionGroupDTO.toConditionGroup())
            .action(actionDTO.toAction())
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RuleDTO) {
            RuleDTO ruleDTO = (RuleDTO) o;

            return Objects.equals(this.id, ruleDTO.id)
                   && Objects.equals(this.name, ruleDTO.name)
                   && Objects.equals(this.conditionGroupDTO, ruleDTO.conditionGroupDTO)
                   && Objects.equals(this.actionDTO, ruleDTO.actionDTO);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, name, conditionGroupDTO, actionDTO);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
