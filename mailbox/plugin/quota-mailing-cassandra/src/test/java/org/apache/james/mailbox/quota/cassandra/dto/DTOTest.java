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

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.User;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.cassandra.JsonEventSerializer;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.mailing.aggregates.UserQuotaThresholds;
import org.apache.james.mailbox.quota.mailing.events.QuotaThresholdChangedEvent;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class DTOTest {

    public static final Quota<QuotaSize> SIZE_QUOTA = Quota.<QuotaSize>builder().used(QuotaSize.size(23)).computedLimit(QuotaSize.size(33)).build();
    public static final Quota<QuotaCount> COUNT_QUOTA = Quota.<QuotaCount>builder().used(QuotaCount.count(12)).computedLimit(QuotaCount.count(45)).build();
    public static final QuotaThresholdChangedEvent EVENT = new QuotaThresholdChangedEvent(
        EventId.first(),
        HistoryEvolution.noChanges(),
        HistoryEvolution.noChanges(),
        SIZE_QUOTA,
        COUNT_QUOTA,
        UserQuotaThresholds.Id.from(User.fromUsername("foo@bar.com")));

    public static final String EVENT_JSON = "{" +
        " \"type\": \"quota-threshold-change\"," +
        " \"eventId\": 0," +
        " \"user\": \"foo@bar.com\"," +
        " \"sizeQuota\": {" +
        "   \"used\": 23," +
        "   \"limit\": 33" +
        " }," +
        " \"countQuota\": {" +
        "   \"used\": 12," +
        "   \"limit\": 45" +
        " }," +
        " \"sizeEvolution\": {" +
        "   \"change\": \"NoChange\"," +
        "   \"threshold\": null," +
        "   \"instant\": null," +
        "   \"recentness\": null" +
        " }," +
        " \"countEvolution\": {" +
        "   \"change\": \"NoChange\"," +
        "   \"threshold\": null," +
        "   \"instant\": null," +
        "   \"recentness\": null" +
        " }" +
        "}";

    public static final String COUNT_QUOTA_JSON = "{" +
        "   \"used\": 12," +
        "   \"limit\": 45" +
        " }";

    public static final String NO_CHANGES_JSON = "{" +
        "  \"change\":\"NoChange\"" +
        "}";

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
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
            new QuotaThresholdChangedEventDTOModule().toDTO(EVENT)))
            .isEqualTo(EVENT_JSON);
    }

    @Test
    void shouldDeserializeQuotaThresholdChangedEventDTO() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());

        assertThat(objectMapper.readValue(EVENT_JSON, QuotaThresholdChangedEventDTO.class)
            .toEvent())
            .isEqualTo(EVENT);
    }

    @Test
    void shouldSerializeQuotaThresholdChangedEvent() throws Exception {
        assertThatJson(new JsonEventSerializer(new QuotaThresholdChangedEventDTOModule())
            .serialize(EVENT))
            .isEqualTo(EVENT_JSON);
    }

}