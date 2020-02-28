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

package org.apache.james.mock.smtp.server.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

public interface Operator {
    enum OperatorName {
        CONTAINS("contains", maybeMatchingValue -> {
            Preconditions.checkState(maybeMatchingValue.isPresent(), "You need to specify a matchingValue with the contains operator");

            return new Condition.OperatorCondition(Operator.CONTAINS, maybeMatchingValue.get());
        }),
        MATCH_ALL("matchAll", maybeMatchingValue -> {
            Preconditions.checkState(!maybeMatchingValue.isPresent(), "You should not specify a matchingValue with the matchAll operator");

            return new Condition.MatchAllCondition();
        });

        private final String name;
        private final Function<Optional<String>, Condition> conditionFactory;

        @JsonCreator
        public static OperatorName from(String name) {
            return Arrays.stream(values())
                .filter(value -> value.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsuported " + name + " operator"));
        }

        OperatorName(String name, Function<Optional<String>, Condition> conditionFactory) {
            this.name = name;
            this.conditionFactory = conditionFactory;
        }

        @JsonValue
        public String getName() {
            return name;
        }

        public Function<Optional<String>, Condition> getConditionFactory() {
            return conditionFactory;
        }
    }

    @FunctionalInterface
    interface Expected {
        Matcher expected(String expected);
    }

    @FunctionalInterface
    interface Matcher {
        boolean matches();
    }

    Operator CONTAINS = new Operator() {
        @Override
        public Expected actual(String actual) {
            return expected -> () -> actual.contains(expected);
        }

        @Override
        public OperatorName getOperatorName() {
            return OperatorName.CONTAINS;
        }
    };

    Expected actual(String actual);

    OperatorName getOperatorName();
}
