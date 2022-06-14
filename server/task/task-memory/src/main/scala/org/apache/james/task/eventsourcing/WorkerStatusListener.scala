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
import scala.jdk.OptionConverters._

case class WorkerStatusListener(eventSourcingSystem: EventSourcingSystem) extends TaskManagerWorker.Listener {

  override def started(taskId: TaskId): Publisher[Void] = SMono(eventSourcingSystem.dispatch(Start(taskId))).`then`()

  override def completed(taskId: TaskId, result: Result, additionalInformationPublisher: Publisher[Optional[TaskExecutionDetails.AdditionalInformation]]): Publisher[Void] =
    SMono.fromPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Complete(taskId, result, additionalInformation.toScala))))
      .`then`()

  override def failed(taskId: TaskId, additionalInformationPublisher: Publisher[Optional[TaskExecutionDetails.AdditionalInformation]], errorMessage: Optional[String], t: Optional[Throwable]): Publisher[Void] =
    SMono.fromPublisher(additionalInformationPublisher)
    .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Fail(taskId, additionalInformation.toScala, errorMessage.toScala, t.toScala.map(t => Throwables.getStackTraceAsString(t))))))
    .`then`()

  override def cancelled(taskId: TaskId, additionalInformationPublisher: Publisher[Optional[TaskExecutionDetails.AdditionalInformation]]): Publisher[Void] =
    SMono.fromPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(Cancel(taskId, additionalInformation.toScala))))
      .`then`()

  override def updated(taskId: TaskId, additionalInformationPublisher: Publisher[TaskExecutionDetails.AdditionalInformation]): Publisher[Void] =
    SMono.fromPublisher(additionalInformationPublisher)
      .flatMap(additionalInformation => SMono(eventSourcingSystem.dispatch(UpdateAdditionalInformation(taskId, additionalInformation))))
      .`then`()

}