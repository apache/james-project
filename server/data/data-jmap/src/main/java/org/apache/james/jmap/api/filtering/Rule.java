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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class Rule {

    public static class Id {

        public static Id of(String id) {
            Preconditions.checkArgument(StringUtils.isNotBlank(id), "`id` is mandatory");
            return new Id(id);
        }

        private final String value;

        private Id(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Id) {
                Id id = (Id) o;
                return Objects.equals(value, id.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }

    public static class ConditionGroup {

        public static ConditionGroup of(ConditionCombiner conditionCombiner, List<Condition> conditions) {
            return new ConditionGroup(conditionCombiner, conditions);
        }

        public static ConditionGroup of(ConditionCombiner conditionCombiner, Condition... conditions) {
            return new ConditionGroup(conditionCombiner, ImmutableList.copyOf(conditions));
        }

        public static ConditionGroup of(Condition condition) {
            return ConditionGroup.of(ConditionCombiner.AND, condition);
        }

        private final ConditionCombiner conditionCombiner;
        private final List<Condition> conditions;

        private ConditionGroup(ConditionCombiner conditionCombiner, List<Condition> conditions) {
            this.conditionCombiner = conditionCombiner;
            this.conditions = conditions;
        }

        public ConditionCombiner getConditionCombiner() {
            return conditionCombiner;
        }

        public List<Condition> getConditions() {
            return conditions;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ConditionGroup) {
                ConditionGroup other = (ConditionGroup) o;
                return Objects.equals(conditionCombiner, other.conditionCombiner)
                    && Objects.equals(conditions, other.conditions);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(conditionCombiner, conditions);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("conditionCombiner", conditionCombiner)
                .add("conditions", conditions)
                .toString();
        }
    }

    public static class Condition {

        public enum Field {
            FROM("from"),
            TO("to"),
            CC("cc"),
            SUBJECT("subject"),
            RECIPIENT("recipient");
            
            public static Optional<Field> find(String fieldName) {
                return Arrays.stream(values())
                        .filter(value -> value.fieldName.equalsIgnoreCase(fieldName))
                        .findAny();
            }
            
            public static Field of(String fieldName) {
                return find(fieldName).orElseThrow(() -> new IllegalArgumentException("'" + fieldName + "' is not a valid field name"));
            }
            
            private final String fieldName;
            
            Field(String fieldName) {
                this.fieldName = fieldName;
            }
            
            public String asString() {
                return fieldName;
            }
        }
        
        public enum Comparator {
            CONTAINS("contains"),
            NOT_CONTAINS("not-contains"),
            EXACTLY_EQUALS("exactly-equals"),
            NOT_EXACTLY_EQUALS("not-exactly-equals");
            
            public static Optional<Comparator> find(String comparatorName) {
                return Arrays.stream(values())
                        .filter(value -> value.comparatorName.equalsIgnoreCase(comparatorName))
                        .findAny();
            }
            
            public static Comparator of(String comparatorName) {
                return find(comparatorName).orElseThrow(() -> new IllegalArgumentException("'" + comparatorName + "' is not a valid comparator name"));
            }
            
            private final String comparatorName;
            
            Comparator(String comparator) {
                this.comparatorName = comparator;
            }
            
            public String asString() {
                return comparatorName;
            }
        }

        public static Condition of(Field field, Comparator comparator, String value) {
            Preconditions.checkNotNull(field, "field should not be null");
            Preconditions.checkNotNull(comparator, "comparator should not be null");
            Preconditions.checkNotNull(value, "value should not be null");
            Preconditions.checkArgument(StringUtils.isNotBlank(value), "value should not be empty");
            return new Condition(field, comparator, value);
        }

        private final Field field;
        private final Comparator comparator;
        private final String value;

        private Condition(Field field, Comparator comparator, String value) {
            this.field = field;
            this.comparator = comparator;
            this.value = value;
        }
        
        public Field getField() {
            return field;
        }
        
        public Comparator getComparator() {
            return comparator;
        }
        
        public String getValue() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Condition) {
                Condition condition = (Condition) o;
                return Objects.equals(field, condition.field)
                    && Objects.equals(comparator, condition.comparator)
                    && Objects.equals(value, condition.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(field, comparator, value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("field", field)
                .add("comparator", comparator)
                .add("value", value)
                .toString();
        }
    }

    public static class Action {

        public static class AppendInMailboxes {

            public static AppendInMailboxes withMailboxIds(List<String> mailboxIds) {
                Preconditions.checkNotNull(mailboxIds, "mailboxIds should not be null");
                return new AppendInMailboxes(mailboxIds);
            }

            public static AppendInMailboxes withMailboxIds(String... mailboxIds) {
                return withMailboxIds(Arrays.asList(mailboxIds));
            }

            private final ImmutableList<String> mailboxIds;

            private AppendInMailboxes(List<String> mailboxIds) {
                this.mailboxIds = ImmutableList.copyOf(mailboxIds);
            }

            public ImmutableList<String> getMailboxIds() {
                return mailboxIds;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof AppendInMailboxes) {
                    AppendInMailboxes appendInMailboxes = (AppendInMailboxes) o;
                    return Objects.equals(mailboxIds, appendInMailboxes.mailboxIds);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(mailboxIds);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                        .add("mailboxIds", mailboxIds)
                        .toString();
            }
        }

        public static class Forward {
            @FunctionalInterface
            public interface RequireLocalCopy {
                Forward keepACopy(boolean value);

                default Forward keepACopy() {
                    return keepACopy(true);
                }

                default Forward withoutACopy() {
                    return keepACopy(false);
                }
            }

            public static RequireLocalCopy to(Collection<MailAddress> addresses) {
                return keepACopy -> new Forward(ImmutableList.copyOf(addresses), keepACopy);
            }

            public static RequireLocalCopy to(MailAddress... addresses) {
                return keepACopy -> new Forward(ImmutableList.copyOf(addresses), keepACopy);
            }

            public static Forward of(List<MailAddress> addresses, boolean keepACopy) {
                return new Forward(ImmutableList.copyOf(addresses), keepACopy);
            }

            private final ImmutableList<MailAddress> addresses;
            private final boolean keepACopy;

            private Forward(ImmutableList<MailAddress> addresses, boolean keepACopy) {
                this.addresses = addresses;
                this.keepACopy = keepACopy;
            }

            public List<MailAddress> getAddresses() {
                return addresses;
            }

            public boolean isKeepACopy() {
                return keepACopy;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Forward) {
                    Forward forward = (Forward) o;
                    return Objects.equals(addresses, forward.addresses)
                        && Objects.equals(keepACopy, forward.keepACopy);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(addresses, keepACopy);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("addresses", addresses)
                    .add("keepACopy", keepACopy)
                    .toString();
            }
        }

        public static class Builder {
            private AppendInMailboxes appendInMailboxes;
            private boolean markAsSeen;
            private boolean markAsImportant;
            private boolean reject;
            private List<String> withKeywords;
            private Optional<Forward> forward;

            public Builder setAppendInMailboxes(AppendInMailboxes appendInMailboxes) {
                this.appendInMailboxes = appendInMailboxes;
                return this;
            }

            public Builder setMarkAsSeen(boolean markAsSeen) {
                this.markAsSeen = markAsSeen;
                return this;
            }

            public Builder setMarkAsImportant(boolean markAsImportant) {
                this.markAsImportant = markAsImportant;
                return this;
            }

            public Builder setReject(boolean reject) {
                this.reject = reject;
                return this;
            }

            public Builder setWithKeywords(List<String> withKeywords) {
                this.withKeywords = withKeywords;
                return this;
            }

            public Builder setForward(Optional<Forward> forward) {
                this.forward = forward;
                return this;
            }

            public Builder setForward(Forward forward) {
                this.forward = Optional.ofNullable(forward);
                return this;
            }

            public Action build() {
                if (Objects.isNull(appendInMailboxes)) {
                    appendInMailboxes = Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of());
                }
                if (Objects.isNull(withKeywords)) {
                    withKeywords = ImmutableList.of();
                }
                if (Objects.isNull(forward)) {
                    forward = Optional.empty();
                }

                return new Action(appendInMailboxes, markAsSeen, markAsImportant, reject, withKeywords, forward);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Action of(AppendInMailboxes appendInMailboxes) {
            return new Action(appendInMailboxes, false, false, false, ImmutableList.of(), Optional.empty());
        }

        public static Action of(AppendInMailboxes appendInMailboxes, boolean markAsSeen, boolean markAsImportant, boolean reject, List<String> withKeywords) {
            return new Action(appendInMailboxes, markAsSeen, markAsImportant, reject, withKeywords, Optional.empty());
        }

        public static Action of(AppendInMailboxes appendInMailboxes, boolean markAsSeen, boolean markAsImportant,
                                boolean reject, List<String> withKeywords, Optional<Forward> forward) {
            return new Action(appendInMailboxes, markAsSeen, markAsImportant, reject, withKeywords, forward);
        }

        private final AppendInMailboxes appendInMailboxes;
        private final boolean markAsSeen;
        private final boolean markAsImportant;
        private final boolean reject;
        private final List<String> withKeywords;
        private final Optional<Forward> forward;

        private Action(AppendInMailboxes appendInMailboxes, boolean markAsSeen, boolean markAsImportant,
                       boolean reject, List<String> withKeywords, Optional<Forward> forward) {
            this.appendInMailboxes = appendInMailboxes;
            this.markAsSeen = markAsSeen;
            this.markAsImportant = markAsImportant;
            this.reject = reject;
            this.withKeywords = withKeywords;
            this.forward = forward;
        }
        
        public AppendInMailboxes getAppendInMailboxes() {
            return appendInMailboxes;
        }

        public boolean isMarkAsSeen() {
            return markAsSeen;
        }

        public boolean isMarkAsImportant() {
            return markAsImportant;
        }

        public boolean isReject() {
            return reject;
        }

        public Collection<String> getWithKeywords() {
            return withKeywords;
        }

        public Optional<Forward> getForward() {
            return forward;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Action) {
                Action action = (Action) o;
                return Objects.equals(appendInMailboxes, action.appendInMailboxes)
                    && Objects.equals(markAsSeen, action.markAsSeen)
                    && Objects.equals(markAsImportant, action.markAsImportant)
                    && Objects.equals(reject, action.reject)
                    && Objects.equals(withKeywords, action.withKeywords)
                    && Objects.equals(forward, action.forward);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(appendInMailboxes, markAsImportant, markAsSeen, reject, withKeywords, forward);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("appendInMailboxes", appendInMailboxes)
                .add("markAsImportant", markAsImportant)
                .add("markAsSeen", markAsSeen)
                .add("reject", reject)
                .add("withKeywords", withKeywords)
                .add("forward", forward)
                .toString();
        }
    }

    public static class Builder {

        private Id id;
        private String name;
        private ConditionGroup conditionGroup;
        private Action action;

        public Builder id(Id id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder conditionGroup(Condition condition) {
            this.conditionGroup = Rule.ConditionGroup.of(condition);
            return this;
        }

        public Builder conditionGroup(ConditionGroup conditionGroup) {
            this.conditionGroup = conditionGroup;
            return this;
        }

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Builder action(Action.Builder action) {
            this.action = action.build();
            return this;
        }

        public Rule build() {
            Preconditions.checkState(id != null, "`id` is mandatory");
            Preconditions.checkState(StringUtils.isNotBlank(name), "`name` is mandatory");
            Preconditions.checkState(conditionGroup != null, "`conditions` is mandatory");
            Preconditions.checkState(action != null, "`action` is mandatory");

            return new Rule(id, name, conditionGroup, action);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public enum ConditionCombiner {
        AND,
        OR
    }

    private final Id id;
    private final String name;
    private final ConditionGroup conditionGroup;
    private final Action action;

    private Rule(Id id, String name, ConditionGroup conditionGroup, Action action) {
        this.id = id;
        this.name = name;
        this.conditionGroup = conditionGroup;
        this.action = action;
    }

    public Id getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ConditionGroup getConditionGroup() {
        return conditionGroup;
    }

    public Action getAction() {
        return action;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Rule) {
            Rule rule = (Rule) o;

            return Objects.equals(this.id, rule.id)
                && Objects.equals(this.name, rule.name)
                && Objects.equals(this.conditionGroup, rule.conditionGroup)
                && Objects.equals(this.action, rule.action);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, name, conditionGroup, action);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("conditionGroup", conditionGroup)
            .add("action", action)
            .toString();
    }
}
