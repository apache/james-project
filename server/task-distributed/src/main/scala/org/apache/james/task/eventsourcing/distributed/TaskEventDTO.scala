/** **************************************************************
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
  * ***************************************************************/

package org.apache.james.task.eventsourcing.distributed

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.james.eventsourcing.EventId
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO
import org.apache.james.server.task.json.JsonTaskSerializer
import org.apache.james.task.eventsourcing._
import org.apache.james.task.{Hostname, Task, TaskId}

sealed abstract class TaskEventDTO(val getType: String, val getAggregate: String, val getEvent: Int) extends EventDTO {
  protected def domainAggregateId: TaskAggregateId = TaskAggregateId(TaskId.fromString(getAggregate))
  protected def domainEventId: EventId = EventId.fromSerialized(getEvent)
}

case class CreatedDTO(@JsonProperty("type") typeName: String,
                      @JsonProperty("aggregate") aggregateId: String,
                      @JsonProperty("event") eventId: Int,
                      @JsonProperty("task") getTask: String,
                      @JsonProperty("hostname") getHostname: String)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject(serializer: JsonTaskSerializer): Created = Created(domainAggregateId, domainEventId, serializer.deserialize(getTask), Hostname(getHostname))
}

object CreatedDTO {
  def fromDomainObject(event: Created, typeName: String, serializer: JsonTaskSerializer): CreatedDTO =
    CreatedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize(), serializer.serialize(event.task), event.hostname.asString)
}

case class StartedDTO(@JsonProperty("type") typeName: String,
                      @JsonProperty("aggregate") aggregateId: String,
                      @JsonProperty("event") eventId: Int,
                      @JsonProperty("hostname") getHostname: String)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject: Started = Started(domainAggregateId, domainEventId, Hostname(getHostname))
}

object StartedDTO {
  def fromDomainObject(event: Started, typeName: String): StartedDTO =
    StartedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize(), event.hostname.asString)
}

case class CancelRequestedDTO(@JsonProperty("type") typeName: String,
                              @JsonProperty("aggregate") aggregateId: String,
                              @JsonProperty("event") eventId: Int,
                              @JsonProperty("hostname") getHostname: String)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject: CancelRequested = CancelRequested(domainAggregateId, domainEventId, Hostname(getHostname))
}

object CancelRequestedDTO {
  def fromDomainObject(event: CancelRequested, typeName: String): CancelRequestedDTO =
    CancelRequestedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize(), event.hostname.asString)
}

case class CompletedDTO(@JsonProperty("type") typeName: String,
                        @JsonProperty("aggregate") aggregateId: String,
                        @JsonProperty("event") eventId: Int,
                        @JsonProperty("result") getResult: String)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject: Completed = Completed(domainAggregateId, domainEventId, domainResult)
  private def domainResult: Task.Result = getResult match {
    case "COMPLETED" => Task.Result.COMPLETED
    case "PARTIAL" => Task.Result.PARTIAL
  }
}

object CompletedDTO {
  def fromDomainObject(event: Completed, typeName: String): CompletedDTO =
    CompletedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize(), resultToString(event.result))

  private def resultToString(result: Task.Result): String = result match {
    case Task.Result.COMPLETED => "COMPLETED"
    case Task.Result.PARTIAL => "PARTIAL"
  }
}

case class FailedDTO(@JsonProperty("type") typeName: String,
                     @JsonProperty("aggregate") aggregateId: String,
                     @JsonProperty("event") eventId: Int)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject: Failed = Failed(domainAggregateId, domainEventId)
}

object FailedDTO {
  def fromDomainObject(event: Failed, typeName: String): FailedDTO =
    FailedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize())
}

case class CancelledDTO(@JsonProperty("type") typeName: String,
                        @JsonProperty("aggregate") aggregateId: String,
                        @JsonProperty("event") eventId: Int)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject: Cancelled = Cancelled(domainAggregateId, domainEventId)
}

object CancelledDTO {
  def fromDomainObject(event: Cancelled, typeName: String): CancelledDTO =
    CancelledDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize())
}
