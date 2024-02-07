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

import java.util.Optional

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.james.eventsourcing.EventId
import org.apache.james.eventsourcing.eventstore.dto.EventDTO
import org.apache.james.json.DTOConverter
import org.apache.james.server.task.json.dto.{AdditionalInformationDTO, TaskDTO}
import org.apache.james.task.TaskExecutionDetails.AdditionalInformation
import org.apache.james.task.eventsourcing._
import org.apache.james.task.eventsourcing.distributed.distributed.{AdditionalInformationConverter, TaskConverter}
import org.apache.james.task.{Hostname, Task, TaskExecutionDetails, TaskId}

import scala.compat.java8.OptionConverters._

package object distributed {
  type AdditionalInformationConverter = DTOConverter[TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO]
  type TaskConverter = DTOConverter[Task, TaskDTO]
}

class NestedTaskDTOSerializerNotFound(val domainObject: Task) extends RuntimeException("Unable to find a serializer for " + domainObject) { }

class NestedTaskDTODeserializerNotFound(val dto: TaskDTO) extends RuntimeException("Unable to find a deserializer for " + dto) { }

class NestedAdditionalInformationDTODeserializerNotFound(val dto: AdditionalInformationDTO) extends RuntimeException("Unable to find a deserializer for " + dto) { }

class NestedAdditionalInformationDTOSerializerNotFound(val domainObject: AdditionalInformation) extends RuntimeException("Unable to find a serializer for " + domainObject) { }

sealed abstract class TaskEventDTO(val getType: String, val getAggregate: String, val getEvent: Int) extends EventDTO {
  protected def domainAggregateId: TaskAggregateId = TaskAggregateId(TaskId.fromString(getAggregate))
  protected def domainEventId: EventId = EventId.fromSerialized(getEvent)

}

case class CreatedDTO(@JsonProperty("type") typeName: String,
                      @JsonProperty("aggregate") aggregateId: String,
                      @JsonProperty("event") eventId: Int,
                      @JsonProperty("task") getTask: TaskDTO,
                      @JsonProperty("hostname") getHostname: String)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject(taskConverter: TaskConverter): Created = {
    val task: Task = taskConverter.toDomainObject(getTask).orElseThrow(() => new NestedTaskDTODeserializerNotFound(getTask))
    Created(domainAggregateId, domainEventId, task, Hostname(getHostname))
  }
}

object CreatedDTO {
  def fromDomainObject(taskConverter: TaskConverter)(event: Created, typeName: String): CreatedDTO = {
    val taskDTO = taskConverter.toDTO(event.task).orElseThrow(() => new NestedTaskDTOSerializerNotFound(event.task))
    CreatedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, taskDTO, event.hostname.asString)
  }
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
    StartedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, event.hostname.asString)
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
    CancelRequestedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, event.hostname.asString)
}

case class CompletedDTO(@JsonProperty("type") typeName: String,
                        @JsonProperty("aggregate") aggregateId: String,
                        @JsonProperty("event") eventId: Int,
                        @JsonProperty("result") getResult: String,
                        @JsonProperty("additionalInformation") getAdditionalInformation: Optional[AdditionalInformationDTO])
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject(additionalInformationConverter: AdditionalInformationConverter): Completed = {
    val additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation] = getAdditionalInformation
      .map(dto => additionalInformationConverter.toDomainObject(dto).orElseThrow(() => new NestedAdditionalInformationDTODeserializerNotFound(dto)))
    Completed(domainAggregateId, domainEventId, domainResult, additionalInformation.asScala)
  }
  private def domainResult: Task.Result = getResult match {
    case "COMPLETED" => Task.Result.COMPLETED
    case "PARTIAL" => Task.Result.PARTIAL
  }
}

object CompletedDTO {
  def fromDomainObject(dtoConverter: AdditionalInformationConverter)(event: Completed, typeName: String): CompletedDTO = {
    val additionalInformationDTO: Optional[AdditionalInformationDTO] = event
      .additionalInformation
      .asJava
      .map(domainObject => dtoConverter.toDTO(domainObject).orElseThrow(() => new NestedAdditionalInformationDTOSerializerNotFound(domainObject)))
    CompletedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, resultToString(event.result), additionalInformationDTO)
  }

  private def resultToString(result: Task.Result): String = result match {
    case Task.Result.COMPLETED => "COMPLETED"
    case Task.Result.PARTIAL => "PARTIAL"
  }
}

