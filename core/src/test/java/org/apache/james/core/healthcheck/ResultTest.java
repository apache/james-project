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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ResultTest {

    private static final ComponentName COMPONENT_NAME = new ComponentName("component");

    @Test
    public void componentNameShouldBeKeptWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.getComponentName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void componentNameShouldBeKeptWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME);

        assertThat(result.getComponentName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void componentNameShouldBeKeptWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME);

        assertThat(result.getComponentName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void statusShouldBeHealthyWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.getStatus()).isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    public void causeShouldBeEmptyWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.getCause()).isEmpty();
    }

    @Test
    public void isHealthyShouldBeTrueWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    public void isDegradedShouldBeFalseWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.isDegraded()).isFalse();
    }

    @Test
    public void isUnhealthyShouldBeFalseWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.isUnHealthy()).isFalse();
    }

    @Test
    public void statusShouldBeDegradedWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME, "cause");

        assertThat(result.getStatus()).isEqualTo(ResultStatus.DEGRADED);
    }

    @Test
    public void causeMayBeEmptyWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME);

        assertThat(result.getCause()).isEmpty();
    }

    @Test
    public void degradedShouldThrowWhenNullCause() {
        assertThatThrownBy(() -> Result.degraded(COMPONENT_NAME, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void causeShouldBeKeptWhenNotDegraded() {
        String cause = "cause";
        Result result = Result.degraded(COMPONENT_NAME, cause);

        assertThat(result.getCause()).contains(cause);
    }

    @Test
    public void isHealthyShouldBeFalseWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME);

        assertThat(result.isHealthy()).isFalse();
    }

    @Test
    public void isDegradedShouldBeFalseWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME);

        assertThat(result.isDegraded()).isTrue();
    }

    @Test
    public void isUnhealthyShouldBeTrueWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME);

        assertThat(result.isUnHealthy()).isFalse();
    }

    @Test
    public void statusShouldBeUnhealthyWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME, "cause");

        assertThat(result.getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
    }

    @Test
    public void causeMayBeEmptyWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME);

        assertThat(result.getCause()).isEmpty();
    }

    @Test
    public void causeShouldBeKeptWhenNotEmpty() {
        String cause = "cause";
        Result result = Result.unhealthy(COMPONENT_NAME, cause);

        assertThat(result.getCause()).contains(cause);
    }

    @Test
    public void isHealthyShouldBeFalseWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME);

        assertThat(result.isHealthy()).isFalse();
    }

    @Test
    public void isDegradedShouldBeFalseWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME);

        assertThat(result.isDegraded()).isFalse();
    }

    @Test
    public void isUnhealthyShouldBeTrueWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME);

        assertThat(result.isUnHealthy()).isTrue();
    }

    @Test
    public void unhealthyShouldThrowWhenNullCause() {
        assertThatThrownBy(() -> Result.unhealthy(COMPONENT_NAME, null))
            .isInstanceOf(NullPointerException.class);
    }
}
