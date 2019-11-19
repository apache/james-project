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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.function.BinaryOperator;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class CommutativityCheckerTest {
    @Test
    void constructorShouldThrowWhenNullValuesToTest() {
        BinaryOperator<Integer> binaryOperator = (a, b) -> a * a + b;

        assertThatThrownBy(() -> new CommutativityChecker<>(null, binaryOperator))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenEmptyValuesToTest() {
        BinaryOperator<Integer> binaryOperator = (a, b) -> a * a + b;

        assertThatThrownBy(() -> new CommutativityChecker<>(ImmutableSet.of(), binaryOperator))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowWhenSingleValueToTest() {
        BinaryOperator<Integer> binaryOperator = (a, b) -> a * a + b;

        assertThatThrownBy(() -> new CommutativityChecker<>(ImmutableSet.of(0), binaryOperator))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowWhenNullOperation() {
        assertThatThrownBy(() -> new CommutativityChecker<>(ImmutableSet.of(0, 1), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findNonCommutativeInputShouldReturnEmptyWhenCommutativeOperation() {
        Set<Integer> integers = ImmutableSet.of(5, 4, 3, 2, 1);
        BinaryOperator<Integer> commutativeOperator = (a, b) -> a + b;
        CommutativityChecker<Integer> commutativityChecker = new CommutativityChecker<>(integers, commutativeOperator);

        assertThat(commutativityChecker.findNonCommutativeInput()).isEmpty();
    }

    @Test
    void findNonCommutativeInputShouldReturnDataWhenNonCommutativeOperation() {
        Set<Integer> integers = ImmutableSet.of(2, 1);
        BinaryOperator<Integer> nonCommutativeOperator = (a, b) -> 2 * a + b;
        CommutativityChecker<Integer> commutativityChecker = new CommutativityChecker<>(integers, nonCommutativeOperator);

        assertThat(commutativityChecker.findNonCommutativeInput())
            .containsOnly(Pair.of(2, 1));
    }

    @Test
    void findNonCommutativeInputShouldNotReturnStableValues() {
        Set<Integer> integers = ImmutableSet.of(0, 1, 2);
        BinaryOperator<Integer> nonCommutativeOperatorWithStableValues = (a, b) -> a * a + b;
        CommutativityChecker<Integer> commutativityChecker = new CommutativityChecker<>(integers, nonCommutativeOperatorWithStableValues);

        assertThat(commutativityChecker.findNonCommutativeInput())
            .containsOnly(Pair.of(1, 2),
                Pair.of(0, 2));
    }

    @Test
    void findNonCommutativeInputShouldReturnEmptyWhenNonCommutativeOperationButOnlyStableValues() {
        Set<Integer> stableValues = ImmutableSet.of(0, 1);
        BinaryOperator<Integer> nonCommutativeOperatorWithStableValues = (a, b) -> a * a + b;
        CommutativityChecker<Integer> commutativityChecker = new CommutativityChecker<>(stableValues, nonCommutativeOperatorWithStableValues);

        assertThat(commutativityChecker.findNonCommutativeInput()).isEmpty();
    }
}
