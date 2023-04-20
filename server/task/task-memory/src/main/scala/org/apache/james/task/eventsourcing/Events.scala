/** **************************************************************
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
package org.apache.james.task.eventsourcing

import org.apache.james.eventsourcing.{Event, EventId}
import org.apache.james.task.Task.Result
import org.apache.james.task.TaskExecutionDetails.AdditionalInformation
import org.apache.james.task.{Hostname, Task}

sealed abstract class TaskEvent(aggregateId: TaskAggregateId, val eventId: EventId) extends Event {
  override def getAggregateId: TaskAggregateId = aggregateId
}

sealed abstract class TerminalTaskEvent(aggregateId: TaskAggregateId, override val eventId: EventId, additionalInformation: Option[AdditionalInformation]) extends TaskEvent(aggregateId, eventId)

case class Created(aggregateId: TaskAggregateId, override val eventId: EventId, task: Task, hostname: Hostname) extends TaskEvent(aggregateId, eventId)

case class Started(aggregateId: TaskAggregateId, override val eventId: EventId, hostname: Hostname) extends TaskEvent(aggregateId, eventId)

case class AdditionalInformationUpdated(aggregateId: TaskAggregateId, override val eventId: EventId, additionalInformation: AdditionalInformation) extends TaskEvent(aggregateId, eventId) {
  override val isASnapshot = true
}

case class CancelRequested(aggregateId: TaskAggregateId, override val eventId: EventId, hostname: Hostname) extends TaskEvent(aggregateId, eventId)

case class Completed(aggregateId: TaskAggregateId, override val eventId: EventId, result: Result, additionalInformation: Option[AdditionalInformation]) extends TerminalTaskEvent(aggregateId, eventId, additionalInformation)

case class Failed(aggregateId: TaskAggregateId, override val eventId: EventId, additionalInformation: Option[AdditionalInformation], errorMessage: Option[String], exception: Option[String]) extends TerminalTaskEvent(aggregateId, eventId, additionalInformation)

case class Cancelled(aggregateId: TaskAggregateId, override val eventId: EventId, additionalInformation: Option[AdditionalInformation]) extends TerminalTaskEvent(aggregateId, eventId, additionalInformation)
