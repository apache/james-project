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
import org.apache.james.eventsourcing.{CommandHandler, Event}
import org.apache.james.task.eventsourcing.TaskCommand._
import org.apache.james.task.{Hostname, TaskId}

sealed abstract class TaskCommandHandler[T <: TaskCommand] extends CommandHandler[T] {

  def loadAggregate(loadHistory: TaskAggregateId => History, taskId: TaskId): TaskAggregate = {
    val aggregateId = TaskAggregateId(taskId)
    TaskAggregate.fromHistory(aggregateId, loadHistory(aggregateId))
  }
}

class CreateCommandHandler(private val loadHistory: TaskAggregateId => History, hostname: Hostname) extends TaskCommandHandler[Create] {
  override def handledClass: Class[Create] = classOf[Create]

  override def handle(command: Create): List[_ <: Event] = {
    TaskAggregate.create(TaskAggregateId(command.id), command.task, hostname)
  }
}

class StartCommandHandler(private val loadHistory: TaskAggregateId => History,
                          private val hostname: Hostname) extends TaskCommandHandler[Start] {
  override def handledClass: Class[Start] = classOf[Start]

  override def handle(command: Start): List[_ <: Event] = {
    loadAggregate(loadHistory, command.id).start(hostname)
  }
}

class RequestCancelCommandHandler(private val loadHistory: TaskAggregateId => History,
                                  private val hostname: Hostname) extends TaskCommandHandler[RequestCancel] {
  override def handledClass: Class[RequestCancel] = classOf[RequestCancel]

  override def handle(command: RequestCancel): List[_ <: Event] = {
    loadAggregate(loadHistory, command.id).requestCancel(hostname)
  }
}

class CompleteCommandHandler(private val loadHistory: TaskAggregateId => History) extends TaskCommandHandler[Complete] {
  override def handledClass: Class[Complete] = classOf[Complete]

  override def handle(command: Complete): List[_ <: Event] = {
    loadAggregate(loadHistory, command.id).complete(command.result, command.additionalInformation)
  }
}

class CancelCommandHandler(private val loadHistory: TaskAggregateId => History) extends TaskCommandHandler[Cancel] {
  override def handledClass: Class[Cancel] = classOf[Cancel]

  override def handle(command: Cancel): List[_ <: Event] = {
    loadAggregate(loadHistory, command.id).cancel(command.additionalInformation)
  }
}

class FailCommandHandler(private val loadHistory: TaskAggregateId => History) extends TaskCommandHandler[Fail] {
  override def handledClass: Class[Fail] = classOf[Fail]

  override def handle(command: Fail): List[_ <: Event] = {
    loadAggregate(loadHistory, command.id).fail(command.additionalInformation, command.errorMessage, command.exception)
  }
}

class UpdateCommandHandler(private val loadHistory: TaskAggregateId => History) extends TaskCommandHandler[UpdateAdditionalInformation] {
  override def handledClass: Class[UpdateAdditionalInformation] = classOf[UpdateAdditionalInformation]

  override def handle(command: UpdateAdditionalInformation): List[_ <: Event] = {
    loadAggregate(loadHistory, command.id).update(command.additionalInformation)
  }
}