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

import java.util.concurrent.ConcurrentHashMap

import org.apache.james.eventsourcing.Subscriber
import org.apache.james.task.{TaskExecutionDetails, TaskId}

class TaskExecutionDetailsProjection() {
  private[this] val projections = new ConcurrentHashMap[TaskId, TaskExecutionDetails]

  val asSubscriber: Subscriber = {
    case created: Created =>
      update(created.getAggregateId.taskId, TaskExecutionDetails.from(created.task, created.aggregateId.taskId))
    case cancelRequested: CancelRequested =>
      update(cancelRequested.aggregateId.taskId)(_.cancelRequested())
    case started: Started =>
      update(started.aggregateId.taskId)(_.start())
    case completed: Completed =>
      update(completed.aggregateId.taskId)(_.completed())
    case failed: Failed =>
      update(failed.aggregateId.taskId)(_.failed())
    case canceled: Cancelled =>
      update(canceled.aggregateId.taskId)(_.cancelEffectively())
    case _ =>
  }

  def load(taskId: TaskId): Option[TaskExecutionDetails] = Option(projections.get(taskId))

  private def update(taskId: TaskId, details: TaskExecutionDetails): Unit = projections.put(taskId, details)

  private def update(taskId: TaskId)(updater: TaskExecutionDetails => TaskExecutionDetails): Unit =
    load(taskId)
      .map(updater)
      .foreach(update(taskId, _))
}