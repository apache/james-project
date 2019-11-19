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
    void toStreamShouldConvertEmptyOptionalToEmptyStream() {
        assertThat(OptionalUtils.toStream(Optional.empty()))
            .isEmpty();
    }

    @Test
    void toStreamShouldConvertFullOptionalToStream() {
        long value = 18L;
        assertThat(OptionalUtils.toStream(Optional.of(value)))
            .containsExactly(value);
    }

    @Test
    void orShouldReturnEmptyWhenNoParameter() {
        assertThat(OptionalUtils.or())
            .isEmpty();
    }

    @Test
    void orShouldReturnEmptyWhenEmpty() {
        assertThat(
            OptionalUtils.or(
                Optional.empty()))
            .isEmpty();
    }

    @Test
    void orShouldReturnValueWhenValue() {
        assertThat(
            OptionalUtils.or(
                Optional.of(1)))
            .contains(1);
    }

    @Test
    void orShouldReturnEmptyWhenBothEmpty() {
        assertThat(
            OptionalUtils.or(
                Optional.empty(),
                Optional.empty()))
            .isEmpty();
    }

    @Test
    void orShouldReturnFirstValueWhenOnlyFirstValue() {
        assertThat(
            OptionalUtils.or(
                Optional.of(18),
                Optional.empty()))
            .contains(18);
    }

    @Test
    void orShouldReturnSecondValueWhenOnlySecondValue() {
        assertThat(
            OptionalUtils.or(
                Optional.empty(),
                Optional.of(18)))
            .contains(18);
    }

    @Test
    void orShouldReturnFirstValueWhenBothValues() {
        assertThat(
            OptionalUtils.or(
                Optional.of(1),
                Optional.of(2)))
            .contains(1);
    }

    @Test
    void orShouldReturnThirdValueWhenOnlyThirdValue() {
        assertThat(
            OptionalUtils.or(
                Optional.empty(),
                Optional.empty(),
                Optional.of(1)))
            .contains(1);
    }

    @Test
    void orSuppliersShouldReturnEmptyWhenNoParameter() {
        assertThat(OptionalUtils.or())
            .isEmpty();
    }

    @Test
    void orSuppliersShouldReturnEmptyWhenEmpty() {
        assertThat(
            OptionalUtils.orSuppliers(
                Optional::empty))
            .isEmpty();
    }

    @Test
    void orSuppliersShouldReturnValueWhenValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.of(1)))
            .contains(1);
    }

    @Test
    void orSuppliersShouldReturnEmptyWhenBothEmpty() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.empty(),
                () -> Optional.empty()))
            .isEmpty();
    }

    @Test
    void orSuppliersShouldReturnFirstValueWhenOnlyFirstValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.of(18),
                Optional::empty))
            .contains(18);
    }

    @Test
    void orSuppliersShouldReturnSecondValueWhenOnlySecondValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                Optional::empty,
                () -> Optional.of(18)))
            .contains(18);
    }

    @Test
    void orSuppliersShouldReturnFirstValueWhenBothValues() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.of(1),
                () -> Optional.of(2)))
            .contains(1);
    }

    @Test
    void orSuppliersShouldReturnThirdValueWhenOnlyThirdValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                Optional::empty,
                Optional::empty,
                () -> Optional.of(1)))
            .contains(1);
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
