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
package org.apache.james.queue.pulsar

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uuid

import java.util.UUID

private[pulsar] object EnqueueId {

  type EnqueueIdConstraint = Uuid
  type EnqueueId = String Refined EnqueueIdConstraint

  def apply(uuid: String): Either[String, EnqueueId] =
    refined.refineV[EnqueueIdConstraint](uuid)

  private def apply(uuid: UUID): EnqueueId =
    refined.refineV[EnqueueIdConstraint](uuid.toString).toOption.get

  def generate() = EnqueueId(UUID.randomUUID())
}
