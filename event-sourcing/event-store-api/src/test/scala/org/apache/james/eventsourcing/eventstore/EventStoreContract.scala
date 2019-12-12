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
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test

object EventStoreContract {
  val AGGREGATE_1 = TestAggregateId(1)
  val AGGREGATE_2 = TestAggregateId(2)
}

trait EventStoreContract {

  @Test
  def getEventsOfAggregateShouldThrowOnNullAggregateId(testee: EventStore) : Unit =
    assertThatThrownBy(() => testee.getEventsOfAggregate(null))
      .isInstanceOf(classOf[NullPointerException])

  @Test
  def appendShouldThrowWhenEventFromSeveralAggregates(testee: EventStore) : Unit = {
    val event1 = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")
    val event2 = TestEvent(event1.eventId.next, EventStoreContract.AGGREGATE_2, "second")
    assertThatThrownBy(() => testee.appendAll(event1, event2))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def appendShouldDoNothingOnEmptyEventList(testee: EventStore) : Unit =
    assertThatCode(() => testee.appendAll())
      .doesNotThrowAnyException()

  @Test
  def appendShouldThrowWhenTryingToRewriteHistory(testee: EventStore) : Unit = {
    val event1 = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")
    testee.append(event1)
    val event2 = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "second")
    assertThatThrownBy(
      () => testee.append(event2))
      .isInstanceOf(classOf[EventStoreFailedException])
  }

  @Test
  def getEventsOfAggregateShouldReturnEmptyHistoryWhenUnknown(testee: EventStore) : Unit =
    assertThat(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1))
      .isEqualTo(History.empty)

  @Test
  def getEventsOfAggregateShouldReturnAppendedEvent(testee: EventStore) : Unit = {
    val event = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")
    testee.append(event)
    assertThat(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1))
      .isEqualTo(History.of(event))
  }

  @Test
  def getEventsOfAggregateShouldReturnAppendedEvents(testee: EventStore) : Unit = {
    val event1 = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")
    val event2 = TestEvent(event1.eventId.next, EventStoreContract.AGGREGATE_1, "second")
    testee.append(event1)
    testee.append(event2)
    assertThat(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1))
      .isEqualTo(History.of(event1, event2))
  }
}