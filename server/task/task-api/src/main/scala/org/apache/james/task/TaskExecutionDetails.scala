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

import com.google.common.base.MoreObjects
import org.apache.james.task.TaskManager.Status._

object TaskExecutionDetails {

  trait AdditionalInformation {}

  def from(task: Task, id: TaskId, hostname: Hostname) = new TaskExecutionDetails(id, task.`type`, WAITING, submittedDate = ZonedDateTime.now, submittedNode = hostname, () => task.details)
}

class TaskExecutionDetails(val taskId: TaskId,
                           private val `type`: TaskType,
                           private val status: TaskManager.Status,
                           private val submittedDate: ZonedDateTime,
                           private val submittedNode: Hostname,
                           private val additionalInformation: () => Optional[TaskExecutionDetails.AdditionalInformation],
                           private val startedDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val ranNode: Optional[Hostname] = Optional.empty(),
                           private val completedDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val canceledDate: Optional[ZonedDateTime] = Optional.empty(),
                           private val cancelRequestedNode: Optional[Hostname] = Optional.empty(),
                           private val failedDate: Optional[ZonedDateTime] = Optional.empty()) {
  def getTaskId: TaskId = taskId

  def getType: TaskType = `type`

  def getStatus: TaskManager.Status = status

  def getAdditionalInformation: Optional[TaskExecutionDetails.AdditionalInformation] = additionalInformation()

  def getSubmittedDate: ZonedDateTime = submittedDate

  def getSubmittedNode: Hostname = submittedNode

  def getStartedDate: Optional[ZonedDateTime] = startedDate

  def getRanNode: Optional[Hostname] = ranNode

  def getCompletedDate: Optional[ZonedDateTime] = completedDate

  def getCanceledDate: Optional[ZonedDateTime] = canceledDate

  def getCancelRequestedNode: Optional[Hostname] = cancelRequestedNode

  def getFailedDate: Optional[ZonedDateTime] = failedDate

  def started(hostname: Hostname): TaskExecutionDetails = status match {
    case WAITING => start(hostname)
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

  def cancelRequested(hostname: Hostname): TaskExecutionDetails = status match {
    case IN_PROGRESS => requestCancel(hostname)
    case WAITING => requestCancel(hostname)
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
        Objects.equals(submittedDate, that.submittedDate) &&
        Objects.equals(submittedNode, that.submittedNode) &&
        Objects.equals(startedDate, that.startedDate) &&
        Objects.equals(ranNode, that.ranNode) &&
        Objects.equals(completedDate, that.completedDate) &&
        Objects.equals(canceledDate, that.canceledDate) &&
        Objects.equals(cancelRequestedNode, that.cancelRequestedNode) &&
        Objects.equals(failedDate, that.failedDate)
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(taskId, `type`, additionalInformation(), status, submittedDate, submittedNode, startedDate, ranNode, completedDate, canceledDate, cancelRequestedNode, failedDate)

  override def toString: String =
    MoreObjects.toStringHelper(this)
      .add("taskId", taskId)
      .add("type", `type`)
      .add("additionalInformation", additionalInformation())
      .add("status", status)
      .add("submittedDate", submittedDate)
      .add("submittedNode", submittedNode)
      .add("startedDate", startedDate)
      .add("ranNode", ranNode)
      .add("completedDate", completedDate)
      .add("canceledDate", canceledDate)
      .add("cancelRequestedNode", cancelRequestedNode)
      .add("failedDate", failedDate)
      .toString

  private def start(hostname: Hostname) = new TaskExecutionDetails(taskId, `type`, IN_PROGRESS,
    submittedDate = submittedDate,
    submittedNode = submittedNode,
    additionalInformation = additionalInformation,
    startedDate = Optional.of(ZonedDateTime.now),
    ranNode = Optional.of(hostname))
  private def complete = new TaskExecutionDetails(taskId, `type`, TaskManager.Status.COMPLETED,
    submittedDate = submittedDate,
    submittedNode = submittedNode,
    additionalInformation = additionalInformation,
    startedDate = startedDate,
    ranNode = ranNode,
    cancelRequestedNode = cancelRequestedNode,
    completedDate = Optional.of(ZonedDateTime.now))
  private def fail = new TaskExecutionDetails(taskId, `type`, TaskManager.Status.FAILED,
    submittedDate = submittedDate,
    submittedNode = submittedNode,
    additionalInformation = additionalInformation,
    startedDate = startedDate,
    ranNode = ranNode,
    cancelRequestedNode = cancelRequestedNode,
    failedDate = Optional.of(ZonedDateTime.now))
  private def requestCancel(hostname: Hostname) = new TaskExecutionDetails(taskId, `type`, TaskManager.Status.CANCEL_REQUESTED,
    submittedDate = submittedDate,
    submittedNode = submittedNode,
    additionalInformation = additionalInformation,
    startedDate = startedDate,
    ranNode = ranNode,
    cancelRequestedNode = Optional.of(hostname),
    canceledDate = Optional.of(ZonedDateTime.now))
  private def cancel = new TaskExecutionDetails(taskId, `type`, TaskManager.Status.CANCELLED,
    submittedDate = submittedDate,
    submittedNode = submittedNode,
    additionalInformation = additionalInformation,
    startedDate = startedDate,
    ranNode = ranNode,
    cancelRequestedNode = cancelRequestedNode,
    canceledDate = Optional.of(ZonedDateTime.now))
}
