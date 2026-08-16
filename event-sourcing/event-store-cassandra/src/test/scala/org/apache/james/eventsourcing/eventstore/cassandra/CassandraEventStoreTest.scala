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

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{literal, update}
import org.apache.james.eventsourcing.eventstore.dto.SnapshotEvent
import org.apache.james.eventsourcing.{EventId, TestEvent}
import org.apache.james.eventsourcing.eventstore.{EventStore, EventStoreContract, History}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.scala.publisher.SMono

@ExtendWith(Array(classOf[CassandraEventStoreExtensionForTestEvents]))
class CassandraEventStoreTest extends EventStoreContract {
  @Test
  def getEventsOfAggregateShouldResumeFromSnapshot(testee: EventStore) : Unit = {
    val event1 = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")
    val event2 = SnapshotEvent(EventId.first.next, EventStoreContract.AGGREGATE_1, "second")
    val event3 = TestEvent(EventId.first.next.next, EventStoreContract.AGGREGATE_1, "third")

    SMono(testee.append(event1)).block()
    SMono(testee.append(event2)).block()
    SMono(testee.append(event3)).block()

    assertThat(SMono(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1)).block())
      .isEqualTo(History.of(event2, event3))
  }

  @Test
  def getEventsOfAggregateShouldResumeFromLatestSnapshot(testee: EventStore) : Unit = {
    val event1 = SnapshotEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")
    val event2 = TestEvent(EventId.first.next, EventStoreContract.AGGREGATE_1, "second")
    val event3 = SnapshotEvent(EventId.first.next.next, EventStoreContract.AGGREGATE_1, "third")

    SMono(testee.append(event1)).block()
    SMono(testee.append(event2)).block()
    SMono(testee.append(event3)).block()

    assertThat(SMono(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1)).block())
      .isEqualTo(History.of(event3))
  }

  @Test
  def getEventsOfAggregateShouldFallBackToFullHistoryWhenSnapshotPointsToMissingEvents(testee: EventStore, session: CqlSession) : Unit = {
    val event1 = TestEvent(EventId.first, EventStoreContract.AGGREGATE_1, "first")

    SMono(testee.append(event1)).block()

    // The snapshot is a static column written by a blind update, outside of the LWT appending the events:
    // it can thus end up referencing events that are no longer part of the partition.
    session.execute(update(CassandraEventStoreTable.EVENTS_TABLE)
      .setColumn(CassandraEventStoreTable.SNAPSHOT, literal(5))
      .whereColumn(CassandraEventStoreTable.AGGREGATE_ID).isEqualTo(literal(EventStoreContract.AGGREGATE_1.asAggregateKey))
      .build())

    assertThat(SMono(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1)).block())
      .isEqualTo(History.of(event1))
  }
}