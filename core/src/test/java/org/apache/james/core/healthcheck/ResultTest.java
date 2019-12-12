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

import nl.jqno.equalsverifier.EqualsVerifier;

class ResultTest {

    private static final ComponentName COMPONENT_NAME = new ComponentName("component");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Result.class)
            .verify();
    }

    @Test
    void componentNameShouldBeKeptWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.getComponentName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    void componentNameShouldBeKeptWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME, "cause");

        assertThat(result.getComponentName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    void componentNameShouldBeKeptWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME, "cause");

        assertThat(result.getComponentName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    void statusShouldBeHealthyWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.getStatus()).isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void causeShouldBeEmptyWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.getCause()).isEmpty();
    }

    @Test
    void isHealthyShouldBeTrueWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    void isDegradedShouldBeFalseWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.isDegraded()).isFalse();
    }

    @Test
    void isUnhealthyShouldBeFalseWhenHealthy() {
        Result result = Result.healthy(COMPONENT_NAME);

        assertThat(result.isUnHealthy()).isFalse();
    }

    @Test
    void statusShouldBeDegradedWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME, "cause");

        assertThat(result.getStatus()).isEqualTo(ResultStatus.DEGRADED);
    }

    @Test
    void degradedShouldThrowWhenNullCause() {
        assertThatThrownBy(() -> Result.degraded(COMPONENT_NAME, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void causeShouldBeKeptWhenNotDegraded() {
        String cause = "cause";
        Result result = Result.degraded(COMPONENT_NAME, cause);

        assertThat(result.getCause()).contains(cause);
    }

    @Test
    void isHealthyShouldBeFalseWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME, "cause");

        assertThat(result.isHealthy()).isFalse();
    }

    @Test
    void isDegradedShouldBeFalseWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME, "cause");

        assertThat(result.isDegraded()).isTrue();
    }

    @Test
    void isUnhealthyShouldBeTrueWhenDegraded() {
        Result result = Result.degraded(COMPONENT_NAME, "cause");

        assertThat(result.isUnHealthy()).isFalse();
    }

    @Test
    void statusShouldBeUnhealthyWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME, "cause");

        assertThat(result.getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
    }

    @Test
    void causeShouldBeKeptWhenNotEmpty() {
        String cause = "cause";
        Result result = Result.unhealthy(COMPONENT_NAME, cause);

        assertThat(result.getCause()).contains(cause);
    }

    @Test
    void isHealthyShouldBeFalseWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME, "cause");

        assertThat(result.isHealthy()).isFalse();
    }

    @Test
    void isDegradedShouldBeFalseWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME, "cause");

        assertThat(result.isDegraded()).isFalse();
    }

    @Test
    void isUnhealthyShouldBeTrueWhenUnhealthy() {
        Result result = Result.unhealthy(COMPONENT_NAME, "cause");

        assertThat(result.isUnHealthy()).isTrue();
    }

    @Test
    void unhealthyShouldThrowWhenNullCause() {
        assertThatThrownBy(() -> Result.unhealthy(COMPONENT_NAME, null))
            .isInstanceOf(NullPointerException.class);
    }
}
