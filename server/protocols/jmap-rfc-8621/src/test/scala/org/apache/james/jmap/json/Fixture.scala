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

package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.{ClientId, CreatedIds, Invocation, ServerId}
import play.api.libs.json.Json

object Fixture {
  val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
  val createdIds: CreatedIds = CreatedIds(Map(ClientId(id) -> ServerId(id)))
  val coreIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:core"
  val mailIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  val invocation1: Invocation = Invocation(
    methodName = MethodName("Core/echo1"),
    arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")),
    methodCallId = MethodCallId("c1"))
  val invocation2: Invocation = Invocation(
    methodName = MethodName("Core/echo2"),
    arguments = Arguments(Json.obj("arg3" -> "arg3data", "arg4" -> "arg4data")),
    methodCallId = MethodCallId("c2")
  )
}
