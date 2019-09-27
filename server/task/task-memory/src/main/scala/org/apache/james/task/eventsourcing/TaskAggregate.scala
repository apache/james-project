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
import org.apache.james.task.TaskManager.Status
import org.apache.james.task.{Hostname, Task}

import scala.collection.JavaConverters._

class TaskAggregate private(val aggregateId: TaskAggregateId, private val history: History) {

  private val currentStatus: Option[Status] = history
    .getEvents
    .asScala
    .foldLeft(DecisionProjection.empty)((decision, event) => decision.update(event))
    .status


  def create(task: Task, hostname: Hostname): util.List[Event] = {
    if (currentStatus.isEmpty) {
      createEventWithId(Created(aggregateId, _, task, hostname))
    } else Nil.asJava
  }

  private[eventsourcing] def start(hostname: Hostname): util.List[Event] = {
    currentStatus match {
      case Some(Status.WAITING) => createEventWithId(Started(aggregateId, _, hostname))
      case _ => Nil.asJava
    }
  }

  def requestCancel(hostname: Hostname): util.List[Event] = {
    currentStatus match {
      case Some(status) if !status.isFinished => createEventWithId(CancelRequested(aggregateId, _, hostname))
      case _ => Nil.asJava
    }
  }

  private[eventsourcing] def complete(result: Result): util.List[Event] = {
    currentStatus match {
      case Some(status) if !status.isFinished => createEventWithId(Completed(aggregateId, _, result))
      case _ => Nil.asJava
    }
  }

  private[eventsourcing] def fail(): util.List[Event] = {
    currentStatus match {
      case Some(status) if !status.isFinished => createEventWithId(Failed(aggregateId, _))
      case _ => Nil.asJava
    }
  }

  private[eventsourcing] def cancel(): util.List[Event] = {
    currentStatus match {
      case Some(status) if !status.isFinished => createEventWithId(Cancelled(aggregateId, _))
      case _ => Nil.asJava
    }
  }

  private def createEventWithId(event: EventId => Event): util.List[Event] =
    List(history.getNextEventId)
      .map({ eventId => event(eventId) })
      .asJava
}

object TaskAggregate {
  def fromHistory(aggregateId: TaskAggregateId, history: History) = new TaskAggregate(aggregateId, history)
}
