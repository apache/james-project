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

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class EventsourcingConfigurationManagementTest {

    @RegisterExtension
    static CassandraEventStoreExtension eventStoreExtension =
        new CassandraEventStoreExtension(
            JsonEventSerializer.forModules(CassandraMailQueueViewConfigurationModule.MAIL_QUEUE_VIEW_CONFIGURATION).withoutNestedType());

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

    private EventsourcingConfigurationManagement createConfigurationManagement(EventStore eventStore) {
        return new EventsourcingConfigurationManagement(eventStore);
    }

    @Test
    void loadShouldReturnEmptyIfNoConfigurationStored(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);

        assertThat(Mono.from(testee.load()).blockOptional())
            .isEmpty();
    }

    @Test
    void loadShouldReturnTheLastConfiguration(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
        testee.registerConfiguration(FIRST_CONFIGURATION);
        testee.registerConfiguration(SECOND_CONFIGURATION);
        testee.registerConfiguration(THIRD_CONFIGURATION);

        assertThat(Mono.from(testee.load()).blockOptional())
            .contains(THIRD_CONFIGURATION);
    }

    @Test
    void loadConfigurationShouldThrowWhenConfigurationIsNull(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);

        assertThatThrownBy(() -> testee.registerConfiguration(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void loadConfigurationShouldThrowWhenBucketCountDecrease(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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
    void loadConfigurationShouldUpdateStoredConfigurationWhenIncreaseBucketCount(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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

        assertThat(Mono.from(testee.load()).blockOptional())
            .contains(increaseOneBucketConfiguration);
    }

    @Test
    void loadConfigurationShouldStoreConfiguration(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);

        testee.registerConfiguration(FIRST_CONFIGURATION);

        assertThat(Mono.from(testee.load()).blockOptional())
            .contains(FIRST_CONFIGURATION);
    }

    @Test
    void loadConfigurationShouldThrowWhenIncreaseSliceWindow(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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
    void loadConfigurationShouldThrowWhenDecreaseSliceWindowByANotDivisibleNumber(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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
    void loadConfigurationShouldUpdateConfigurationWhenDecreaseSliceWindowByADivisibleNumber(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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

        assertThat(Mono.from(testee.load()).blockOptional())
            .contains(decreaseTwiceSliceWindowConfiguration);
    }

    @Test
    void loadConfigurationShouldUpdateConfigurationWhenIncreaseUpdatePace(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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

        assertThat(Mono.from(testee.load()).blockOptional())
            .contains(decreaseTwiceSliceWindowConfiguration);
    }

    @Test
    void loadConfigurationShouldUpdateConfigurationWhenDecreaseUpdatePace(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
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

        assertThat(Mono.from(testee.load()).blockOptional())
            .contains(decreaseTwiceSliceWindowConfiguration);
    }

    @Test
    void loadConfigurationShouldIgnoreDuplicateWhenStoreTheSameConfigurationTwice(EventStore eventStore) {
        EventsourcingConfigurationManagement testee = createConfigurationManagement(eventStore);
        testee.registerConfiguration(FIRST_CONFIGURATION);
        testee.registerConfiguration(FIRST_CONFIGURATION);

        assertThat(Mono.from(eventStore.getEventsOfAggregate(CONFIGURATION_AGGREGATE_ID))
            .block()
            .getEventsJava())
            .hasSize(1);
    }
}