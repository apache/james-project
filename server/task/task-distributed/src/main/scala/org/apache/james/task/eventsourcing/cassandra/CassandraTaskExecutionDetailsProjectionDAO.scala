 /***************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.task.eventsourcing.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{BoundStatement, Row}
import com.datastax.oss.driver.api.core.data.UdtValue
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import org.apache.james.backends.cassandra.init.{CassandraTypesProvider, CassandraZonedDateTimeModule}
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer
import org.apache.james.task._
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionTable._
import org.apache.james.util.ReactorUtils
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.SMono

import java.time.Instant
import java.util.Optional
import jakarta.inject.Inject
import scala.compat.java8.OptionConverters._

class CassandraTaskExecutionDetailsProjectionDAO @Inject()(session: CqlSession, typesProvider: CassandraTypesProvider, jsonTaskAdditionalInformationSerializer: JsonTaskAdditionalInformationSerializer) {
  private val cassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  private val dateType = typesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME)

  private val insertStatement = session.prepare(insertInto(TABLE_NAME)
    .value(TASK_ID, bindMarker(TASK_ID))
    .value(TYPE, bindMarker(TYPE))
    .value(STATUS, bindMarker(STATUS))
    .value(SUBMITTED_DATE, bindMarker(SUBMITTED_DATE))
    .value(SUBMITTED_NODE, bindMarker(SUBMITTED_NODE))
    .value(STARTED_DATE, bindMarker(STARTED_DATE))
    .value(RAN_NODE, bindMarker(RAN_NODE))
    .value(COMPLETED_DATE, bindMarker(COMPLETED_DATE))
    .value(CANCELED_DATE, bindMarker(CANCELED_DATE))
    .value(CANCEL_REQUESTED_NODE, bindMarker(CANCEL_REQUESTED_NODE))
    .value(FAILED_DATE, bindMarker(FAILED_DATE))
    .value(ADDITIONAL_INFORMATION, bindMarker(ADDITIONAL_INFORMATION))
    .build())

  private val selectStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(TASK_ID).isEqualTo(bindMarker(TASK_ID))
    .build())

  private val listStatement = session.prepare(selectFrom(TABLE_NAME).all().build())


  private val removeStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(TASK_ID).isEqualTo(bindMarker(TASK_ID))
    .build())

  def saveDetails(details: TaskExecutionDetails): Mono[Void] =
    Mono.from(serializeAdditionalInformation(details)
      .flatMap(serializeAdditionalInformation => {
    val boundStatement =  insertStatement.bind()
      .setUuid(TASK_ID, details.getTaskId.getValue)
      .setString(TYPE, details.getType.asString())
      .setString(STATUS, details.getStatus.getValue)
      .setUdtValue(SUBMITTED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getSubmittedDate))
      .setString(SUBMITTED_NODE, details.getSubmittedNode.asString)

    val bindOptionalFieldOperations = List(
      (statement: BoundStatement) => bindOptionalUDTValue(statement, STARTED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getStartedDate)),
      (statement: BoundStatement) => bindOptionalStringValue(statement, RAN_NODE, details.getRanNode.map[String](_.asString)),
      (statement: BoundStatement) => bindOptionalUDTValue(statement, COMPLETED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getCompletedDate)),
      (statement: BoundStatement) => bindOptionalUDTValue(statement, CANCELED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getCanceledDate)),
      (statement: BoundStatement) => bindOptionalStringValue(statement, CANCEL_REQUESTED_NODE, details.getCancelRequestedNode.map[String](_.asString)),
      (statement: BoundStatement) => bindOptionalUDTValue(statement, FAILED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getFailedDate)),
      (statement: BoundStatement) => bindOptionalStringValue(statement, ADDITIONAL_INFORMATION, serializeAdditionalInformation),
    )

    val fullyBoundStatement = bindOptionalFieldOperations.foldLeft(boundStatement)((statement, bindFieldOperation) => {
      bindFieldOperation(statement)
    })

    SMono(cassandraAsyncExecutor.executeVoid(fullyBoundStatement))
  }))

  private def bindOptionalStringValue(statement: BoundStatement, fieldName: String, fieldValue: Optional[String]) =
    fieldValue.asScala match {
      case Some(value) => statement.setString(fieldName, value)
      case None => statement
    }

  private def bindOptionalUDTValue(statement: BoundStatement, fieldName: String, fieldValue: Optional[UdtValue]) =
    fieldValue.asScala match {
      case Some(value) => statement.setUdtValue(fieldName, value)
      case None => statement
    }

  private def serializeAdditionalInformation(details: TaskExecutionDetails): SMono[Optional[String]] = SMono.fromCallable(() =>details
    .getAdditionalInformation
    .map(jsonTaskAdditionalInformationSerializer.serialize(_)))
    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  def readDetails(taskId: TaskId): Mono[TaskExecutionDetails] = cassandraAsyncExecutor
    .executeSingleRow(selectStatement.bind().setUuid(TASK_ID, taskId.getValue))
    .map(readRow)

  def listDetails(): Flux[TaskExecutionDetails] = cassandraAsyncExecutor
    .executeRows(listStatement.bind())
    .map(readRow)

  def listDetailsByBeforeDate(beforeDate: Instant): Flux[TaskExecutionDetails] =
    listDetails()
      .filter(detail => detail.getSubmittedDate.toInstant.isBefore(beforeDate))

  def remove(details: TaskExecutionDetails) : Mono[Void] =
    cassandraAsyncExecutor.executeVoid(removeStatement.bind()
      .setUuid(TASK_ID, details.getTaskId.getValue))

  private def readRow(row: Row): TaskExecutionDetails = {
    val taskType = TaskType.of(row.getString(TYPE))
    new TaskExecutionDetails(
      taskId = TaskId.fromUUID(row.getUuid(TASK_ID)),
      `type` = TaskType.of(row.getString(TYPE)),
      status = TaskManager.Status.fromString(row.getString(STATUS)),
      submittedDate = CassandraZonedDateTimeModule.fromUDT(row.getUdtValue(SUBMITTED_DATE)),
      submittedNode = Hostname(row.getString(SUBMITTED_NODE)),
      startedDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUdtValue(STARTED_DATE)),
      ranNode = Optional.ofNullable(row.getString(RAN_NODE)).map(Hostname(_)),
      completedDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUdtValue(COMPLETED_DATE)),
      canceledDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUdtValue(CANCELED_DATE)),
      cancelRequestedNode = Optional.ofNullable(row.getString(CANCEL_REQUESTED_NODE)).map(Hostname(_)),
      failedDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUdtValue(FAILED_DATE)),
      additionalInformation = () => deserializeAdditionalInformation(taskType, row))
  }

  private def deserializeAdditionalInformation(taskType: TaskType, row: Row): Optional[TaskExecutionDetails.AdditionalInformation] =
    Optional.ofNullable(row.getString(ADDITIONAL_INFORMATION))
      .map(additionalInformation => jsonTaskAdditionalInformationSerializer.deserialize(additionalInformation))
}
