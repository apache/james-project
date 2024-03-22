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

import jakarta.inject.Inject
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection
import org.apache.james.task.{TaskExecutionDetails, TaskId}
import org.reactivestreams.Publisher

import java.time.Instant
import scala.compat.java8.OptionConverters._
import scala.jdk.CollectionConverters._

class CassandraTaskExecutionDetailsProjection @Inject()(cassandraTaskExecutionDetailsProjectionDAO: CassandraTaskExecutionDetailsProjectionDAO)
  extends TaskExecutionDetailsProjection {

  override def load(taskId: TaskId): Option[TaskExecutionDetails] =
    cassandraTaskExecutionDetailsProjectionDAO.readDetails(taskId).blockOptional().asScala

  override def list: List[TaskExecutionDetails] =
    cassandraTaskExecutionDetailsProjectionDAO.listDetails().collectList().block().asScala.toList

  override def update(details: TaskExecutionDetails): Unit =
    cassandraTaskExecutionDetailsProjectionDAO.saveDetails(details).block()

  override def loadReactive(taskId: TaskId): Publisher[TaskExecutionDetails] =
    cassandraTaskExecutionDetailsProjectionDAO.readDetails(taskId)

  override def listReactive(): Publisher[TaskExecutionDetails] = cassandraTaskExecutionDetailsProjectionDAO.listDetails()

  override def updateReactive(details: TaskExecutionDetails): Publisher[Void] = cassandraTaskExecutionDetailsProjectionDAO.saveDetails(details)

  override def listDetailsByBeforeDate(beforeDate: Instant): Publisher[TaskExecutionDetails] = cassandraTaskExecutionDetailsProjectionDAO.listDetailsByBeforeDate(beforeDate)

  override def remove(taskExecutionDetails: TaskExecutionDetails): Publisher[Void] = cassandraTaskExecutionDetailsProjectionDAO.remove(taskExecutionDetails)
}
