/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.eventsourcing.eventstore

import org.apache.james.eventsourcing.{AggregateId, Event}

import scala.annotation.varargs
import scala.jdk.CollectionConverters._

trait EventStore {
  def append(event: Event): Unit = appendAll(List(event))

  @varargs
  def appendAll(events: Event*): Unit = appendAll(events.toList)

  def appendAll(events: java.util.List[Event]): Unit = appendAll(events.asScala.toList)

  /**
   * This method should check that no input event has an id already stored and throw otherwise
   * It should also check that all events belong to the same aggregate
   */
  def appendAll(events: List[Event]) : Unit

  def getEventsOfAggregate(aggregateId: AggregateId) : History
}