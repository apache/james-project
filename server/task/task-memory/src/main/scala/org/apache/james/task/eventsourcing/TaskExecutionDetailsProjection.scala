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
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.james.eventsourcing.ReactiveSubscriber
import org.apache.james.task.{Hostname, TaskExecutionDetails, TaskId, TaskManager}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.time.Instant
import scala.compat.java8.OptionConverters._
import scala.jdk.CollectionConverters._

trait TaskExecutionDetailsProjection {
  def asSubscriber(hostname: Hostname): ReactiveSubscriber = event => event.event match {
    case created: Created =>
      updateReactive(TaskExecutionDetails.from(created.task, created.aggregateId.taskId, created.hostname))
    case cancelRequested: CancelRequested =>
      updateReactive(cancelRequested.aggregateId.taskId)(_.cancelRequested(hostname))
    case started: Started =>
      updateReactive(started.aggregateId.taskId)(_.started(hostname))
    case completed: Completed =>
      updateReactive(completed.aggregateId.taskId)(_.completed(completed.additionalInformation.asJava))
    case failed: Failed =>
      updateReactive(failed.aggregateId.taskId)(_.failed(failed.additionalInformation.asJava))
    case canceled: Cancelled =>
      updateReactive(canceled.aggregateId.taskId)(_.cancelEffectively(canceled.additionalInformation.asJava))
    case updated: AdditionalInformationUpdated =>
      updateReactiveWith(updated.aggregateId.taskId)(_.updateInformation(updated.additionalInformation), (details, expectedStatus) => updateAdditionalInformationReactive(details, expectedStatus))
  }

  private def updateReactive(taskId: TaskId)(updater: TaskExecutionDetails => TaskExecutionDetails): Publisher[Void] =
    updateReactiveWith(taskId)(updater, (details, expectedStatus) => updateReactive(details, expectedStatus))

  private def updateReactiveWith(taskId: TaskId)(updater: TaskExecutionDetails => TaskExecutionDetails,
                                                 save: (TaskExecutionDetails, TaskManager.Status) => Publisher[java.lang.Boolean]): Publisher[Void] =
    SMono.fromPublisher(loadReactive(taskId))
      .flatMap(loaded => {
        val updated = updater(loaded)
        if (updated eq loaded) {
          // the event does not apply to the current status: nothing to save
          SMono.empty
        } else {
          SMono.fromPublisher(save(updated, loaded.getStatus))
            .filter(saved => !saved)
            // a concurrent update changed the status since the load: apply the updater again to the new details
            .flatMap(_ => SMono.fromPublisher(updateReactiveWith(taskId)(updater, save)))
        }
      })
      .`then`(SMono.empty)

  def load(taskId: TaskId): Option[TaskExecutionDetails]
  def list: List[TaskExecutionDetails]
  def update(details: TaskExecutionDetails): Unit

  def loadReactive(taskId: TaskId): Publisher[TaskExecutionDetails]

  def listReactive(): Publisher[TaskExecutionDetails]

  def listDetailsByBeforeDate(beforeDate: Instant): Publisher[TaskExecutionDetails]

  def updateReactive(details: TaskExecutionDetails): Publisher[Void]

  /**
   * Saves the details only if the stored ones still have the expected status.
   *
   * @return whether the details were saved
   */
  def updateReactive(details: TaskExecutionDetails, expectedStatus: TaskManager.Status): Publisher[java.lang.Boolean]

  /**
   * Saves the details carrying a progress update (new additional information) only if the stored ones still have the
   * expected status.
   *
   * @return whether the details were saved
   */
  def updateAdditionalInformationReactive(details: TaskExecutionDetails, expectedStatus: TaskManager.Status): Publisher[java.lang.Boolean] =
    updateReactive(details, expectedStatus)

  def remove(taskExecutionDetails: TaskExecutionDetails) : Publisher[Void]
}

class MemoryTaskExecutionDetailsProjection() extends TaskExecutionDetailsProjection {
  private[this] val details = new ConcurrentHashMap[TaskId, TaskExecutionDetails]

  override def load(taskId: TaskId): Option[TaskExecutionDetails] = Option(this.details.get(taskId))

  override def list: List[TaskExecutionDetails] = this.details.values().asScala.toList

  override def update(details: TaskExecutionDetails): Unit = this.details.put(details.taskId, details)


  override def loadReactive(taskId: TaskId): Publisher[TaskExecutionDetails] = SMono.fromCallable(() => this.details.get(taskId))

  override def listReactive(): Publisher[TaskExecutionDetails] = SFlux.fromIterable(this.details.values().asScala)

  override def updateReactive(details: TaskExecutionDetails): Publisher[Void] = SMono.fromCallable(() => this.details.put(details.taskId, details)).`then`(SMono.empty)

  override def updateReactive(details: TaskExecutionDetails, expectedStatus: TaskManager.Status): Publisher[java.lang.Boolean] =
    SMono.fromCallable(() => {
      val saved = new AtomicBoolean(false)
      this.details.computeIfPresent(details.taskId, (_, current) =>
        if (current.getStatus == expectedStatus) {
          saved.set(true)
          details
        } else {
          current
        })
      java.lang.Boolean.valueOf(saved.get())
    })

  override def listDetailsByBeforeDate(beforeDate: Instant): Publisher[TaskExecutionDetails] = SFlux.fromIterable(this.details.values().asScala)
    .filter(taskExecutionDetails => taskExecutionDetails.getSubmittedDate.toInstant.isBefore(beforeDate))

  override def remove(taskExecutionDetails: TaskExecutionDetails): Publisher[Void] =
    SMono.fromCallable(() => details.remove(taskExecutionDetails.taskId)).`then`(SMono.empty)

}
