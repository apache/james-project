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

import com.google.common.base.Throwables
import org.apache.james.eventsourcing.EventSourcingSystem
import org.apache.james.task.Task.Result
import org.apache.james.task.eventsourcing.TaskCommand._
import org.apache.james.task.{TaskExecutionDetails, TaskId, TaskManagerWorker}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import java.util.Optional
import scala.compat.java8.OptionConverters._

case class WorkerStatusListener(eventSourcingSystem: EventSourcingSystem) extends TaskManagerWorker.Listener {

  override def started(taskId: TaskId): Publisher[Void] = SMono(eventSourcingSystem.dispatch(Start(taskId))).`then`()

  override def completed(taskId: TaskId, result: Result, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    SMono(eventSourcingSystem.dispatch(Complete(taskId, result, additionalInformation.asScala))).`then`()

  override def failed(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation], errorMessage: String, t: Throwable): Publisher[Void] =
    SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.asScala, Some(errorMessage), Some(Throwables.getStackTraceAsString(t))))).`then`()

  override def failed(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation], t: Throwable): Publisher[Void] =
    SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.asScala, None, Some(Throwables.getStackTraceAsString(t))))).`then`()

  override def failed(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.asScala, None, None))).`then`()

  override def cancelled(taskId: TaskId, additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    SMono(eventSourcingSystem.dispatch(Cancel(taskId, additionalInformation.asScala))).`then`()

  override def updated(taskId: TaskId, additionalInformation: TaskExecutionDetails.AdditionalInformation): Publisher[Void] =
    SMono(eventSourcingSystem.dispatch(UpdateAdditionalInformation(taskId, additionalInformation))).`then`()

  override def completed(taskId: TaskId, result: Result, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    optionAdditionalInformationPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Complete(taskId, result, additionalInformation))))
      .`then`()

  override def failed(taskId: TaskId, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation], errorMessage: String, t: Throwable): Publisher[Void] =
    optionAdditionalInformationPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation, Some(errorMessage), Some(Throwables.getStackTraceAsString(t))))))
      .`then`()

  override def failed(taskId: TaskId, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation], t: Throwable): Publisher[Void] =
    optionAdditionalInformationPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation, None, Some(Throwables.getStackTraceAsString(t))))))
      .`then`()

  override def failed(taskId: TaskId, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    optionAdditionalInformationPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation, None, None))))
      .`then`()

  override def cancelled(taskId: TaskId, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    optionAdditionalInformationPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Cancel(taskId, additionalInformation))))
      .`then`()

  override def updated(taskId: TaskId, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    SMono.fromPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(UpdateAdditionalInformation(taskId, additionalInformation))))
      .`then`()

  private def optionAdditionalInformationPublisher(additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation]): SMono[Option[TaskExecutionDetails.AdditionalInformation]] =
    SMono.fromPublisher(additionalInformationPublisher)
      .map(e => Option(e))
      .switchIfEmpty(SMono.just[Option[TaskExecutionDetails.AdditionalInformation]](None))
}