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

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public interface MetricFactoryContract {

    String NAME_1 = "name 1";
    String NAME_2 = "name 2";

    MetricFactory testee();

    @Test
    default void generateWithSameNameShouldReturnMetricsWithCorrelatedCounter() {
        Metric metric1 = testee().generate(NAME_1);
        Metric anotherMetric1 = testee().generate(NAME_1);

        metric1.add(47);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(metric1.getCount()).isEqualTo(47);
            softly.assertThat(anotherMetric1.getCount()).isEqualTo(47);
        });
    }

    @Test
    default void generateWithDifferentNamesShouldReturnIndependentMetrics() {
        Metric metric1 = testee().generate(NAME_1);
        Metric metric2 = testee().generate(NAME_2);

        metric1.add(1);
        metric2.add(2);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(metric1.getCount()).isEqualTo(1);
            softly.assertThat(metric2.getCount()).isEqualTo(2);
        });
    }
}