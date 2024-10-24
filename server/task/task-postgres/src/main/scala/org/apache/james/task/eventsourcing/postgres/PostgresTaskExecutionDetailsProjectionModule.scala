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

import java.time.LocalDateTime
import java.util.UUID

import org.apache.james.backends.postgres.{PostgresCommons, PostgresIndex, PostgresModule, PostgresTable}
import org.jooq.impl.{DSL, SQLDataType}
import org.jooq.{Field, JSONB, Record, Table}

object PostgresTaskExecutionDetailsProjectionModule {
  val TABLE_NAME: Table[Record] = DSL.table("task_execution_details_projection")

  val TASK_ID: Field[UUID] = DSL.field("task_id", SQLDataType.UUID.notNull)
  val ADDITIONAL_INFORMATION: Field[JSONB] = DSL.field("additional_information", SQLDataType.JSONB)
  val TYPE: Field[String] = DSL.field("type", SQLDataType.VARCHAR)
  val STATUS: Field[String] = DSL.field("status", SQLDataType.VARCHAR)
  val SUBMITTED_DATE: Field[LocalDateTime] = DSL.field("submitted_date", PostgresCommons.DataTypes.TIMESTAMP)
  val SUBMITTED_NODE: Field[String] = DSL.field("submitted_node", SQLDataType.VARCHAR)
  val STARTED_DATE: Field[LocalDateTime] = DSL.field("started_date", PostgresCommons.DataTypes.TIMESTAMP)
  val RAN_NODE: Field[String] = DSL.field("ran_node", SQLDataType.VARCHAR)
  val COMPLETED_DATE: Field[LocalDateTime] = DSL.field("completed_date", PostgresCommons.DataTypes.TIMESTAMP)
  val CANCELED_DATE: Field[LocalDateTime] = DSL.field("canceled_date", PostgresCommons.DataTypes.TIMESTAMP)
  val CANCEL_REQUESTED_NODE: Field[String] = DSL.field("cancel_requested_node", SQLDataType.VARCHAR)
  val FAILED_DATE: Field[LocalDateTime] = DSL.field("failed_date", PostgresCommons.DataTypes.TIMESTAMP)

  private val TABLE: PostgresTable = PostgresTable.name(TABLE_NAME.getName)
    .createTableStep((dsl, tableName) => dsl.createTableIfNotExists(tableName)
      .column(TASK_ID)
      .column(ADDITIONAL_INFORMATION)
      .column(TYPE)
      .column(STATUS)
      .column(SUBMITTED_DATE)
      .column(SUBMITTED_NODE)
      .column(STARTED_DATE)
      .column(RAN_NODE)
      .column(COMPLETED_DATE)
      .column(CANCELED_DATE)
      .column(CANCEL_REQUESTED_NODE)
      .column(FAILED_DATE)
      .constraint(DSL.primaryKey(TASK_ID)))
    .disableRowLevelSecurity
    .build

  private val SUBMITTED_DATE_INDEX: PostgresIndex = PostgresIndex.name("task_execution_details_projection_submittedDate_index")
    .createIndexStep((dsl, indexName) => dsl.createIndexIfNotExists(indexName)
      .on(TABLE_NAME, SUBMITTED_DATE));

  val MODULE: PostgresModule = PostgresModule
    .builder
    .addTable(TABLE)
    .addIndex(SUBMITTED_DATE_INDEX)
    .build
}
