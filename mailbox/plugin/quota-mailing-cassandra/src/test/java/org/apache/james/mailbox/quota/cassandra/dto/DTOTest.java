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
import static org.apache.james.mailbox.quota.mailing.events.QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._80;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.mailing.aggregates.UserQuotaThresholds;
import org.apache.james.mailbox.quota.mailing.events.HistoryEvolutionDTO;
import org.apache.james.mailbox.quota.mailing.events.QuotaDTO;
import org.apache.james.mailbox.quota.mailing.events.QuotaThresholdChangedEvent;
import org.apache.james.mailbox.quota.mailing.events.QuotaThresholdChangedEventDTO;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class DTOTest {

    static final Quota<QuotaSizeLimit, QuotaSizeUsage> SIZE_QUOTA = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(23)).computedLimit(QuotaSizeLimit.size(33)).build();
    static final Quota<QuotaCountLimit, QuotaCountUsage> COUNT_QUOTA = Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(12)).computedLimit(QuotaCountLimit.count(45)).build();
    static final Instant INSTANT = Instant.ofEpochMilli(45554);
    public static final String DEFAULT_LISTENER_NAME = "default";
    static final QuotaThresholdChangedEvent EVENT = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.noChanges(),
        HistoryEvolution.noChanges(),
        SIZE_QUOTA,
        COUNT_QUOTA,
        UserQuotaThresholds.Id.from(Username.of("foo@bar.com"), DEFAULT_LISTENER_NAME));
    static final QuotaThresholdChangedEvent EVENT_2 = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_75, INSTANT)),
        HistoryEvolution.noChanges(),
        SIZE_QUOTA,
        Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(12)).computedLimit(QuotaCountLimit.unlimited()).build(),
        UserQuotaThresholds.Id.from(Username.of("foo@bar.com"), DEFAULT_LISTENER_NAME));
    static final QuotaThresholdChangedEvent EVENT_3 = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_75, INSTANT)),
        HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, INSTANT),
            HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod),
        SIZE_QUOTA,
        Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(12)).computedLimit(QuotaCountLimit.unlimited()).build(),
        UserQuotaThresholds.Id.from(Username.of("foo@bar.com"), DEFAULT_LISTENER_NAME));
    static final QuotaThresholdChangedEvent EVENT_4 = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_75, INSTANT)),
        HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, INSTANT),
            HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod),
        SIZE_QUOTA,
        Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(12)).computedLimit(QuotaCountLimit.unlimited()).build(),
        UserQuotaThresholds.Id.from(Username.of("foo@bar.com"), DEFAULT_LISTENER_NAME));

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
        objectMapper.registerSubtypes(new NamedType(QuotaThresholdChangedEventDTO.class, "quota-threshold-change"));
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
    void shouldSerializeQuotaThresholdChangedEvent() throws Exception {
        JsonSerializationVerifier.dtoModule(QUOTA_THRESHOLD_CHANGE)
            .testCase(EVENT, EVENT_JSON)
            .testCase(EVENT_2, EVENT_JSON_2)
            .testCase(EVENT_3, EVENT_JSON_3)
            .testCase(EVENT_4, EVENT_JSON_4)
            .verify();
    }
}