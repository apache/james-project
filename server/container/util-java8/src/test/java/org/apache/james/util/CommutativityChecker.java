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

import java.util.Set;
import java.util.function.BinaryOperator;

import org.apache.commons.lang3.tuple.Pair;
import org.paukov.combinatorics3.Generator;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class CommutativityChecker<T> {
    private final Set<T> valuesToTest;
    private final BinaryOperator<T> operationToTest;

    public CommutativityChecker(Set<T> valuesToTest, BinaryOperator<T> operationToTest) {
        Preconditions.checkNotNull(valuesToTest);
        Preconditions.checkNotNull(operationToTest);
        Preconditions.checkArgument(valuesToTest.size() > 1, "You must to pass more than one value to check commutativity");
        this.valuesToTest = valuesToTest;
        this.operationToTest = operationToTest;
    }

    public Set<Pair<T, T>> findNonCommutativeInput() {
        return Generator.combination(valuesToTest)
            .simple(2)
            .stream()
            .map(list -> Pair.of(list.get(0), list.get(1)))
            .filter(this::isNotCommutative)
            .collect(Guavate.toImmutableSet());
    }

    private boolean isNotCommutative(Pair<T, T> pair) {
        T leftThenRight = operationToTest.apply(pair.getLeft(), pair.getRight());
        T rightThenLeft = operationToTest.apply(pair.getRight(), pair.getLeft());
        return !leftThenRight.equals(rightThenLeft);
    }

}
