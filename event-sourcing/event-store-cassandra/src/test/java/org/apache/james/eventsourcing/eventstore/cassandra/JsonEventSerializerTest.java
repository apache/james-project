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

package org.apache.james.eventsourcing.eventstore.cassandra;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.TestAggregateId;
import org.apache.james.eventsourcing.TestEvent;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.OtherEvent;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.TestEventDTOModules;
import org.junit.jupiter.api.Test;

class JsonEventSerializerTest {
    public static final EventId EVENT_ID = EventId.fromSerialized(0);
    public static final TestAggregateId AGGREGATE_ID = TestAggregateId.testId(1);

    public static final OtherEvent OTHER_EVENT = new OtherEvent(EVENT_ID, AGGREGATE_ID, 1);
    public static final TestEvent TEST_EVENT = new TestEvent(EVENT_ID, AGGREGATE_ID, "first");

    public static final String TEST_EVENT_JSON = "{\"type\":\"TestType\",\"data\":\"first\",\"eventId\":0,\"aggregate\":1}";
    public static final String OTHER_EVENT_JSON = "{\"type\":\"other-type\",\"data\":1,\"eventId\":0,\"aggregate\":1}";
    public static final String MISSING_TYPE_EVENT_JSON = "{\"data\":1,\"eventId\":0,\"aggregate\":1}";
    public static final String DUPLICATE_TYPE_EVENT_JSON = "{\"type\":\"TestType\", \"type\":\"other-type\",\"data\":1,\"eventId\":0,\"aggregate\":1}";

    @Test
    void shouldDeserializeKnownEvent() throws Exception {
        assertThat(JsonEventSerializer.forModules(TestEventDTOModules.TEST_TYPE()).withoutNestedType()
            .deserialize(TEST_EVENT_JSON))
            .isEqualTo(TEST_EVENT);
    }

    @Test
    void shouldThrowWhenDeserializeUnknownEvent() {
        assertThatThrownBy(() -> JsonEventSerializer.forModules().withoutNestedType()
            .deserialize(TEST_EVENT_JSON))
            .isInstanceOf(JsonEventSerializer.UnknownEventException.class);
    }

    @Test
    void serializeShouldHandleAllKnownEvents() throws Exception {
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(TestEventDTOModules.TEST_TYPE(), TestEventDTOModules.OTHER_TEST_TYPE())
            .withoutNestedType();

        assertThatJson(
            jsonEventSerializer.serialize(OTHER_EVENT))
            .isEqualTo(OTHER_EVENT_JSON);

        assertThatJson(
            jsonEventSerializer.serialize(TEST_EVENT))
            .isEqualTo(TEST_EVENT_JSON);
    }

    @Test
    void deserializeShouldHandleAllKnownEvents() throws Exception {
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(TestEventDTOModules.TEST_TYPE(), TestEventDTOModules.OTHER_TEST_TYPE())
            .withoutNestedType();

        assertThatJson(
            jsonEventSerializer.deserialize(OTHER_EVENT_JSON))
            .isEqualTo(OTHER_EVENT);

        assertThatJson(
            jsonEventSerializer.deserialize(TEST_EVENT_JSON))
            .isEqualTo(TEST_EVENT);
    }


    @Test
    void deserializeShouldThrowWhenEventWithDuplicatedTypes() {
        assertThatThrownBy(() -> JsonEventSerializer
            .forModules(TestEventDTOModules.TEST_TYPE(), TestEventDTOModules.OTHER_TEST_TYPE())
            .withoutNestedType()
            .deserialize(DUPLICATE_TYPE_EVENT_JSON))
            .isInstanceOf(JsonEventSerializer.InvalidEventException.class);
    }

    @Test
    void deserializeShouldThrowWhenEventWithMissingType() {
        assertThatThrownBy(() -> JsonEventSerializer
            .forModules(TestEventDTOModules.TEST_TYPE(), TestEventDTOModules.OTHER_TEST_TYPE())
            .withoutNestedType()
            .deserialize(MISSING_TYPE_EVENT_JSON))
            .isInstanceOf(JsonEventSerializer.InvalidEventException.class);
    }

    @Test
    void shouldSerializeKnownEvent() throws Exception {
        assertThatJson(JsonEventSerializer.forModules(TestEventDTOModules.TEST_TYPE()).withoutNestedType()
            .serialize(TEST_EVENT))
            .isEqualTo(TEST_EVENT_JSON);
    }

    @Test
    void shouldThrowWhenSerializeUnknownEvent() {
        assertThatThrownBy(() -> JsonEventSerializer.forModules().withoutNestedType()
            .serialize(TEST_EVENT))
            .isInstanceOf(JsonEventSerializer.UnknownEventException.class);
    }


}