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
package org.apache.james.task

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.Optional

import org.apache.james.task.TaskExecutionDetails.AdditionalInformation

object TaskExecutionDetailsFixture {
  val SUBMITTED_DATE = ZonedDateTime.of(LocalDateTime.of(2000, 1, 1, 0, 0), ZoneId.of("Europe/Paris"))
  val SUBMITTED_NODE = Hostname("foo")
  val SUBMITTED_DATE_2 = ZonedDateTime.of(LocalDateTime.of(2011, 11, 11, 11, 11), ZoneId.of("Europe/Paris"))
  val SUBMITTED_NODE_2 = Hostname("bar")
  val TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd")
  val TASK_ID_2 = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafe")
  val ADDITIONAL_INFORMATION: () => Optional[AdditionalInformation] = Optional.empty

  val TYPE = TaskType.of("type")
  val TASK_EXECUTION_DETAILS = new TaskExecutionDetails(TASK_ID, TYPE, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE, ADDITIONAL_INFORMATION)
  val TASK_EXECUTION_DETAILS_2 = new TaskExecutionDetails(TASK_ID_2, TYPE, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE, ADDITIONAL_INFORMATION)
  val TASK_EXECUTION_DETAILS_UPDATED = new TaskExecutionDetails(TASK_ID, TYPE, TaskManager.Status.FAILED, SUBMITTED_DATE, SUBMITTED_NODE, ADDITIONAL_INFORMATION)


  val ADDITIONAL_INFORMATION_2: () => Optional[AdditionalInformation] = () => Optional.of(new CustomAdditionalInformation("hello"))
  val TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION = new TaskExecutionDetails(TASK_ID, TYPE, TaskManager.Status.COMPLETED, SUBMITTED_DATE_2, SUBMITTED_NODE_2, ADDITIONAL_INFORMATION)

}

case class CustomAdditionalInformation(value: String) extends AdditionalInformation
