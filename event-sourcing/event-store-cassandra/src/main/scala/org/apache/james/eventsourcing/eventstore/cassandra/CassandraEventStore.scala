/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.eventsourcing.eventstore.cassandra

import com.google.common.base.Preconditions
import jakarta.inject.Inject
import org.apache.james.eventsourcing.eventstore.{EventStore, EventStoreFailedException, History}
import org.apache.james.eventsourcing.{AggregateId, Event, EventId}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class CassandraEventStore @Inject() (eventStoreDao: EventStoreDao) extends EventStore {
  override def appendAll(events: Iterable[Event]): Publisher[Void] =
    if (events.nonEmpty) {
      doAppendAll(events)
    } else {
      SMono.empty
    }

  private def doAppendAll(events: Iterable[Event]): SMono[Void] = {
    Preconditions.checkArgument(Event.belongsToSameAggregate(events))
    val snapshotId = events.filter(_.isASnapshot).map(_.eventId).headOption
    eventStoreDao.appendAll(events, snapshotId)
      .filter(success => success)
      .single()
      .onErrorMap({
        case _: NoSuchElementException => EventStoreFailedException("Concurrent update to the EventStore detected")
        case e => e
      })
      .`then`(SMono.empty)
  }

  override def getEventsOfAggregate(aggregateId: AggregateId): SMono[History] =
    eventStoreDao.getSnapshot(aggregateId)
      .flatMap(snapshotId => eventStoreDao.getEventsOfAggregate(aggregateId, snapshotId))
      .switchIfEmpty(eventStoreDao.getEventsOfAggregate(aggregateId))

  override def remove(aggregateId: AggregateId): Publisher[Void] = eventStoreDao.delete(aggregateId)
}
