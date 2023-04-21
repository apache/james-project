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
package org.apache.james.eventsourcing.eventstore

import org.apache.james.eventsourcing.{Event, EventId}

import java.util.Optional
import scala.annotation.varargs
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object History {
  def empty: History = new History(Nil)

  def of(events: List[Event]): History = new History(events)

  @varargs
  def of(events: Event*): History = of(events.toList)
}

final case class History private(events: List[Event]) {
  if (hasEventIdDuplicates(events)) {
    throw EventStoreFailedException("Event History contains duplicated EventId")
  }

  private def hasEventIdDuplicates(events: List[Event]) = {
    val eventIdsNumber = events.map(event => event.eventId)
      .toSet
      .size
    eventIdsNumber != events.size
  }

  def getVersion: Option[EventId] = events
    .map(event => event.eventId)
    .maxOption

  def getVersionAsJava: Optional[EventId] = getVersion.toJava

  def getEvents: List[Event] = events

  def getEventsJava: java.util.List[Event] = events.asJava

  def getNextEventId: EventId = getVersion
    .map(eventId => eventId.next)
    .getOrElse(EventId.first)

}