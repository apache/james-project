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


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.events.EventDeadLettersHealthCheck;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

public class PeriodicalHealthChecksTest {

    @FunctionalInterface
    interface TestingHealthCheck extends HealthCheck {
        ComponentName COMPONENT_NAME = new ComponentName("testing");

        Mono<Result> check();

        default ComponentName componentName() {
            return COMPONENT_NAME;
        }
    }

    public static ListAppender<ILoggingEvent> getListAppenderForClass(Class clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);

        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();

        logger.addAppender(loggingEventListAppender);

        return loggingEventListAppender;
    }

    private static final Duration PERIOD = Duration.ofSeconds(10);
    private static final int EXPECTED_INVOKED_TIME = 10;
    private HealthCheck mockHealthCheck1;
    private HealthCheck mockHealthCheck2;
    private VirtualTimeScheduler scheduler;
    private PeriodicalHealthChecks testee;

    @BeforeEach
    void setUp() {
        mockHealthCheck1 = Mockito.mock(EventDeadLettersHealthCheck.class);
        mockHealthCheck2 = Mockito.mock(GuiceLifecycleHealthCheck.class);
        when(mockHealthCheck1.check()).thenReturn(Mono.just(Result.healthy(new ComponentName("mockHealthCheck1"))));
        when(mockHealthCheck2.check()).thenReturn(Mono.just(Result.healthy(new ComponentName("mockHealthCheck2"))));

        scheduler = VirtualTimeScheduler.getOrSet();
        testee = new PeriodicalHealthChecks(ImmutableSet.of(mockHealthCheck1, mockHealthCheck2),
            scheduler,
            new PeriodicalHealthChecksConfiguration(PERIOD));
    }

    @AfterEach
    void tearDown() {
        testee.stop();
    }

    @Test
    void healthChecksShouldBeConsideredFailedIfExceedingTimeout() {
        testee = new PeriodicalHealthChecks(ImmutableSet.of(mockHealthCheck1, mockHealthCheck2),
            scheduler,
            new PeriodicalHealthChecksConfiguration(Duration.ofMillis(1)));

        when(mockHealthCheck1.check()).thenReturn(Mono.just(Result.healthy(new ComponentName("mockHealthCheck1"))).delayElement(Duration.ofMillis(10)));
        when(mockHealthCheck2.check()).thenReturn(Mono.just(Result.healthy(new ComponentName("mockHealthCheck2"))).delayElement(Duration.ofMillis(10)));

        testee.start();

        assertThatCode(() -> scheduler.advanceTimeBy(PERIOD))
            .doesNotThrowAnyException();
    }

    @Test
    void startShouldCallHealthCheckAtLeastOnce() {
        testee.start();

        scheduler.advanceTimeBy(PERIOD);
        verify(mockHealthCheck1, atLeast(1)).check();
    }

    @Test
    void startShouldLogPeriodicallyWhenUnhealthy() {
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(PeriodicalHealthChecks.class);

        TestingHealthCheck unhealthy = () -> Mono.just(Result.unhealthy(TestingHealthCheck.COMPONENT_NAME, "cause"));
        testee = new PeriodicalHealthChecks(ImmutableSet.of(unhealthy),
            scheduler,
            new PeriodicalHealthChecksConfiguration(PERIOD));
        testee.start();

        scheduler.advanceTimeBy(PERIOD);
        assertThat(loggingEvents.list).hasSize(1)
            .allSatisfy(loggingEvent -> {
                assertThat(loggingEvent.getLevel()).isEqualTo(Level.ERROR);
                assertThat(loggingEvent.getFormattedMessage()).contains("UNHEALTHY", "testing", "cause");
            });
    }

    @Test
    void startShouldLogPeriodicallyWhenDegraded() {
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(PeriodicalHealthChecks.class);

        TestingHealthCheck degraded = () -> Mono.just(Result.degraded(TestingHealthCheck.COMPONENT_NAME, "cause"));
        testee = new PeriodicalHealthChecks(ImmutableSet.of(degraded),
            scheduler,
            new PeriodicalHealthChecksConfiguration(PERIOD));
        testee.start();

        scheduler.advanceTimeBy(PERIOD);
        assertThat(loggingEvents.list).hasSize(1)
            .allSatisfy(loggingEvent -> {
                assertThat(loggingEvent.getLevel()).isEqualTo(Level.WARN);
                assertThat(loggingEvent.getFormattedMessage()).contains("DEGRADED", "testing", "cause");
            });
    }

    @Test
    void startShouldNotLogWhenHealthy() {
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(PeriodicalHealthChecks.class);

        TestingHealthCheck healthy = () -> Mono.just(Result.healthy(TestingHealthCheck.COMPONENT_NAME));
        testee = new PeriodicalHealthChecks(ImmutableSet.of(healthy),
            scheduler,
            new PeriodicalHealthChecksConfiguration(PERIOD));
        testee.start();

        scheduler.advanceTimeBy(PERIOD);
        assertThat(loggingEvents.list).hasSize(0);
    }

    @Test
    void startShouldLogWhenMultipleHealthChecks() {
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(PeriodicalHealthChecks.class);

        TestingHealthCheck unhealthy = () -> Mono.just(Result.unhealthy(TestingHealthCheck.COMPONENT_NAME, "cause"));
        TestingHealthCheck degraded = () -> Mono.just(Result.degraded(TestingHealthCheck.COMPONENT_NAME, "cause"));
        TestingHealthCheck healthy = () -> Mono.just(Result.healthy(TestingHealthCheck.COMPONENT_NAME));

        testee = new PeriodicalHealthChecks(ImmutableSet.of(unhealthy, degraded, healthy),
            scheduler,
            new PeriodicalHealthChecksConfiguration(PERIOD));
        testee.start();

        scheduler.advanceTimeBy(PERIOD);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(loggingEvents.list).hasSize(2);
            softly.assertThat(loggingEvents.list.stream()
                .map(event -> new Tuple(event.getLevel(), event.getFormattedMessage()))
                .collect(Guavate.toImmutableList()))
                .containsExactlyInAnyOrder(
                    new Tuple(Level.ERROR, "UNHEALTHY: testing : cause"),
                    new Tuple(Level.WARN, "DEGRADED: testing : cause"));
        });
    }

    @Test
    void startShouldCallHealthCheckMultipleTimes() {
        testee.start();

        scheduler.advanceTimeBy(PERIOD.multipliedBy(EXPECTED_INVOKED_TIME));
        verify(mockHealthCheck1, times(EXPECTED_INVOKED_TIME)).check();
    }

    @Test
    void startShouldCallAllHealthChecks() {
        testee.start();

        scheduler.advanceTimeBy(PERIOD.multipliedBy(EXPECTED_INVOKED_TIME));
        verify(mockHealthCheck1, times(EXPECTED_INVOKED_TIME)).check();
        verify(mockHealthCheck2, times(EXPECTED_INVOKED_TIME)).check();
    }

    @Test
    void startShouldCallRemainingHealthChecksWhenAHealthCheckThrows() {
        when(mockHealthCheck1.check()).thenReturn(Mono.error(new RuntimeException()));

        testee.start();

        scheduler.advanceTimeBy(PERIOD.multipliedBy(EXPECTED_INVOKED_TIME));
        verify(mockHealthCheck2, times(EXPECTED_INVOKED_TIME)).check();
    }
}