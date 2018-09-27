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

package org.apache.james.queue.rabbitmq.view.cassandra.configuration;

import static org.apache.james.queue.rabbitmq.view.cassandra.configuration.EventsourcingConfigurationManagement.CONFIGURATION_AGGREGATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventsourcingConfigurationManagementTest {
    private static final int DEFAULT_BUCKET_COUNT = 10;
    private static final int DEFAULT_UPDATE_PACE = 100;
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final Duration FORTY_FIVE_MINUTES = Duration.ofMinutes(45);
    private static final Duration THIRTY_MINUTES = Duration.ofMinutes(30);

    private static final CassandraMailQueueViewConfiguration FIRST_CONFIGURATION = CassandraMailQueueViewConfiguration.builder()
        .bucketCount(DEFAULT_BUCKET_COUNT)
        .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
        .sliceWindow(ONE_HOUR)
        .build();
    private static final CassandraMailQueueViewConfiguration SECOND_CONFIGURATION = CassandraMailQueueViewConfiguration.builder()
        .bucketCount(DEFAULT_BUCKET_COUNT + 1)
        .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
        .sliceWindow(ONE_HOUR)
        .build();
    private static final CassandraMailQueueViewConfiguration THIRD_CONFIGURATION = CassandraMailQueueViewConfiguration.builder()
        .bucketCount(DEFAULT_BUCKET_COUNT + 2)
        .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
        .sliceWindow(ONE_HOUR)
        .build();
    private InMemoryEventStore eventStore;

    private EventsourcingConfigurationManagement createConfigurationManagement() {
        return new EventsourcingConfigurationManagement(eventStore);
    }

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
    }

    @Test
    void loadShouldReturnEmptyIfNoConfigurationStored() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();

        assertThat(testee.load())
            .isEmpty();
    }

    @Test
    void loadShouldReturnTheLastConfiguration() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(FIRST_CONFIGURATION);
        testee.registerConfiguration(SECOND_CONFIGURATION);
        testee.registerConfiguration(THIRD_CONFIGURATION);

        assertThat(testee.load())
            .contains(THIRD_CONFIGURATION);
    }

    @Test
    void loadConfigurationShouldThrowWhenConfigurationIsNull() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();

        assertThatThrownBy(() -> testee.registerConfiguration(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void loadConfigurationShouldThrowWhenBucketCountDecrease() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        assertThatThrownBy(() -> testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
                .bucketCount(DEFAULT_BUCKET_COUNT - 1)
                .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
                .sliceWindow(ONE_HOUR)
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadConfigurationShouldUpdateStoredConfigurationWhenIncreaseBucketCount() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        CassandraMailQueueViewConfiguration increaseOneBucketConfiguration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT + 1)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build();
        testee.registerConfiguration(increaseOneBucketConfiguration);

        assertThat(testee.load())
            .contains(increaseOneBucketConfiguration);
    }

    @Test
    void loadConfigurationShouldStoreConfiguration() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();

        testee.registerConfiguration(FIRST_CONFIGURATION);

        assertThat(testee.load())
            .contains(FIRST_CONFIGURATION);
    }

    @Test
    void loadConfigurationShouldThrowWhenIncreaseSliceWindow() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        assertThatThrownBy(() -> testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
                .bucketCount(DEFAULT_BUCKET_COUNT)
                .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
                .sliceWindow(TWO_HOURS)
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadConfigurationShouldThrowWhenDecreaseSliceWindowByANotDivisibleNumber() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        assertThatThrownBy(() -> testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
                .bucketCount(DEFAULT_BUCKET_COUNT)
                .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
                .sliceWindow(FORTY_FIVE_MINUTES)
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadConfigurationShouldUpdateConfigurationWhenDecreaseSliceWindowByADivisibleNumber() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        CassandraMailQueueViewConfiguration decreaseTwiceSliceWindowConfiguration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(THIRTY_MINUTES)
            .build();
        testee.registerConfiguration(decreaseTwiceSliceWindowConfiguration);

        assertThat(testee.load())
            .contains(decreaseTwiceSliceWindowConfiguration);
    }

    @Test
    void loadConfigurationShouldUpdateConfigurationWhenIncreaseUpdatePace() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        CassandraMailQueueViewConfiguration decreaseTwiceSliceWindowConfiguration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE + 10)
            .sliceWindow(ONE_HOUR)
            .build();
        testee.registerConfiguration(decreaseTwiceSliceWindowConfiguration);

        assertThat(testee.load())
            .contains(decreaseTwiceSliceWindowConfiguration);
    }

    @Test
    void loadConfigurationShouldUpdateConfigurationWhenDecreaseUpdatePace() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE)
            .sliceWindow(ONE_HOUR)
            .build());

        CassandraMailQueueViewConfiguration decreaseTwiceSliceWindowConfiguration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_PACE - 10)
            .sliceWindow(ONE_HOUR)
            .build();
        testee.registerConfiguration(decreaseTwiceSliceWindowConfiguration);

        assertThat(testee.load())
            .contains(decreaseTwiceSliceWindowConfiguration);
    }

    @Test
    void loadConfigurationShouldIgnoreDuplicateWhenStoreTheSameConfigurationTwice() {
        EventsourcingConfigurationManagement testee = createConfigurationManagement();
        testee.registerConfiguration(FIRST_CONFIGURATION);
        testee.registerConfiguration(FIRST_CONFIGURATION);

        List<Event> eventsStored = eventStore.getEventsOfAggregate(CONFIGURATION_AGGREGATE_ID)
            .getEvents();
        assertThat(eventsStored)
            .hasSize(1);
    }
}