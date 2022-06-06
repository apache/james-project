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

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule

object CassandraTaskExecutionDetailsProjectionTable {
  val TABLE_NAME: String = "taskExecutionDetailsProjection"

  val TASK_ID: String = "taskID"
  val ADDITIONAL_INFORMATION: String = "additionalInformation"
  val TYPE: String = "type"
  val STATUS: String = "status"
  val SUBMITTED_DATE: String = "submittedDate"
  val SUBMITTED_NODE: String = "submittedNode"
  val STARTED_DATE: String = "startedDate"
  val RAN_NODE: String = "ranNode"
  val COMPLETED_DATE: String = "completedDate"
  val CANCELED_DATE: String = "canceledDate"
  val CANCEL_REQUESTED_NODE: String = "cancelRequestedNode"
  val FAILED_DATE: String = "failedDate"
}

object CassandraTaskExecutionDetailsProjectionModule {

  val MODULE: CassandraModule = CassandraModule.table(CassandraTaskExecutionDetailsProjectionTable.TABLE_NAME)
    .comment("Projection of TaskExecutionDetails used by the distributed task manager")
    .options(options => options.withCaching(true, SchemaBuilder.RowsPerPartition.NONE))
    .statement(statement => types => statement
      .withPartitionKey(CassandraTaskExecutionDetailsProjectionTable.TASK_ID, DataTypes.UUID)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.ADDITIONAL_INFORMATION, DataTypes.TEXT)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.TYPE, DataTypes.TEXT)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.STATUS, DataTypes.TEXT)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.SUBMITTED_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.SUBMITTED_NODE, DataTypes.TEXT)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.STARTED_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.RAN_NODE, DataTypes.TEXT)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.COMPLETED_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.CANCELED_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.CANCEL_REQUESTED_NODE, DataTypes.TEXT)
      .withColumn(CassandraTaskExecutionDetailsProjectionTable.FAILED_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME)))
    .build
}
