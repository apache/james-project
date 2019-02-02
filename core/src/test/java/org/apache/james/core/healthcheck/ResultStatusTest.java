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

package org.apache.james.core.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

public class ResultStatusTest {

    @Test
    public void mergeReturnHealthyWhenMergeWithHealthy() {
        assertThat(ResultStatus.merge(ResultStatus.HEALTHY, ResultStatus.HEALTHY))
                .isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    public void mergeReturnUnHealthyWhenMergeWithUnHealthy() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(ResultStatus.merge(ResultStatus.HEALTHY, ResultStatus.UNHEALTHY))
                .isEqualTo(ResultStatus.UNHEALTHY);
            softly.assertThat(ResultStatus.merge(ResultStatus.DEGRADED, ResultStatus.UNHEALTHY))
                .isEqualTo(ResultStatus.UNHEALTHY);
            softly.assertThat(ResultStatus.merge(ResultStatus.UNHEALTHY, ResultStatus.HEALTHY))
                .isEqualTo(ResultStatus.UNHEALTHY);
            softly.assertThat(ResultStatus.merge(ResultStatus.UNHEALTHY, ResultStatus.DEGRADED))
                .isEqualTo(ResultStatus.UNHEALTHY);
            softly.assertThat(ResultStatus.merge(ResultStatus.UNHEALTHY, ResultStatus.UNHEALTHY))
                .isEqualTo(ResultStatus.UNHEALTHY);
        });
    }

    @Test
    public void mergeReturnDegradedWhenMergeWithDegraded() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(ResultStatus.merge(ResultStatus.HEALTHY, ResultStatus.DEGRADED))
                .isEqualTo(ResultStatus.DEGRADED);
            softly.assertThat(ResultStatus.merge(ResultStatus.DEGRADED, ResultStatus.DEGRADED))
                .isEqualTo(ResultStatus.DEGRADED);
            softly.assertThat(ResultStatus.merge(ResultStatus.DEGRADED, ResultStatus.HEALTHY))
                .isEqualTo(ResultStatus.DEGRADED);
        });
    }
}
