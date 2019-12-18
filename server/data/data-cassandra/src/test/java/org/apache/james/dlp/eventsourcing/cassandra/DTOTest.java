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

package org.apache.james.dlp.eventsourcing.cassandra;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.dlp.eventsourcing.cassandra.DLPConfigurationModules.DLP_CONFIGURATION_CLEAR;
import static org.apache.james.dlp.eventsourcing.cassandra.DLPConfigurationModules.DLP_CONFIGURATION_STORE;
import static org.apache.james.util.ClassLoaderUtils.getSystemResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.eventsourcing.aggregates.DLPAggregateId;
import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsAdded;
import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsRemoved;
import org.apache.james.eventsourcing.EventId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

class DTOTest {
    private static final DLPConfigurationItem CONFIGURATION_ITEM_1 = DLPConfigurationItem.builder()
        .id(DLPConfigurationItem.Id.of("1"))
        .explanation("Find whatever contains james.org")
        .expression("james.org")
        .targetsSender()
        .targetsRecipients()
        .targetsContent()
        .build();
    private static final DLPConfigurationItemDTO DLP_CONFIGURATION_DTO_1 = DLPConfigurationItemDTO.from(CONFIGURATION_ITEM_1);
    private static final DLPConfigurationItem CONFIGURATION_ITEM_2 = DLPConfigurationItem.builder()
        .id(DLPConfigurationItem.Id.of("2"))
        .explanation("Find senders have domain apache.org")
        .expression("apache.org")
        .targetsSender()
        .build();
    private static final DLPConfigurationItem CONFIGURATION_ITEM_3 = DLPConfigurationItem.builder()
        .id(DLPConfigurationItem.Id.of("3"))
        .expression("linagora.org")
        .targetsSender()
        .targetsContent()
        .build();

    private static final List<DLPConfigurationItem> DLP_CONFIGURATION_ITEMS = ImmutableList.of(
        CONFIGURATION_ITEM_1,
        CONFIGURATION_ITEM_2,
        CONFIGURATION_ITEM_3);

    private static final DLPAggregateId DLP_AGGREGATE_ID = new DLPAggregateId(Domain.of("james.org"));

    private static final String ITEMS_REMOVED_EVENT_JSON_1 = getSystemResourceAsString("json/dlp/eventsourcing/items_removed_event_1.json");
    private static final String ITEMS_REMOVED_EVENT_JSON_2 = getSystemResourceAsString("json/dlp/eventsourcing/items_removed_event_2.json");
    private static final String CONFIGURATION_ITEMS_JSON_1 = getSystemResourceAsString("json/dlp/eventsourcing/configuration_item_1.json");
    private static final String ITEMS_ADDED_EVENT_JSON_1 = getSystemResourceAsString("json/dlp/eventsourcing/items_added_event_1.json");
    private static final String ITEMS_ADDED_EVENT_JSON_2 = getSystemResourceAsString("json/dlp/eventsourcing/items_added_event_2.json");

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
        objectMapper.registerSubtypes(
            new NamedType(DLPConfigurationItemsRemovedDTO.class, "dlp-configuration-clear"),
            new NamedType(DLPConfigurationItemAddedDTO.class, "dlp-configuration-store"));
    }

    @Test
    void shouldSerializeDLPConfigurationRemovedEvent() throws Exception {
        JsonSerializationVerifier.dtoModule(DLP_CONFIGURATION_CLEAR)
            .bean(new ConfigurationItemsRemoved(
                DLP_AGGREGATE_ID,
                EventId.first(),
                DLP_CONFIGURATION_ITEMS))
            .json(ITEMS_REMOVED_EVENT_JSON_2)
            .verify();
    }

    @Test
    void shouldThrowsExceptionWhenDeserializeRemovedEventWithEmptyItems() {
        assertThatThrownBy(
            () -> objectMapper.readValue(ITEMS_REMOVED_EVENT_JSON_1, DLPConfigurationItemsRemovedDTO.class));
    }

    @Test
    void shouldThrowsExceptionWhenDeserializeAddedEventWithEmptyItems() {
        assertThatThrownBy(
            () -> objectMapper.readValue(ITEMS_ADDED_EVENT_JSON_1, DLPConfigurationItemAddedDTO.class));
    }

    @Test
    void shouldSerializeDLPConfigurationItemAddedEvent() throws Exception {
        JsonSerializationVerifier.dtoModule(DLP_CONFIGURATION_STORE)
            .bean(new ConfigurationItemsAdded(
                DLP_AGGREGATE_ID,
                EventId.first(),
                DLP_CONFIGURATION_ITEMS))
            .json(ITEMS_ADDED_EVENT_JSON_2)
            .verify();
    }

    @Test
    void shouldSerializeDLPConfigurationItemDTO() throws Exception {
        assertThatJson(
            objectMapper.writeValueAsString(DLP_CONFIGURATION_DTO_1))
            .isEqualTo(CONFIGURATION_ITEMS_JSON_1);
    }

    @Test
    void shouldDeserializeDLPConfigurationItemDTO() throws Exception {
        assertThat(
            objectMapper.readValue(CONFIGURATION_ITEMS_JSON_1, DLPConfigurationItemDTO.class))
            .isEqualTo(DLP_CONFIGURATION_DTO_1);
    }
}
