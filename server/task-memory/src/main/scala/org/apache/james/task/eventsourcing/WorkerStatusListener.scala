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

import org.apache.james.eventsourcing.EventSourcingSystem
import org.apache.james.task.Task.Result
import org.apache.james.task.eventsourcing.TaskCommand._
import org.apache.james.task.{TaskId, TaskManagerWorker}

case class WorkerStatusListener(eventSourcingSystem: EventSourcingSystem) extends TaskManagerWorker.Listener {

  override def started(taskId: TaskId): Unit = eventSourcingSystem.dispatch(Start(taskId))

  override def completed(taskId: TaskId, result: Result): Unit = eventSourcingSystem.dispatch(Complete(taskId, result))

  override def failed(taskId: TaskId, t: Throwable): Unit = eventSourcingSystem.dispatch(Fail(taskId))

  override def failed(taskId: TaskId): Unit = eventSourcingSystem.dispatch(Fail(taskId))

  override def cancelled(taskId: TaskId): Unit = eventSourcingSystem.dispatch(Cancel(taskId))
}