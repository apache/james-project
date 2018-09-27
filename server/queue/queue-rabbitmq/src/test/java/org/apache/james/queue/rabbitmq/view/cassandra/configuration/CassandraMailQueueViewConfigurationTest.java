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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraMailQueueViewConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(CassandraMailQueueViewConfiguration.class).verify();
    }

    @Test
    void validateConfigurationChangeShouldAcceptIdentity() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatCode(() -> configuration.validateConfigurationChange(configuration))
            .doesNotThrowAnyException();
    }

    @Test
    void validateConfigurationChangeShouldAcceptBucketCountIncrease() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatCode(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(3)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofHours(1))
                .build()))
            .doesNotThrowAnyException();
    }

    @Test
    void validateConfigurationChangeShouldAcceptDividingSliceWindow() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatCode(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(2)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofMinutes(20))
                .build()))
            .doesNotThrowAnyException();
    }

    @Test
    void validateConfigurationChangeShouldRejectArbitraryDecreaseSliceWindow() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatThrownBy(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(2)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofMinutes(25))
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateConfigurationChangeShouldRejectDecreaseBucketCount() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatThrownBy(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(1)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofHours(1))
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}