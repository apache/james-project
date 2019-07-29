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

package org.apache.james.task

import java.time.ZonedDateTime
import java.util.{Objects, Optional}

import org.apache.james.task.TaskManager.Status._

import com.google.common.base.MoreObjects

object TaskExecutionDetails {

  trait AdditionalInformation {}

  def from(task: Task, id: TaskId) = new TaskExecutionDetails(id, task.`type`, () => task.details, WAITING, submitDate = Optional.of(ZonedDateTime.now))
}

class TaskExecutionDetails(val taskId: TaskId,
                           private val `type`: String,
                           private val additionalInformation: () => Optional[TaskExecutionDetails.AdditionalInformation],
                           private val status: TaskManager.Status,
                           private val submitDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val startedDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val completedDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val canceledDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val failedDate: Optional[ZonedDateTime] = Optional.empty()) {
  def getTaskId: TaskId = taskId

  def getType: String = `type`

  def getStatus: TaskManager.Status = status

  def getAdditionalInformation: Optional[TaskExecutionDetails.AdditionalInformation] = additionalInformation()

  def getSubmitDate: Optional[ZonedDateTime] = submitDate

  def getStartedDate: Optional[ZonedDateTime] = startedDate

  def getCompletedDate: Optional[ZonedDateTime] = completedDate

  def getCanceledDate: Optional[ZonedDateTime] = canceledDate

  def getFailedDate: Optional[ZonedDateTime] = failedDate

  def started: TaskExecutionDetails = status match {
    case WAITING => start
    case _ => this
  }

  def completed: TaskExecutionDetails = status match {
    case IN_PROGRESS => complete
    case CANCEL_REQUESTED => complete
    case WAITING => complete
    case _ => this
  }

  def failed: TaskExecutionDetails = status match {
    case IN_PROGRESS => fail
    case CANCEL_REQUESTED => fail
    case _ => this
  }

  def cancelRequested: TaskExecutionDetails = status match {
    case IN_PROGRESS => requestCancel
    case WAITING => requestCancel
    case _ => this
  }

  def cancelEffectively: TaskExecutionDetails = status match {
    case CANCEL_REQUESTED => cancel
    case IN_PROGRESS => cancel
    case WAITING => cancel
    case _ => this
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[TaskExecutionDetails]

  override def equals(other: Any): Boolean = other match {
    case that: TaskExecutionDetails =>
      (that canEqual this) &&
        Objects.equals(taskId, that.taskId) &&
        Objects.equals(`type`, that.`type`) &&
        Objects.equals(additionalInformation(), that.additionalInformation()) &&
        Objects.equals(status, that.status) &&
        Objects.equals(submitDate, that.submitDate) &&
        Objects.equals(startedDate, that.startedDate) &&
        Objects.equals(completedDate, that.completedDate) &&
        Objects.equals(canceledDate, that.canceledDate) &&
        Objects.equals(failedDate, that.failedDate)
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(taskId, `type`, additionalInformation(), status, submitDate, startedDate, completedDate, canceledDate, failedDate)

  override def toString: String =
    MoreObjects.toStringHelper(this)
      .add("taskId", taskId)
      .add("type", `type`)
      .add("", additionalInformation())
      .add("", status)
      .add("", submitDate)
      .add("", startedDate)
      .add("", completedDate)
      .add("", canceledDate)
      .add("", failedDate)
      .toString

  private def start = new TaskExecutionDetails(taskId, `type`, additionalInformation, IN_PROGRESS,
    submitDate = submitDate,
    startedDate = Optional.of(ZonedDateTime.now))
  private def complete = new TaskExecutionDetails(taskId, `type`, additionalInformation, TaskManager.Status.COMPLETED,
    submitDate = submitDate,
    startedDate = startedDate,
    completedDate = Optional.of(ZonedDateTime.now))
  private def fail = new TaskExecutionDetails(taskId, `type`, additionalInformation, TaskManager.Status.FAILED,
    submitDate = submitDate,
    startedDate = startedDate,
    failedDate = Optional.of(ZonedDateTime.now))
  private def requestCancel = new TaskExecutionDetails(taskId, `type`, additionalInformation, TaskManager.Status.CANCEL_REQUESTED,
    submitDate = submitDate,
    startedDate = startedDate,
    canceledDate = Optional.of(ZonedDateTime.now))
  private def cancel = new TaskExecutionDetails(taskId, `type`, additionalInformation, TaskManager.Status.CANCELLED,
    submitDate = submitDate,
    startedDate = startedDate,
    canceledDate = Optional.of(ZonedDateTime.now))
}
