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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class Rule {

    public static class Id {

        public static Id of(String id) {
            Preconditions.checkArgument(StringUtils.isNotEmpty(id), "`id` is mandatory");
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
                return find(fieldName).orElseThrow(() -> new IllegalStateException("'" + fieldName + "' is not a valid field name"));
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
                return find(comparatorName).orElseThrow(() -> new IllegalStateException("'" + comparatorName + "' is not a valid comparator name"));
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

        public static Action of(AppendInMailboxes appendInMailboxes) {
            return new Action(appendInMailboxes);
        }

        private final AppendInMailboxes appendInMailboxes;

        private Action(AppendInMailboxes appendInMailboxes) {
            this.appendInMailboxes = appendInMailboxes;
        }
        
        public AppendInMailboxes getAppendInMailboxes() {
            return appendInMailboxes;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Action) {
                Action action = (Action) o;
                return Objects.equals(appendInMailboxes, action.appendInMailboxes);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(appendInMailboxes);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("appendInMailboxes", appendInMailboxes)
                .toString();
        }
    }

    public static class Builder {

        private Id id;
        private String name;
        private Condition condition;
        private Action action;

        public Builder id(Id id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder condition(Condition condition) {
            this.condition = condition;
            return this;
        }

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Rule build() {
            return new Rule(id, name, condition, action);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    private final Id id;
    private final String name;
    private final Condition condition;
    private final Action action;

    public Rule(Id id, String name, Condition condition, Action action) {
        Preconditions.checkArgument(id != null, "`id` is mandatory");
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "`name` is mandatory");
        Preconditions.checkArgument(StringUtils.isNotEmpty(name), "`name` is mandatory");
        Preconditions.checkArgument(condition != null, "`condition` is mandatory");
        Preconditions.checkArgument(action != null, "`action` is mandatory");

        this.id = id;
        this.name = name;
        this.condition = condition;
        this.action = action;
    }

    public Id getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Condition getCondition() {
        return condition;
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
                && Objects.equals(this.condition, rule.condition)
                && Objects.equals(this.action, rule.action);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, name, condition, action);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("condition", condition)
            .add("action", action)
            .toString();
    }
}
