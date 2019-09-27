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
package org.apache.james.task.eventsourcing.cassandra

import java.util.Optional

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, insertInto, select}
import com.datastax.driver.core.{Row, Session}
import javax.inject.Inject
import org.apache.james.backends.cassandra.init.{CassandraTypesProvider, CassandraZonedDateTimeModule}
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionTable._
import org.apache.james.task.{Hostname, TaskExecutionDetails, TaskId, TaskManager, TaskType}
import reactor.core.publisher.{Flux, Mono}

@Inject
class CassandraTaskExecutionDetailsProjectionDAO(session: Session, typesProvider: CassandraTypesProvider) {

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
    .value(FAILED_DATE, bindMarker(FAILED_DATE)))

  private val selectStatement = session.prepare(select().from(TABLE_NAME)
    .where(QueryBuilder.eq(TASK_ID, bindMarker(TASK_ID))))

  private val listStatement = session.prepare(select().from(TABLE_NAME))

  def saveDetails(details : TaskExecutionDetails): Mono[Void] = cassandraAsyncExecutor.executeVoid(
    insertStatement.bind
      .setUUID(TASK_ID, details.getTaskId.getValue)
      .setString(TYPE, details.getType.asString())
      .setString(STATUS, details.getStatus.getValue)
      .setUDTValue(SUBMITTED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getSubmittedDate))
      .setString(SUBMITTED_NODE, details.getSubmittedNode.asString)
      .setUDTValue(STARTED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getStartedDate).orElse(null))
      .setString(RAN_NODE, details.getRanNode.map[String](_.asString).orElse(null))
      .setUDTValue(COMPLETED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getCompletedDate).orElse(null))
      .setUDTValue(CANCELED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getCanceledDate).orElse(null))
      .setString(CANCEL_REQUESTED_NODE, details.getCancelRequestedNode.map[String](_.asString).orElse(null))
      .setUDTValue(FAILED_DATE, CassandraZonedDateTimeModule.toUDT(dateType, details.getStartedDate).orElse(null)))

  def readDetails(taskId: TaskId): Mono[TaskExecutionDetails] = cassandraAsyncExecutor
    .executeSingleRow(selectStatement.bind().setUUID(TASK_ID, taskId.getValue))
    .map(readRow)

  def listDetails(): Flux[TaskExecutionDetails] = cassandraAsyncExecutor
    .executeRows(listStatement.bind())
    .map(readRow)

  private def readRow(row: Row): TaskExecutionDetails = new TaskExecutionDetails(
    taskId = TaskId.fromUUID(row.getUUID(TASK_ID)),
    `type` = TaskType.of(row.getString(TYPE)),
    status = TaskManager.Status.fromString(row.getString(STATUS)),
    submittedDate = CassandraZonedDateTimeModule.fromUDT(row.getUDTValue(SUBMITTED_DATE)),
    submittedNode = Hostname(row.getString(SUBMITTED_NODE)),
    startedDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUDTValue(STARTED_DATE)),
    ranNode = Optional.ofNullable(row.getString(RAN_NODE)).map(Hostname(_)),
    completedDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUDTValue(COMPLETED_DATE)),
    canceledDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUDTValue(CANCELED_DATE)),
    cancelRequestedNode = Optional.ofNullable(row.getString(CANCEL_REQUESTED_NODE)).map(Hostname(_)),
    failedDate = CassandraZonedDateTimeModule.fromUDTOptional(row.getUDTValue(FAILED_DATE)),
    additionalInformation = Optional.empty)
}
