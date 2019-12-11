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

package org.apache.james.metrics.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public interface MetricContract {

    Metric testee();

    @Test
    default void incrementShouldIncreaseCounter() {
        testee().increment();

        assertThat(testee().getCount())
            .isEqualTo(1);
    }

    @Test
    default void incrementShouldIncreaseCounterAfterMultipleCalls() {
        testee().increment();
        testee().increment();
        testee().increment();

        assertThat(testee().getCount())
            .isEqualTo(3);
    }

    @Test
    default void decrementShouldDecreaseCounter() {
        testee().decrement();

        assertThat(testee().getCount())
            .isEqualTo(-1);
    }

    @Test
    default void decrementShouldDecreaseCounterAfterMultipleCalls() {
        testee().decrement();
        testee().decrement();
        testee().decrement();

        assertThat(testee().getCount())
            .isEqualTo(-3);
    }

    @Test
    default void addShouldIncreaseCounter() {
        testee().add(10);

        assertThat(testee().getCount())
            .isEqualTo(10);
    }

    @Test
    default void addShouldDecreaseCounterWhenNegativeNumber() {
        testee().add(-9);

        assertThat(testee().getCount())
            .isEqualTo(-9);
    }

    @Test
    default void addShouldKeepCounterBeTheSameWhenZero() {
        testee().add(10);
        testee().add(0);

        assertThat(testee().getCount())
            .isEqualTo(10);
    }

    @Test
    default void removeShouldDecreaseCounter() {
        testee().remove(10);

        assertThat(testee().getCount())
            .isEqualTo(-10);
    }

    @Test
    default void removeShouldIncreaseCounterWhenNegativeNumber() {
        testee().remove(-9);

        assertThat(testee().getCount())
            .isEqualTo(9);
    }

    @Test
    default void removeShouldKeepCounterBeTheSameWhenZero() {
        testee().remove(888);
        testee().remove(0);

        assertThat(testee().getCount())
            .isEqualTo(-888);
    }

    @Test
    default void getCountShouldReturnZeroWhenNoUpdate() {
        assertThat(testee().getCount())
            .isEqualTo(0);
    }
}