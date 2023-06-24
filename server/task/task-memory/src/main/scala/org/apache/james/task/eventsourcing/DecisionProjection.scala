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

import java.time.Instant

import org.apache.james.eventsourcing.Event
import org.apache.james.task.TaskManager.Status

case class DecisionProjection(status: Status, latestUpdateAdditionalInformationUpdate : Option[Instant]) {
  def update(event: Event): DecisionProjection = {
      event match {
        case _: Created => this
        case _: Started => DecisionProjection(Status.IN_PROGRESS, None)
        case _: CancelRequested => DecisionProjection(Status.CANCEL_REQUESTED, latestUpdateAdditionalInformationUpdate)
        case event: Cancelled => DecisionProjection(Status.CANCELLED, event.additionalInformation.map(_.timestamp))
        case event: Completed => DecisionProjection(Status.COMPLETED, event.additionalInformation.map(_.timestamp))
        case event: Failed => DecisionProjection(Status.FAILED, event.additionalInformation.map(_.timestamp))
        case event: AdditionalInformationUpdated => DecisionProjection(Status.IN_PROGRESS, Some(event.additionalInformation.timestamp))
      }
  }

  def additionalInformationIsOlderThan(timestamp: Instant) : Boolean = latestUpdateAdditionalInformationUpdate.forall(timestamp.isAfter)

}

object DecisionProjection {
  def initial(taskEvent : TaskEvent): DecisionProjection = {
    taskEvent match {
      case _: Created => DecisionProjection(Status.WAITING, None)
      case updated: AdditionalInformationUpdated => DecisionProjection(Status.IN_PROGRESS, Some(updated.additionalInformation.timestamp))
      case _ => throw new NotImplementedError()
    }
  }
}

