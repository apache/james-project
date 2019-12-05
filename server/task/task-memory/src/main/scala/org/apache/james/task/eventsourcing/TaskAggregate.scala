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

import scala.jdk.CollectionConverters._

class TaskAggregate private(val aggregateId: TaskAggregateId, private val history: History) {

  val initialEvent = history.getEvents.asScala.headOption match {
    case Some(created @ Created(_, _, _, _)) => created
    case _ => throw new IllegalArgumentException("History must start with Created event")
  }

  private val currentDecisionProjection: DecisionProjection = history
    .getEvents
    .asScala
    .toSeq
    .tail
    .foldLeft(DecisionProjection.initial(initialEvent))((decision, event) => decision.update(event))

  private def optionToJavaList[T](element: Option[T]): util.List[T] = element.toList.asJava

  private def createEventIfNotFinished(event: EventId => Event): Option[Event] = {
    if (!currentDecisionProjection.status.isFinished) {
      Some(event(history.getNextEventId))
    } else
      None
  }

  private def createEvent(event: EventId => Event): Option[Event] = Some(event(history.getNextEventId))

  private def createEventIfNotFinishedAsJavaList(event: EventId => Event): util.List[Event] = optionToJavaList(createEventIfNotFinished(event))

  private[eventsourcing] def start(hostname: Hostname): util.List[Event] =
    createEventIfNotFinishedAsJavaList(Started(aggregateId, _, hostname))

  private[eventsourcing] def requestCancel(hostname: Hostname): util.List[Event] =
    createEventIfNotFinishedAsJavaList(CancelRequested(aggregateId, _, hostname))

  private[eventsourcing] def update(additionalInformation: AdditionalInformation): util.List[Event] =
    optionToJavaList(currentDecisionProjection.status match {
      case Status.IN_PROGRESS if currentDecisionProjection.additionalInformationIsOlderThan(additionalInformation.timestamp) => createEvent(AdditionalInformationUpdated(aggregateId, _, additionalInformation))
      case Status.CANCEL_REQUESTED if currentDecisionProjection.additionalInformationIsOlderThan(additionalInformation.timestamp) => createEvent(AdditionalInformationUpdated(aggregateId, _, additionalInformation))
      case _ => None
    })

  private[eventsourcing] def complete(result: Result, additionalInformation: Option[AdditionalInformation]): util.List[Event] =
    createEventIfNotFinishedAsJavaList(Completed(aggregateId, _, result, additionalInformation))


  private[eventsourcing] def fail(additionalInformation: Option[AdditionalInformation], errorMessage: Option[String], exception: Option[String]): util.List[Event] =
    createEventIfNotFinishedAsJavaList(Failed(aggregateId, _, additionalInformation, errorMessage, exception))

  private[eventsourcing] def cancel(additionalInformation: Option[AdditionalInformation]): util.List[Event] =
    createEventIfNotFinishedAsJavaList(Cancelled(aggregateId, _, additionalInformation))

}

object TaskAggregate {
  def fromHistory(aggregateId: TaskAggregateId, history: History): TaskAggregate = new TaskAggregate(aggregateId, history)

  def create(aggregateId: TaskAggregateId, task: Task, hostname: Hostname): util.List[Event] = {
    List[Event](Created(aggregateId, EventId.first(), task, hostname)).asJava
  }
}
