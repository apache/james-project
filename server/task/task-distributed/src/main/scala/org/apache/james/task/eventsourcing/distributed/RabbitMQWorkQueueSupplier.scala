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
package org.apache.james.task.eventsourcing.distributed

import javax.inject.Inject

import org.apache.james.backends.rabbitmq.SimpleConnectionPool
import org.apache.james.eventsourcing.EventSourcingSystem
import org.apache.james.server.task.json.JsonTaskSerializer
import org.apache.james.task.eventsourcing.{WorkQueueSupplier, WorkerStatusListener}
import org.apache.james.task.{SerialTaskManagerWorker, WorkQueue}

@Inject
class RabbitMQWorkQueueSupplier(private val rabbitMQConnectionPool: SimpleConnectionPool,
                                private val jsonTaskSerializer: JsonTaskSerializer) extends WorkQueueSupplier {
  override def apply(eventSourcingSystem: EventSourcingSystem): WorkQueue = {
    val listener = WorkerStatusListener(eventSourcingSystem)
    val worker = new SerialTaskManagerWorker(listener)
    val rabbitMQWorkQueue = new RabbitMQWorkQueue(worker, rabbitMQConnectionPool, jsonTaskSerializer)
    rabbitMQWorkQueue.start()
    rabbitMQWorkQueue
  }
}
