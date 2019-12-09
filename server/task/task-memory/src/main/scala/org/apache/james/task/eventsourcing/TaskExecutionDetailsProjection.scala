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
import org.apache.james.task.{Hostname, TaskExecutionDetails, TaskId}

import scala.compat.java8.OptionConverters._
import scala.jdk.CollectionConverters._

trait TaskExecutionDetailsProjection {
  def asSubscriber(hostname: Hostname): Subscriber = {
    case created: Created =>
      update(TaskExecutionDetails.from(created.task, created.aggregateId.taskId, created.hostname))
    case cancelRequested: CancelRequested =>
      update(cancelRequested.aggregateId.taskId)(_.cancelRequested(hostname))
    case started: Started =>
      update(started.aggregateId.taskId)(_.started(hostname))
    case completed: Completed =>
      update(completed.aggregateId.taskId)(_.completed(completed.additionalInformation.asJava))
    case failed: Failed =>
      update(failed.aggregateId.taskId)(_.failed(failed.additionalInformation.asJava))
    case canceled: Cancelled =>
      update(canceled.aggregateId.taskId)(_.cancelEffectively(canceled.additionalInformation.asJava))
    case updated: AdditionalInformationUpdated =>
      update(updated.aggregateId.taskId)(_.updateInformation(updated.additionalInformation))
  }

  private def update(taskId: TaskId)(updater: TaskExecutionDetails => TaskExecutionDetails): Unit =
    load(taskId)
      .map(updater)
      .foreach(update)

  def load(taskId: TaskId): Option[TaskExecutionDetails]
  def list: List[TaskExecutionDetails]
  def update(details: TaskExecutionDetails): Unit
}

class MemoryTaskExecutionDetailsProjection() extends TaskExecutionDetailsProjection {
  private[this] val details = new ConcurrentHashMap[TaskId, TaskExecutionDetails]

  override def load(taskId: TaskId): Option[TaskExecutionDetails] = Option(this.details.get(taskId))

  override def list: List[TaskExecutionDetails] = this.details.values().asScala.toList

  override def update(details: TaskExecutionDetails): Unit = this.details.put(details.taskId, details)
}
