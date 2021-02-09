/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.modules.blobstore.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class StorageStrategyChangedDTOTest {

    private static final int EVENT_ID_SERIALIZED = 10;
    private static final EventId EVENT_ID = EventId.fromSerialized(EVENT_ID_SERIALIZED);
    private static final String STORAGE_STRATEGY_AGGREGATE_KEY = "aggrKey";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(StorageStrategyChangedDTO.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenStorageStrategyAddedIsNull() {
        assertThatThrownBy(() -> StorageStrategyChangedDTO.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldReturnCorrespondingDTO() {
        StorageStrategyChanged configurationChanged = new StorageStrategyChanged(
            EVENT_ID,
            () -> STORAGE_STRATEGY_AGGREGATE_KEY,
            StorageStrategy.DEDUPLICATION);

        StorageStrategyChangedDTO dto = StorageStrategyChangedDTO.from(configurationChanged);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dto.getEventId()).isEqualTo(EVENT_ID_SERIALIZED);
            softly.assertThat(dto.getType()).isEqualTo(StorageStrategyModule.TYPE_NAME);
            softly.assertThat(dto.getStorageStrategy()).isEqualTo(StorageStrategy.DEDUPLICATION.name());
        });
    }

    @Test
    void toEventShouldReturnCorrespondingStorageStrategyChangedEvent() {
        StorageStrategyChangedDTO dto = new StorageStrategyChangedDTO(
            EVENT_ID_SERIALIZED,
            STORAGE_STRATEGY_AGGREGATE_KEY,
            StorageStrategyModule.TYPE_NAME,
            StorageStrategy.DEDUPLICATION.name());
        StorageStrategyChanged event = dto.toEvent();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(event.eventId()).isEqualTo(EVENT_ID);
            softly.assertThat(event.getAggregateId().asAggregateKey()).isEqualTo(STORAGE_STRATEGY_AGGREGATE_KEY);
            softly.assertThat(event.getStorageStrategy()).isEqualTo(StorageStrategy.DEDUPLICATION);
        });
    }
}
