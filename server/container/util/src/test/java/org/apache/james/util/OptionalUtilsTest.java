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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class OptionalUtilsTest {

    @Test
    void ifEmptyShouldPreserveValueOfEmptyOptionals() {
        Optional<Object> expected = OptionalUtils.executeIfEmpty(Optional.empty(), () -> { });

        assertThat(expected).isEmpty();
    }

    @Test
    void ifEmptyShouldPreserveValueOfPresentOptionals() {
        String value = "value";
        Optional<String> expected = OptionalUtils.executeIfEmpty(Optional.of(value), () -> { });

        assertThat(expected).contains(value);
    }

    @Test
    void ifEmptyShouldPerformOperationIfEmpty() {
        AtomicInteger operationCounter = new AtomicInteger(0);

        OptionalUtils.executeIfEmpty(Optional.empty(), operationCounter::incrementAndGet);

        assertThat(operationCounter.get()).isEqualTo(1);
    }

    @Test
    void ifEmptyShouldNotPerformOperationIfPresent() {
        AtomicInteger operationCounter = new AtomicInteger(0);

        OptionalUtils.executeIfEmpty(Optional.of("value"), operationCounter::incrementAndGet);

        assertThat(operationCounter.get()).isEqualTo(0);
    }

    @Test
    void containsDifferentShouldReturnTrueWhenNullStoreValue() {
        assertThat(OptionalUtils.containsDifferent(Optional.of("any"), null)).isTrue();
    }

    @Test
    void containsDifferentShouldReturnFalseWhenEmpty() {
        assertThat(OptionalUtils.containsDifferent(Optional.empty(), "any")).isFalse();
    }

    @Test
    void containsDifferentShouldReturnFalseWhenSameValue() {
        assertThat(OptionalUtils.containsDifferent(Optional.of("any"), "any")).isFalse();
    }

    @Test
    void containsDifferentShouldReturnTrueWhenDifferentValue() {
        assertThat(OptionalUtils.containsDifferent(Optional.of("any"), "other")).isTrue();
    }

    @Test
    void matchesShouldReturnFalseWhenFirstOptionalIsEmpty() {
        assertThat(
            OptionalUtils.matches(
                Optional.empty(),
                Optional.of(42),
                Integer::equals))
            .isFalse();
    }

    @Test
    void matchesShouldReturnFalseWhenSecondOptionalIsEmpty() {
        assertThat(
            OptionalUtils.matches(
                Optional.of(42),
                Optional.empty(),
                Integer::equals))
            .isFalse();
    }

    @Test
    void matchesShouldReturnFalseWhenBothOptionalsAreEmpty() {
        assertThat(
            OptionalUtils.matches(
                Optional.empty(),
                Optional.empty(),
                Integer::equals))
            .isFalse();
    }

    @Test
    void matchesShouldReturnFalseWhenConditionIsNotMatching() {
        assertThat(
            OptionalUtils.matches(
                Optional.of(42),
                Optional.of(43),
                Integer::equals))
            .isFalse();
    }

    @Test
    void matchesShouldReturnTrueWhenConditionIsMatching() {
        assertThat(
            OptionalUtils.matches(
                Optional.of(42),
                Optional.of(42),
                Integer::equals))
            .isTrue();
    }

}
