 /***************************************************************
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
package org.apache.james.eventsourcing.eventstore.cassandra

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.eventsourcing.eventstore.cassandra.dto.{OtherEvent, TestEventDTOModules}
import org.apache.james.eventsourcing.{EventId, TestAggregateId, TestEvent}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

object JsonEventSerializerTest {
  val EVENT_ID: EventId = EventId.fromSerialized(0)
  val AGGREGATE_ID: TestAggregateId = TestAggregateId(1)
  val OTHER_EVENT: OtherEvent = OtherEvent(EVENT_ID, AGGREGATE_ID, 1)
  val TEST_EVENT: TestEvent = TestEvent(EVENT_ID, AGGREGATE_ID, "first")
  val TEST_EVENT_JSON: String = "{\"type\":\"TestType\",\"data\":\"first\",\"eventId\":0,\"aggregate\":1}"
  val OTHER_EVENT_JSON: String = "{\"type\":\"other-type\",\"data\":1,\"eventId\":0,\"aggregate\":1}"
  val MISSING_TYPE_EVENT_JSON: String = "{\"data\":1,\"eventId\":0,\"aggregate\":1}"
  val DUPLICATE_TYPE_EVENT_JSON: String = "{\"type\":\"TestType\", \"type\":\"other-type\",\"data\":1,\"eventId\":0,\"aggregate\":1}"
}

class JsonEventSerializerTest {
  @Test
  def shouldDeserializeKnownEvent(): Unit =
    assertThat(JsonEventSerializer.forModules(TestEventDTOModules.TEST_TYPE)
      .withoutNestedType.deserialize(JsonEventSerializerTest.TEST_EVENT_JSON))
      .isEqualTo(JsonEventSerializerTest.TEST_EVENT)

  @Test
  def shouldThrowWhenDeserializeUnknownEvent(): Unit =
    assertThatThrownBy(() =>
      JsonEventSerializer
        .forModules()
        .withoutNestedType.deserialize(JsonEventSerializerTest.TEST_EVENT_JSON))
      .isInstanceOf(classOf[JsonEventSerializer.UnknownEventException])

  @Test
  def serializeShouldHandleAllKnownEvents(): Unit = {
    val jsonEventSerializer = JsonEventSerializer.forModules(TestEventDTOModules.TEST_TYPE, TestEventDTOModules.OTHER_TEST_TYPE).withoutNestedType
    assertThatJson(jsonEventSerializer.serialize(JsonEventSerializerTest.OTHER_EVENT)).isEqualTo(JsonEventSerializerTest.OTHER_EVENT_JSON)
    assertThatJson(jsonEventSerializer.serialize(JsonEventSerializerTest.TEST_EVENT)).isEqualTo(JsonEventSerializerTest.TEST_EVENT_JSON)
  }

  @Test
  def deserializeShouldHandleAllKnownEvents(): Unit = {
    val jsonEventSerializer = JsonEventSerializer.forModules(TestEventDTOModules.TEST_TYPE, TestEventDTOModules.OTHER_TEST_TYPE).withoutNestedType
    assertThatJson(jsonEventSerializer.deserialize(JsonEventSerializerTest.OTHER_EVENT_JSON)).isEqualTo(JsonEventSerializerTest.OTHER_EVENT)
    assertThatJson(jsonEventSerializer.deserialize(JsonEventSerializerTest.TEST_EVENT_JSON)).isEqualTo(JsonEventSerializerTest.TEST_EVENT)
  }

  @Test
  def deserializeShouldThrowWhenEventWithMissingType(): Unit =
    assertThatThrownBy(() =>
      JsonEventSerializer
        .forModules(TestEventDTOModules.TEST_TYPE, TestEventDTOModules.OTHER_TEST_TYPE)
        .withoutNestedType.deserialize(JsonEventSerializerTest.MISSING_TYPE_EVENT_JSON))
      .isInstanceOf(classOf[JsonEventSerializer.InvalidEventException])

  @Test
  def shouldSerializeKnownEvent(): Unit =
    assertThatJson(JsonEventSerializer.forModules(TestEventDTOModules.TEST_TYPE)
      .withoutNestedType.serialize(JsonEventSerializerTest.TEST_EVENT))
      .isEqualTo(JsonEventSerializerTest.TEST_EVENT_JSON)

  @Test
  def shouldThrowWhenSerializeUnknownEvent(): Unit =
    assertThatThrownBy(() =>
      JsonEventSerializer.forModules()
        .withoutNestedType.serialize(JsonEventSerializerTest.TEST_EVENT))
      .isInstanceOf(classOf[JsonEventSerializer.UnknownEventException])
}