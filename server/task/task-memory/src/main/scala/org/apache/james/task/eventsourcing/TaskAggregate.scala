/** **************************************************************
  * Licensed to the Apache Software Foundation (ASF) under one   *
  * or more contributor license agreements.  See the NOTICE file *
  * distributed with this work for additional information        *
  * regarding copyright ownership.  The ASF licenses this file   *
  * to you under the Apache License, Version 2.0 (the            *
  * "License"); you may not use this file except in compliance   *
  * with the License.  You may obtain a copy of the License at   *
  * *
  * http://www.apache.org/licenses/LICENSE-2.0                 *
  * *
  * Unless required by applicable law or agreed to in writing,   *
  * software distributed under the License is distributed on an  *
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
  * KIND, either express or implied.  See the License for the    *
  * specific language governing permissions and limitations      *
  * under the License.                                           *
  * ***************************************************************/
package org.apache.james.task.eventsourcing

import java.util

import org.apache.james.eventsourcing.eventstore.History
import org.apache.james.eventsourcing.{Event, EventId}
import org.apache.james.task.Task.Result
import org.apache.james.task.TaskExecutionDetails.AdditionalInformation
import org.apache.james.task.TaskManager.Status
import org.apache.james.task.{Hostname, Task}

import scala.collection.JavaConverters._

class TaskAggregate private(val aggregateId: TaskAggregateId, private val history: History) {

  history.getEvents.asScala.headOption match {
    case Some(Created(_, _, _, _)) =>
    case _ => throw new IllegalArgumentException("History must start with Created event")
  }

  private val currentStatus: Status = history
    .getEvents
    .asScala
    .foldLeft(DecisionProjection.empty)((decision, event) => decision.update(event))
    .status
    .get

  private[eventsourcing] def start(hostname: Hostname): util.List[Event] = {
    if (!currentStatus.isFinished) {
      createEventWithId(Started(aggregateId, _, hostname))
    } else {
      Nil.asJava
    }
  }

  def requestCancel(hostname: Hostname): util.List[Event] = {
    if (!currentStatus.isFinished) {
      createEventWithId(CancelRequested(aggregateId, _, hostname))
    } else {
      Nil.asJava
    }
  }

  private[eventsourcing] def update(additionalInformation: AdditionalInformation): util.List[Event] = {
    currentStatus match {
      case Status.IN_PROGRESS => createEventWithId(AdditionalInformationUpdated(aggregateId, _, additionalInformation))
      case Status.CANCEL_REQUESTED => createEventWithId(AdditionalInformationUpdated(aggregateId, _, additionalInformation))
      case Status.COMPLETED => Nil.asJava
      case Status.FAILED => Nil.asJava
      case Status.WAITING => Nil.asJava
      case Status.CANCELLED => Nil.asJava
      case _ => Nil.asJava
    }
  }

  private[eventsourcing] def complete(result: Result, additionalInformation: Option[AdditionalInformation]): util.List[Event] = {
    if (!currentStatus.isFinished) {
      createEventWithId(Completed(aggregateId, _, result, additionalInformation))
    } else {
      Nil.asJava
    }
  }

  private[eventsourcing] def fail(additionalInformation: Option[AdditionalInformation], errorMessage: Option[String], exception: Option[String]): util.List[Event] = {
    if (!currentStatus.isFinished) {
      createEventWithId(Failed(aggregateId, _, additionalInformation, errorMessage, exception))
    } else {
      Nil.asJava
    }
  }

  private[eventsourcing] def cancel(additionalInformation: Option[AdditionalInformation]): util.List[Event] = {
    if (!currentStatus.isFinished) {
      createEventWithId(Cancelled(aggregateId, _, additionalInformation))
    } else {
      Nil.asJava
    }
  }

  private def createEventWithId(event: EventId => Event): util.List[Event] =
    List(history.getNextEventId)
      .map({ eventId => event(eventId) })
      .asJava
}

object TaskAggregate {
  def fromHistory(aggregateId: TaskAggregateId, history: History): TaskAggregate = new TaskAggregate(aggregateId, history)

  def create(aggregateId: TaskAggregateId, task: Task, hostname: Hostname): util.List[Event] = {
    List[Event](Created(aggregateId, EventId.first(), task, hostname)).asJava
  }
}
