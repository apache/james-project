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

package org.apache.james.metrics.tests;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordingMetricTest implements MetricContract {

    private RecordingMetric testee;

    @BeforeEach
    void setUp() {
        testee = new RecordingMetric();
    }

    @Override
    public Metric testee() {
        return testee;
    }

    @Test
    void decrementShouldThrowWhenCounterIsZero() {
        assertThatThrownBy(() -> testee.decrement())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("metric counter is supposed to be a non-negative number, thus this operation cannot be applied");
    }

    @Test
    void removeShouldThrowWhenCounterIsLessThanPassedParam() {
        testee.add(10);
        assertThatThrownBy(() -> testee.remove(11))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("metric counter is supposed to be a non-negative number, thus this operation cannot be applied");
    }
}