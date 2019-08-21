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

package org.apache.james.mock.smtp.server;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = Condition.Builder.class)
interface Condition {
    @JsonPOJOBuilder(withPrefix = "")
    class Builder {
        private Operator.OperatorName operator;
        private Optional<String> matchingValue;

        public Builder() {
            this.matchingValue = Optional.empty();
        }

        public Builder operator(Operator.OperatorName operator) {
            this.operator = operator;
            return this;
        }

        public Builder matchingValue(String matchingValue) {
            this.matchingValue = Optional.of(matchingValue);
            return this;
        }

        public Condition build() {
            Preconditions.checkState(operator != null, "You need to specify an operator");

            return operator.getConditionFactory().apply(matchingValue);
        }
    }

    class MatchAllCondition implements Condition {
        public Operator.OperatorName getOperator() {
            return Operator.OperatorName.MATCH_ALL;
        }

        @Override
        public boolean matches(String line) {
            return true;
        }

        @Override
        public final boolean equals(Object o) {
            return o instanceof MatchAllCondition;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(MatchAllCondition.class);
        }
    }

    class OperatorCondition implements Condition {
        private final Operator operator;
        private final String matchingValue;

        OperatorCondition(Operator operator, String matchingValue) {
            Preconditions.checkNotNull(operator);
            Preconditions.checkNotNull(matchingValue);

            this.operator = operator;
            this.matchingValue = matchingValue;
        }

        public Operator.OperatorName getOperator() {
            return operator.getOperatorName();
        }

        public String getMatchingValue() {
            return matchingValue;
        }

        @Override
        public boolean matches(String line) {
            return operator.actual(line)
                .expected(matchingValue)
                .matches();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof OperatorCondition) {
                OperatorCondition condition = (OperatorCondition) o;

                return Objects.equals(this.operator, condition.operator)
                    && Objects.equals(this.matchingValue, condition.matchingValue);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(operator, matchingValue);
        }
    }

    Condition MATCH_ALL = new MatchAllCondition();

    boolean matches(String line);
}
