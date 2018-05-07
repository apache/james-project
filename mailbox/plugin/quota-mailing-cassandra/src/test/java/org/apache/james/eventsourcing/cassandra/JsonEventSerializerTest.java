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

package org.apache.james.eventsourcing.cassandra;

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.TestAggregateId;
import org.apache.james.eventsourcing.TestEvent;
import org.apache.james.eventsourcing.cassandra.dto.TestEventDTOModule;
import org.junit.jupiter.api.Test;

class JsonEventSerializerTest {
    public static final String TEST_EVENT_JSON = "{\"type\":\"TestType\",\"data\":\"first\",\"eventId\":0,\"aggregate\":1}";
    public static final TestEvent TEST_EVENT = new TestEvent(
        EventId.fromSerialized(0),
        TestAggregateId.testId(1),
        "first");

    @Test
    void shouldDeserializeKnownEvent() throws Exception {
        assertThat(new JsonEventSerializer(new TestEventDTOModule())
            .deserialize(TEST_EVENT_JSON))
            .isEqualTo(TEST_EVENT);
    }

    @Test
    void shouldThrowWhenDeserializeUnknownEvent() {
        assertThatThrownBy(() -> new JsonEventSerializer()
            .deserialize(TEST_EVENT_JSON))
            .isInstanceOf(JsonEventSerializer.UnknownEventException.class);
    }

    @Test
    void shouldSerializeKnownEvent() throws Exception {
        assertThatJson(new JsonEventSerializer(new TestEventDTOModule())
            .serialize(TEST_EVENT))
            .isEqualTo(TEST_EVENT_JSON);
    }

    @Test
    void shouldThrowWhenSerializeUnknownEvent() {
        assertThatThrownBy(() -> new JsonEventSerializer()
            .serialize(TEST_EVENT))
            .isInstanceOf(JsonEventSerializer.UnknownEventException.class);
    }

}