case class FailedDTO(@JsonProperty("type") typeName: String,
                     @JsonProperty("aggregate") aggregateId: String,
                     @JsonProperty("event") eventId: Int,
                     @JsonProperty("additionalInformation") getAdditionalInformation: Optional[AdditionalInformationDTO],
                     @JsonProperty("errorMessage") getErrorMessage: Optional[String],
                     @JsonProperty("exception") getException: Optional[String])
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject(additionalInformationConverter: AdditionalInformationConverter): Failed = {
    val additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation] = getAdditionalInformation
      .map(dto => additionalInformationConverter.toDomainObject(dto).orElseThrow(() => new NestedAdditionalInformationDTODeserializerNotFound(dto)))
    Failed(domainAggregateId, domainEventId, additionalInformation.asScala, getErrorMessage.asScala, getException.asScala)
  }
}

object FailedDTO {
  def fromDomainObject(dtoConverter: AdditionalInformationConverter)(event: Failed, typeName: String): FailedDTO = {
    val additionalInformationDTO: Optional[AdditionalInformationDTO] = event
      .additionalInformation
      .asJava.map(domainObject => dtoConverter.toDTO(domainObject).orElseThrow(() => new NestedAdditionalInformationDTOSerializerNotFound(domainObject)))
    FailedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, additionalInformationDTO, event.errorMessage.asJava, event.exception.asJava)
  }
}

case class CancelledDTO(@JsonProperty("type") typeName: String,
                        @JsonProperty("aggregate") aggregateId: String,
                        @JsonProperty("event") eventId: Int,
                        @JsonProperty("additionalInformation") getAdditionalInformation: Optional[AdditionalInformationDTO])
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject(additionalInformationConverter: AdditionalInformationConverter): Cancelled = {
    val additionalInformation: Optional[TaskExecutionDetails.AdditionalInformation] = getAdditionalInformation
      .map(dto => additionalInformationConverter.toDomainObject(dto).orElseThrow(() => new NestedAdditionalInformationDTODeserializerNotFound(dto)))
    Cancelled(domainAggregateId, domainEventId, additionalInformation.asScala)
  }
}

object CancelledDTO {
  def fromDomainObject(additionalInformationConverter: AdditionalInformationConverter)(event: Cancelled, typeName: String): CancelledDTO = {
    val additionalInformationDTO: Optional[AdditionalInformationDTO] = event
      .additionalInformation
      .asJava
      .map(domainObject => additionalInformationConverter.toDTO(domainObject).orElseThrow(() => new NestedAdditionalInformationDTOSerializerNotFound(domainObject)))
    CancelledDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, additionalInformationDTO)
  }
}

case class AdditionalInformationUpdatedDTO(@JsonProperty("type") typeName: String,
                     @JsonProperty("aggregate") aggregateId: String,
                     @JsonProperty("event") eventId: Int,
                     @JsonProperty("additionalInformation") getAdditionalInformation: AdditionalInformationDTO)
  extends TaskEventDTO(typeName, aggregateId, eventId) {
  def toDomainObject(additionalInformationConverter: AdditionalInformationConverter): AdditionalInformationUpdated = {
    val additionalInformation = additionalInformationConverter
        .toDomainObject(getAdditionalInformation)
        .orElseThrow(() => new NestedAdditionalInformationDTODeserializerNotFound(getAdditionalInformation))
    AdditionalInformationUpdated(domainAggregateId, domainEventId, additionalInformation)
  }
}

object AdditionalInformationUpdatedDTO {
  def fromDomainObject(additionalInformationConverter: AdditionalInformationConverter)(event: AdditionalInformationUpdated, typeName: String): AdditionalInformationUpdatedDTO = {
    val additionalInformationDTO = additionalInformationConverter
        .toDTO(event.additionalInformation)
        .orElseThrow(() => new NestedAdditionalInformationDTOSerializerNotFound(event.additionalInformation))
    AdditionalInformationUpdatedDTO(typeName, event.aggregateId.taskId.asString(), event.eventId.serialize, additionalInformationDTO)
  }
}
