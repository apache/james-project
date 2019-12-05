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
import javax.inject.Inject
import org.apache.james.eventsourcing.eventstore.{EventStore, EventStoreFailedException, History}
import org.apache.james.eventsourcing.{AggregateId, Event}

class CassandraEventStore @Inject() (eventStoreDao: EventStoreDao) extends EventStore {
  override def appendAll(events: List[Event]): Unit = {
    if (events.nonEmpty) {
      doAppendAll(events)
    }
  }

  private def doAppendAll(events: List[Event]): Unit = {
    Preconditions.checkArgument(Event.belongsToSameAggregate(events))
    val success: Boolean = eventStoreDao.appendAll(events).block()
    if (!success) {
      throw EventStoreFailedException("Concurrent update to the EventStore detected")
    }
  }

  override def getEventsOfAggregate(aggregateId: AggregateId): History = {
    eventStoreDao.getEventsOfAggregate(aggregateId).block()
  }
}