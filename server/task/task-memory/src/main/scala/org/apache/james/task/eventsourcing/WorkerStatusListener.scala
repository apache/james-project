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

import java.util.Optional

import com.google.common.base.Throwables
import org.apache.james.eventsourcing.EventSourcingSystem
import org.apache.james.task.Task.Result
import org.apache.james.task.eventsourcing.TaskCommand._
import org.apache.james.task.{TaskExecutionDetails, TaskId, TaskManagerWorker}
import org.reactivestreams.Publisher

import reactor.core.scala.publisher.SMono

import scala.compat.java8.OptionConverters._

case class WorkerStatusListener(eventSourcingSystem: EventSourcingSystem) extends TaskManagerWorker.Listener {

  override def started(taskId: TaskId): Publisher[Void] = eventSourcingSystem.dispatch(Start(taskId))

  override def completed(taskId: TaskId, result: Result, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    eventSourcingSystem.dispatch(Complete(taskId, result, additionalInformation.asScala))

  override def failed(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation], errorMessage: String, t: Throwable): Publisher[Void] =
    eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.asScala, Some(errorMessage), Some(Throwables.getStackTraceAsString(t))))

  override def failed(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation], t: Throwable): Publisher[Void] =
    eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.asScala, None, Some(Throwables.getStackTraceAsString(t))))

  override def failed(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.asScala, None, None))

  override def cancelled(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    eventSourcingSystem.dispatch(Cancel(taskId, additionalInformation.asScala ))

  override def updated(taskId: TaskId, additionalInformation: TaskExecutionDetails.AdditionalInformation): Publisher[Void] =
    eventSourcingSystem.dispatch(UpdateAdditionalInformation(taskId, additionalInformation))
}