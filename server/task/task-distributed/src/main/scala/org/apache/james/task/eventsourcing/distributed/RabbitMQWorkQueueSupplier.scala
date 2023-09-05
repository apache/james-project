/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.task.eventsourcing.distributed

import java.time.Duration
import com.google.common.annotations.VisibleForTesting

import javax.inject.Inject
import org.apache.james.backends.rabbitmq.{RabbitMQConfiguration, ReceiverProvider}
import org.apache.james.eventsourcing.EventSourcingSystem
import org.apache.james.server.task.json.JsonTaskSerializer
import org.apache.james.task.SerialTaskManagerWorker
import org.apache.james.task.eventsourcing.{WorkQueueSupplier, WorkerStatusListener}
import reactor.rabbitmq.Sender

class RabbitMQWorkQueueSupplier @Inject()(private val sender: Sender,
                                          private val receiverProvider: ReceiverProvider,
                                          private val jsonTaskSerializer: JsonTaskSerializer,
                                          private val cancelRequestName: CancelRequestQueueName,
                                          private val configuration: RabbitMQWorkQueueConfiguration,
                                          private val rabbitMQConfiguration: RabbitMQConfiguration) extends WorkQueueSupplier {

  val DEFAULT_ADDITIONAL_INFORMATION_POLLING_INTERVAL =  Duration.ofSeconds(30)
  override def apply(eventSourcingSystem: EventSourcingSystem): RabbitMQWorkQueue = {
     apply(eventSourcingSystem, DEFAULT_ADDITIONAL_INFORMATION_POLLING_INTERVAL)
  }

  @VisibleForTesting
  def apply(eventSourcingSystem: EventSourcingSystem, additionalInformationPollingInterval: Duration): RabbitMQWorkQueue = {
    val listener = WorkerStatusListener(eventSourcingSystem)
    val worker = new SerialTaskManagerWorker(listener, additionalInformationPollingInterval)
    val rabbitMQWorkQueue = new RabbitMQWorkQueue(worker, sender, receiverProvider, jsonTaskSerializer, configuration, cancelRequestName, rabbitMQConfiguration)
    rabbitMQWorkQueue
  }
}
