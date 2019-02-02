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

package org.apache.james.mailbox.quota.cassandra.dto;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._80;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.mailing.aggregates.UserQuotaThresholds;
import org.apache.james.mailbox.quota.mailing.events.QuotaThresholdChangedEvent;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class DTOTest {

    static final Quota<QuotaSize> SIZE_QUOTA = Quota.<QuotaSize>builder().used(QuotaSize.size(23)).computedLimit(QuotaSize.size(33)).build();
    static final Quota<QuotaCount> COUNT_QUOTA = Quota.<QuotaCount>builder().used(QuotaCount.count(12)).computedLimit(QuotaCount.count(45)).build();
    static final Instant INSTANT = Instant.ofEpochMilli(45554);
    public static final String DEFAULT_LISTENER_NAME = "default";
    static final QuotaThresholdChangedEvent EVENT = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.noChanges(),
        HistoryEvolution.noChanges(),
        SIZE_QUOTA,
        COUNT_QUOTA,
        UserQuotaThresholds.Id.from(User.fromUsername("foo@bar.com"), DEFAULT_LISTENER_NAME));
    static final QuotaThresholdChangedEvent EVENT_2 = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_75, INSTANT)),
        HistoryEvolution.noChanges(),
        SIZE_QUOTA,
        Quota.<QuotaCount>builder().used(QuotaCount.count(12)).computedLimit(QuotaCount.unlimited()).build(),
        UserQuotaThresholds.Id.from(User.fromUsername("foo@bar.com"), DEFAULT_LISTENER_NAME));
    static final QuotaThresholdChangedEvent EVENT_3 = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_75, INSTANT)),
        HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, INSTANT),
            HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod),
        SIZE_QUOTA,
        Quota.<QuotaCount>builder().used(QuotaCount.count(12)).computedLimit(QuotaCount.unlimited()).build(),
        UserQuotaThresholds.Id.from(User.fromUsername("foo@bar.com"), DEFAULT_LISTENER_NAME));
    static final QuotaThresholdChangedEvent EVENT_4 = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_75, INSTANT)),
        HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, INSTANT),
            HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod),
        SIZE_QUOTA,
        Quota.<QuotaCount>builder().used(QuotaCount.count(12)).computedLimit(QuotaCount.unlimited()).build(),
        UserQuotaThresholds.Id.from(User.fromUsername("foo@bar.com"), DEFAULT_LISTENER_NAME));

    static final String EVENT_JSON = ClassLoaderUtils.getSystemResourceAsString("json/event.json");
    static final String EVENT_JSON_2 = ClassLoaderUtils.getSystemResourceAsString("json/event2.json");
    static final String EVENT_JSON_3 = ClassLoaderUtils.getSystemResourceAsString("json/event3.json");
    static final String EVENT_JSON_4 = ClassLoaderUtils.getSystemResourceAsString("json/event4.json");

    static final String COUNT_QUOTA_JSON = "{" +
        "   \"used\": 12," +
        "   \"limit\": 45" +
        " }";

    static final String NO_CHANGES_JSON = "{" +
        "  \"change\":\"NoChange\"" +
        "}";

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    @Test
    void shouldSerializeQuotaDTO() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(QuotaDTO.from(COUNT_QUOTA)))
            .isEqualTo(COUNT_QUOTA_JSON);
    }

    @Test
    void shouldDeserializeQuotaDTO() throws Exception {
        assertThat(objectMapper.readValue(COUNT_QUOTA_JSON, QuotaDTO.class).asCountQuota())
            .isEqualTo(COUNT_QUOTA);
    }

    @Test
    void shouldSerializeHistoryEvolutionDTO() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(HistoryEvolutionDTO.toDto(
            HistoryEvolution.noChanges())))
            .isEqualTo(NO_CHANGES_JSON);
    }

    @Test
    void shouldDeserializeHistoryEvolutionDTO() throws Exception {
        assertThat(objectMapper.readValue(NO_CHANGES_JSON, HistoryEvolutionDTO.class)
            .toHistoryEvolution())
            .isEqualTo(HistoryEvolution.noChanges());
    }

    @Test
    void shouldSerializeQuotaThresholdChangedEventDTO() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(
            QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE.toDTO(EVENT)))
            .isEqualTo(EVENT_JSON);
    }

    @Test
    void shouldDeserializeQuotaThresholdChangedEventDTO() throws Exception {
        assertThat(objectMapper.readValue(EVENT_JSON, QuotaThresholdChangedEventDTO.class)
            .toEvent())
            .isEqualTo(EVENT);
    }

    @Test
    void shouldSerializeQuotaThresholdChangedEvent() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .serialize(EVENT))
            .isEqualTo(EVENT_JSON);
    }

    @Test
    void shouldDeserializeQuotaThresholdChangedEvent() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .deserialize(EVENT_JSON))
            .isEqualTo(EVENT);
    }

    @Test
    void shouldSerializeEvent2() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .serialize(EVENT_2))
            .isEqualTo(EVENT_JSON_2);
    }

    @Test
    void shouldDeserializeEvent2() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .deserialize(EVENT_JSON_2))
            .isEqualTo(EVENT_2);
    }

    @Test
    void shouldSerializeEvent3() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .serialize(EVENT_3))
            .isEqualTo(EVENT_JSON_3);
    }

    @Test
    void shouldDeserializeEvent3() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .deserialize(EVENT_JSON_3))
            .isEqualTo(EVENT_3);
    }

    @Test
    void shouldSerializeEvent4() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .serialize(EVENT_4))
            .isEqualTo(EVENT_JSON_4);
    }

    @Test
    void shouldDeserializeEvent4() throws Exception {
        assertThatJson(new JsonEventSerializer(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE)
            .deserialize(EVENT_JSON_4))
            .isEqualTo(EVENT_4);
    }

}