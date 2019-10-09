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

import org.apache.james.eventsourcing.Event
import org.apache.james.task.TaskManager.Status

case class DecisionProjection(status: Option[Status]) {
  def update(event: Event): DecisionProjection = {
    DecisionProjection(
      event match {
        case event: Created => Some(Status.WAITING)
        case event: Started => Some(Status.IN_PROGRESS)
        case event: CancelRequested => Some(Status.CANCEL_REQUESTED)
        case event: Cancelled => Some(Status.CANCELLED)
        case event: Completed => Some(Status.COMPLETED)
        case event: Failed => Some(Status.FAILED)
        case event: AdditionalInformationUpdated => status
      }
    )
  }

}

object DecisionProjection {
  def empty: DecisionProjection = DecisionProjection(None)
}

