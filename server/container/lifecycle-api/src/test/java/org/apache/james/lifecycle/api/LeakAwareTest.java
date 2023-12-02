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

package org.apache.james.lifecycle.api;

import static org.apache.james.lifecycle.api.Disposable.LeakAware;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;

import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LeakAwareTest {

    private static final class LeakResourceSample extends LeakAware<LeakResourceSample.TestResource> {
        static class TestResource extends LeakAware.Resource {
            public TestResource(Disposable cleanup) {
                super(cleanup);
            }
        }

        public static LeakResourceSample create(AtomicBoolean atomicBoolean) {
            return new LeakResourceSample(new TestResource(() -> atomicBoolean.set(true)));
        }

        LeakResourceSample(TestResource resource) {
            super(resource);
        }
    }

    private static final ConditionFactory awaitAtMostTenSeconds = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await()
        .atMost(TEN_SECONDS);

    public static ListAppender<ILoggingEvent> getListAppenderForClass(Class clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);

        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();

        logger.addAppender(loggingEventListAppender);
        return loggingEventListAppender;
    }

    private void forceChangeLevel(String level) {
        LeakAware.LEVEL = LeakAware.Level.parse(level);
    }

    // using reflect to change LeakAware.LEVEL value
    private static void forceChangeLevel(LeakAware.Level level) {
        LeakAware.LEVEL = level;
    }

    @Test
    void leakDetectionShouldCloseUnclosedResources() throws NoSuchFieldException, IllegalAccessException {
        forceChangeLevel(LeakAware.Level.SIMPLE);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample = null;

        System.gc();
        awaitAtMostTenSeconds.until(() -> {
            LeakAware.track();
            return atomicBoolean.get();
        });
    }

    @Test
    void leakDetectionShouldNotReportClosedObjects() throws NoSuchFieldException, IllegalAccessException {
        forceChangeLevel(LeakAware.Level.SIMPLE);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample.dispose();
        atomicBoolean.set(false);
        resourceSample = null;

        System.gc();
        awaitAtMostTenSeconds.until(() -> {
            LeakAware.track();
            return !atomicBoolean.get();
        });
    }

    @Test
    void resourceShouldNotBeDetectedLeakWhenLevelIsNone() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        forceChangeLevel(LeakAware.Level.NONE);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample = null;

        System.gc();
        Thread.sleep(500);
        LeakAware.track();
        assertThat(atomicBoolean.get()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple", "advanced"})
    void leakDetectionShouldLogWhenDetected(String level) throws NoSuchFieldException, IllegalAccessException {
        forceChangeLevel(level);
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(Disposable.LeakAwareFinalizer.class);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample = null;

        System.gc();
        awaitAtMostTenSeconds.untilAsserted(() -> {
            LeakAware.track();
            assertThat(loggingEvents.list).hasSize(1)
                .allSatisfy(loggingEvent -> {
                    assertThat(loggingEvent.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(loggingEvent.getFormattedMessage()).contains("Leak detected", "TestResource");
                });
        });
    }

    @Test
    void leakDetectionShouldLogTraceRecordWhenLevelIsAdvanced() throws NoSuchFieldException, IllegalAccessException {
        forceChangeLevel(LeakAware.Level.ADVANCED);
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(Disposable.LeakAwareFinalizer.class);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample = null;

        System.gc();
        awaitAtMostTenSeconds.untilAsserted(() -> {
            LeakAware.track();
            assertThat(loggingEvents.list).hasSize(1)
                .allSatisfy(loggingEvent -> {
                    assertThat(loggingEvent.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(loggingEvent.getFormattedMessage()).contains("This resource was instanced at", "LeakAwareTest#leakDetectionShouldLogTraceRecordWhenLevelIsAdvanced");
                });
        });
    }

    @Test
    void leakDetectionShouldThrowWhenDetectedAndLevelIsTesting() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        forceChangeLevel(LeakAware.Level.TESTING);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample = null;

        System.gc();
        Thread.sleep(500);
        assertThatThrownBy(LeakAware::track)
            .isInstanceOf(LeakAware.LeakDetectorException.class);
    }

    @Test
    void leakDetectionShouldNotLogWhenLevelIsNone() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        forceChangeLevel(LeakAware.Level.NONE);
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(Disposable.LeakAwareFinalizer.class);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        LeakResourceSample resourceSample = LeakResourceSample.create(atomicBoolean);
        resourceSample = null;

        System.gc();
        Thread.sleep(500);
        assertThat(loggingEvents.list).isEmpty();
    }

}
