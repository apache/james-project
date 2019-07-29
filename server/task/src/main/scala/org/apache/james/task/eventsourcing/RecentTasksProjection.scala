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

import java.util.concurrent.ConcurrentLinkedDeque

import org.apache.james.eventsourcing.Subscriber
import org.apache.james.task.TaskId

trait RecentTasksProjection {
  def list(): List[TaskId]

  def asSubscriber: Subscriber = {
    case Created(aggregateId, _, _) => add(aggregateId.taskId)
    case _ =>
  }

  def add(taskId: TaskId): Unit
}

class MemoryRecentTasksProjection() extends RecentTasksProjection {

  import scala.collection.JavaConverters._

  private val tasks = new ConcurrentLinkedDeque[TaskId]

  override def list(): List[TaskId] = tasks.asScala.toList

  override def add(taskId: TaskId): Unit = tasks.add(taskId)
}
