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

package org.apache.james;


import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.mailbox.events.EventDeadLettersHealthCheck;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PeriodicalHealthChecksTest {

    private static final long INITIAL_DELAY = 1;
    private static final long PERIOD = 1;
    private static final ConditionFactory AWAIT = Awaitility.await()
        .atMost(Duration.TEN_SECONDS)
        .with()
        .pollInterval(Duration.ONE_SECOND);

    private HealthCheck mockHealthCheck1;
    private HealthCheck mockHealthCheck2;
    private PeriodicalHealthChecks testee;

    @BeforeEach
    void setUp() {
        mockHealthCheck1 = Mockito.mock(EventDeadLettersHealthCheck.class);
        mockHealthCheck2 = Mockito.mock(GuiceLifecycleHealthCheck.class);
        when(mockHealthCheck1.check()).thenReturn(Result.healthy(new ComponentName("mockHealthCheck1")));
        when(mockHealthCheck2.check()).thenReturn(Result.healthy(new ComponentName("mockHealthCheck2")));

        Set<HealthCheck> healthCheckSet = new HashSet<>();
        healthCheckSet.add(mockHealthCheck1);
        healthCheckSet.add(mockHealthCheck2);

        testee = new PeriodicalHealthChecks(healthCheckSet, new PeriodicalHealthChecksConfiguration(INITIAL_DELAY, PERIOD));
        testee.start();
    }

    @AfterEach
    void tearDown() {
        testee.stop();
    }

    @Test
    void startShouldCallHealthCheckAtLeastOnce() {
        AWAIT.untilAsserted(() -> verify(mockHealthCheck1, atLeast(1)).check());
    }

    @Test
    void startShouldCallHealthCheckMultipleTimes() {
        AWAIT.untilAsserted(() -> verify(mockHealthCheck1, times(5)).check());
    }

    @Test
    void startShouldCallAllHealthChecks() {
        AWAIT.untilAsserted(() -> {
            verify(mockHealthCheck1, atLeast(5)).check();
            verify(mockHealthCheck2, atLeast(5)).check();
        });
    }
}
