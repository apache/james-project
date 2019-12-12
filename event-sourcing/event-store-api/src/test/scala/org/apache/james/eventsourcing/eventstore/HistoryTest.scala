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
package org.apache.james.eventsourcing.eventstore

import org.apache.james.eventsourcing.{EventId, TestAggregateId, TestEvent}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class HistoryTest {

  @Test
  def emptyShouldGenerateAnEmptyHistory() : Unit = assertThat(History.empty)
    .isEqualTo(History.of())

  @Test
  def getVersionShouldReturnEmptyWhenEmpty() : Unit = assertThat(History.empty.getVersion)
    .isEqualTo(None)

  @Test
  def getVersionShouldReturnSingleEventIdWhenSingleEvent() : Unit =
    assertThat(History.of(TestEvent(EventId.first, TestAggregateId(42), "any")).getVersion)
      .isEqualTo(Some(EventId.first))

  @Test
  def getVersionShouldReturnHighestEventId() : Unit = {
    val event1 = TestEvent(EventId.first, TestAggregateId(42), "any")
    val event2 = TestEvent(event1.eventId.next, TestAggregateId(42), "any")
    assertThat(History.of(event1, event2).getVersion)
      .isEqualTo(Some(event2.eventId))
  }

  @Test
  def duplicateHistoryShouldThrow() : Unit = {
    val event1 = TestEvent(EventId.first, TestAggregateId(42), "any")
    val event2 = TestEvent(EventId.first, TestAggregateId(42), "any")
    assertThatThrownBy(() => History.of(event1, event2))
      .isInstanceOf(classOf[EventStoreFailedException])
  }
}