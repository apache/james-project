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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.steveash.guavate.Guavate;

public class OptionalUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void ifEmptyShouldPreserveValueOfEmptyOptionals() {
        Optional<Object> expected = OptionalUtils.executeIfEmpty(Optional.empty(), () -> { });

        assertThat(expected).isEmpty();
    }

    @Test
    public void ifEmptyShouldPreserveValueOfPresentOptionals() {
        String value = "value";
        Optional<String> expected = OptionalUtils.executeIfEmpty(Optional.of(value), () -> { });

        assertThat(expected).contains(value);
    }

    @Test
    public void ifEmptyShouldPerformOperationIfEmpty() {
        AtomicInteger operationCounter = new AtomicInteger(0);

        OptionalUtils.executeIfEmpty(Optional.empty(), operationCounter::incrementAndGet);

        assertThat(operationCounter.get()).isEqualTo(1);
    }

    @Test
    public void ifEmptyShouldNotPerformOperationIfPresent() {
        AtomicInteger operationCounter = new AtomicInteger(0);

        OptionalUtils.executeIfEmpty(Optional.of("value"), operationCounter::incrementAndGet);

        assertThat(operationCounter.get()).isEqualTo(0);
    }

    @Test
    public void toStreamShouldConvertEmptyOptionalToEmptyStream() {
        assertThat(
            OptionalUtils.toStream(Optional.empty())
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void toStreamShouldConvertFullOptionalToStream() {
        long value = 18L;
        assertThat(
            OptionalUtils.toStream(Optional.of(value))
                .collect(Guavate.toImmutableList()))
            .containsExactly(value);
    }

    @Test
    public void orShouldReturnEmptyWhenNoParameter() {
        assertThat(OptionalUtils.or())
            .isEmpty();
    }

    @Test
    public void orShouldReturnEmptyWhenEmpty() {
        assertThat(
            OptionalUtils.or(
                Optional.empty()))
            .isEmpty();
    }

    @Test
    public void orShouldReturnValueWhenValue() {
        assertThat(
            OptionalUtils.or(
                Optional.of(1)))
            .contains(1);
    }

    @Test
    public void orShouldReturnEmptyWhenBothEmpty() {
        assertThat(
            OptionalUtils.or(
                Optional.empty(),
                Optional.empty()))
            .isEmpty();
    }

    @Test
    public void orShouldReturnFirstValueWhenOnlyFirstValue() {
        assertThat(
            OptionalUtils.or(
                Optional.of(18),
                Optional.empty()))
            .contains(18);
    }

    @Test
    public void orShouldReturnSecondValueWhenOnlySecondValue() {
        assertThat(
            OptionalUtils.or(
                Optional.empty(),
                Optional.of(18)))
            .contains(18);
    }

    @Test
    public void orShouldReturnFirstValueWhenBothValues() {
        assertThat(
            OptionalUtils.or(
                Optional.of(1),
                Optional.of(2)))
            .contains(1);
    }

    @Test
    public void orShouldReturnThirdValueWhenOnlyThirdValue() {
        assertThat(
            OptionalUtils.or(
                Optional.empty(),
                Optional.empty(),
                Optional.of(1)))
            .contains(1);
    }

    @Test
    public void orSuppliersShouldReturnEmptyWhenNoParameter() {
        assertThat(OptionalUtils.or())
            .isEmpty();
    }

    @Test
    public void orSuppliersShouldReturnEmptyWhenEmpty() {
        assertThat(
            OptionalUtils.orSuppliers(
                Optional::empty))
            .isEmpty();
    }

    @Test
    public void orSuppliersShouldReturnValueWhenValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.of(1)))
            .contains(1);
    }

    @Test
    public void orSuppliersShouldReturnEmptyWhenBothEmpty() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.empty(),
                () -> Optional.empty()))
            .isEmpty();
    }

    @Test
    public void orSuppliersShouldReturnFirstValueWhenOnlyFirstValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.of(18),
                Optional::empty))
            .contains(18);
    }

    @Test
    public void orSuppliersShouldReturnSecondValueWhenOnlySecondValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                Optional::empty,
                () -> Optional.of(18)))
            .contains(18);
    }

    @Test
    public void orSuppliersShouldReturnFirstValueWhenBothValues() {
        assertThat(
            OptionalUtils.orSuppliers(
                () -> Optional.of(1),
                () -> Optional.of(2)))
            .contains(1);
    }

    @Test
    public void orSuppliersShouldReturnThirdValueWhenOnlyThirdValue() {
        assertThat(
            OptionalUtils.orSuppliers(
                Optional::empty,
                Optional::empty,
                () -> Optional.of(1)))
            .contains(1);
    }

    @Test
    public void containsDifferentShouldReturnTrueWhenNullStoreValue() throws Exception {
        assertThat(OptionalUtils.containsDifferent(Optional.of("any"), null)).isTrue();
    }

    @Test
    public void containsDifferentShouldReturnFalseWhenEmpty() throws Exception {
        assertThat(OptionalUtils.containsDifferent(Optional.empty(), "any")).isFalse();
    }

    @Test
    public void containsDifferentShouldReturnFalseWhenSameValue() throws Exception {
        assertThat(OptionalUtils.containsDifferent(Optional.of("any"), "any")).isFalse();
    }

    @Test
    public void containsDifferentShouldReturnTrueWhenDifferentValue() throws Exception {
        assertThat(OptionalUtils.containsDifferent(Optional.of("any"), "other")).isTrue();
    }

}
