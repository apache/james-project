/****************************************************************
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

package org.apache.james.task.eventsourcing.postgres

import java.time.{Instant, LocalDateTime}
import java.util.Optional

import com.google.common.collect.ImmutableMap
import javax.inject.Inject
import org.apache.james.backends.postgres.PostgresCommons.{LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME, INSTANT_TO_LOCAL_DATE_TIME}
import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer
import org.apache.james.task._
import org.apache.james.task.eventsourcing.postgres.PostgresTaskExecutionDetailsProjectionModule._
import org.apache.james.util.ReactorUtils
import org.jooq.JSONB.jsonb
import org.jooq.{InsertQuery, Record}
import reactor.core.publisher.{Flux, Mono}

class PostgresTaskExecutionDetailsProjectionDAO @Inject()(postgresExecutor: PostgresExecutor, jsonTaskAdditionalInformationSerializer: JsonTaskAdditionalInformationSerializer) {

  def saveDetails(details: TaskExecutionDetails): Mono[Void] =
    Mono.from(serializeAdditionalInformation(details)
      .flatMap(serializedAdditionalInformation => postgresExecutor.executeVoid(dsl => {
        val insertValues: ImmutableMap[Any, Any] = toInsertValues(details, serializedAdditionalInformation)

        val insertStatement: InsertQuery[Record] = dsl.insertQuery(TABLE_NAME)
        insertStatement.addValue(TASK_ID, details.getTaskId.getValue)
        insertStatement.addValues(insertValues)
        insertStatement.onConflict(TASK_ID)
        insertStatement.onDuplicateKeyUpdate(true)
        insertStatement.addValuesForUpdate(insertValues)

        Mono.from(insertStatement)
      })))

  private def toInsertValues(details: TaskExecutionDetails, serializedAdditionalInformation: Optional[String]): ImmutableMap[Any, Any] = {
    val builder: ImmutableMap.Builder[Any, Any] = ImmutableMap.builder()
    builder.put(TYPE, details.getType.asString())
    builder.put(STATUS, details.getStatus.getValue)
    builder.put(SUBMITTED_DATE, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(details.getSubmittedDate))
    builder.put(SUBMITTED_NODE, details.getSubmittedNode.asString)
    details.getStartedDate.ifPresent(startedDate => builder.put(STARTED_DATE, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(startedDate)))
    details.getRanNode.ifPresent(hostname => builder.put(RAN_NODE, hostname.asString))
    details.getCompletedDate.ifPresent(completedDate => builder.put(COMPLETED_DATE, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(completedDate)))
    details.getCanceledDate.ifPresent(canceledDate => builder.put(CANCELED_DATE, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(canceledDate)))
    details.getCancelRequestedNode.ifPresent(hostname => builder.put(CANCEL_REQUESTED_NODE, hostname.asString))
    details.getFailedDate.ifPresent(failedDate => builder.put(FAILED_DATE, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(failedDate)))
    serializedAdditionalInformation.ifPresent(info => builder.put(ADDITIONAL_INFORMATION, jsonb(info)))
    builder.build()
  }

  private def serializeAdditionalInformation(details: TaskExecutionDetails): Mono[Optional[String]] = Mono.fromCallable(() => details
      .getAdditionalInformation
      .map(jsonTaskAdditionalInformationSerializer.serialize(_)))
    .cast(classOf[Optional[String]])
    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  def readDetails(taskId: TaskId): Mono[TaskExecutionDetails] =
    postgresExecutor.executeRow(dsl => Mono.from(dsl.selectFrom(TABLE_NAME)
        .where(TASK_ID.eq(taskId.getValue))))
      .map(toTaskExecutionDetails)

  def listDetails(): Flux[TaskExecutionDetails] =
    postgresExecutor.executeRows(dsl => Flux.from(dsl.selectFrom(TABLE_NAME)))
      .map(toTaskExecutionDetails)

  def listDetailsByBeforeDate(beforeDate: Instant): Flux[TaskExecutionDetails] =
    postgresExecutor.executeRows(dsl => Flux.from(dsl.selectFrom(TABLE_NAME)
        .where(SUBMITTED_DATE.lt(INSTANT_TO_LOCAL_DATE_TIME.apply(beforeDate)))))
      .map(toTaskExecutionDetails)

  def remove(details: TaskExecutionDetails): Mono[Void] =
    postgresExecutor.executeVoid(dsl => Mono.from(dsl.deleteFrom(TABLE_NAME)
      .where(TASK_ID.eq(details.getTaskId.getValue))))

  private def toTaskExecutionDetails(record: Record): TaskExecutionDetails =
    new TaskExecutionDetails(
      taskId = TaskId.fromUUID(record.get(TASK_ID)),
      `type` = TaskType.of(record.get(TYPE)),
      status = TaskManager.Status.fromString(record.get(STATUS)),
      submittedDate = LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(SUBMITTED_DATE, classOf[LocalDateTime])),
      submittedNode = Hostname(record.get(SUBMITTED_NODE)),
      startedDate = Optional.ofNullable(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(STARTED_DATE, classOf[LocalDateTime]))),
      ranNode = Optional.ofNullable(record.get(RAN_NODE)).map(Hostname(_)),
      completedDate = Optional.ofNullable(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(COMPLETED_DATE, classOf[LocalDateTime]))),
      canceledDate = Optional.ofNullable(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(CANCELED_DATE, classOf[LocalDateTime]))),
      cancelRequestedNode = Optional.ofNullable(record.get(CANCEL_REQUESTED_NODE)).map(Hostname(_)),
      failedDate = Optional.ofNullable(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(FAILED_DATE, classOf[LocalDateTime]))),
      additionalInformation = () => deserializeAdditionalInformation(record))

  private def deserializeAdditionalInformation(record: Record): Optional[TaskExecutionDetails.AdditionalInformation] =
    Optional.ofNullable(record.get(ADDITIONAL_INFORMATION))
      .map(additionalInformation => jsonTaskAdditionalInformationSerializer.deserialize(additionalInformation.data()))
}
