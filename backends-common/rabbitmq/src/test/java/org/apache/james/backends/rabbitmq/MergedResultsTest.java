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

package org.apache.james.backends.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.Test;

class MergedResultsTest {
    private static final ComponentName COMPONENT_NAME = new ComponentName("merged");
    private static final ComponentName ITEM = new ComponentName("item");

    @Test
    void mergeShouldBeHealthyWhenNoResult() {
        assertThat(MergedResults.merge(COMPONENT_NAME, List.of()).isHealthy()).isTrue();
    }

    @Test
    void mergeShouldKeepTheWorstStatusAndEveryCause() {
        Result merged = MergedResults.merge(COMPONENT_NAME, List.of(
            Result.healthy(ITEM),
            Result.degraded(ITEM, "first cause"),
            Result.unhealthy(ITEM, "second cause")));

        assertThat(merged.isUnHealthy()).isTrue();
        assertThat(merged.getComponentName()).isEqualTo(COMPONENT_NAME);
        assertThat(merged.getCause()).contains("first cause; second cause");
    }

    @Test
    void mergeShouldKeepASingleErrorAsIs() {
        RuntimeException error = new RuntimeException("error");

        Result merged = MergedResults.merge(COMPONENT_NAME, List.of(
            Result.unhealthy(ITEM, "cause", error),
            Result.unhealthy(ITEM, "other cause")));

        assertThat(merged.getError()).containsSame(error);
    }

    @Test
    void mergeShouldKeepEveryErrorWhenSeveral() {
        RuntimeException firstError = new RuntimeException("first error");
        RuntimeException secondError = new RuntimeException("second error");

        Result merged = MergedResults.merge(COMPONENT_NAME, List.of(
            Result.unhealthy(ITEM, "first cause", firstError),
            Result.unhealthy(ITEM, "second cause", secondError)));

        assertThat(merged.getError()).hasValueSatisfying(error -> assertThat(error.getSuppressed())
            .containsExactly(firstError, secondError));
    }
}
