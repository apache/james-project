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
package org.apache.james.eventsourcing.eventstore.memory

import java.util.concurrent.atomic.AtomicReference

import com.google.common.base.Preconditions
import org.apache.james.eventsourcing.eventstore.{EventStore, History}
import org.apache.james.eventsourcing.{AggregateId, Event}
import org.reactivestreams.Publisher

import reactor.core.scala.publisher.SMono

class InMemoryEventStore() extends EventStore {
  private val storeRef: AtomicReference[Map[AggregateId, History]] =
    new AtomicReference(Map().withDefault(_ => History.empty))

  override def appendAll(events: Iterable[Event]): Publisher[Void] = {
    if (events.nonEmpty) {
      SMono.fromCallable(() => doAppendAll(events)).`then`()
    } else {
      SMono.empty
    }
  }

  override def getEventsOfAggregate(aggregateId: AggregateId): Publisher[History] = {
    Preconditions.checkNotNull(aggregateId)
    SMono.fromCallable(() => storeRef.get()(aggregateId))
  }

  private def doAppendAll(events: Iterable[Event]): Boolean = {
    val aggregateId: AggregateId = getAggregateId(events)
    storeRef.updateAndGet(store => {
      val updatedHistory = History.of(store(aggregateId).getEvents ++ events)
      store.updated(aggregateId, updatedHistory)
    })
    true
  }

  private def getAggregateId(events: Iterable[Event]): AggregateId = {
    Preconditions.checkArgument(events.nonEmpty)
    val aggregateId = events.head.getAggregateId
    Preconditions.checkArgument(belongsToSameAggregate(aggregateId, events))
    aggregateId
  }

  private def belongsToSameAggregate(aggregateId: AggregateId, events: Iterable[Event]) =
    events.forall(_.getAggregateId.equals(aggregateId))

  override def remove(aggregateId: AggregateId): Publisher[Void] =
    SMono.fromCallable(() => storeRef.set(storeRef.get().removed(aggregateId))).`then`()
}