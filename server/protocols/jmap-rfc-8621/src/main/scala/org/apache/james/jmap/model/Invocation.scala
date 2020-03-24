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
package org.apache.james.jmap.model

import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import de.cbley.refined.play.json._

case class Invocation(methodName: MethodName, arguments: Arguments, methodCallId: MethodCallId)

object Invocation {
  private final val FIRST_ELEMENT: Int = 0
  private final val SECOND_ELEMENT: Int = 1
  private final val THIRD_ELEMENT: Int = 2

  case class MethodName(value: NonEmptyString)
  case class Arguments(value: JsObject) extends AnyVal
  case class MethodCallId(value: NonEmptyString)

  implicit val methodNameFormat: Format[MethodName] = Json.valueFormat[MethodName]
  implicit val argumentFormat: Format[Arguments] = Json.valueFormat[Arguments]
  implicit val methodCallIdFormat: Format[MethodCallId] = Json.valueFormat[MethodCallId]
  implicit val invocationRead: Reads[Invocation] = (
      (JsPath \ FIRST_ELEMENT).read[MethodName] and
      (JsPath \ SECOND_ELEMENT).read[Arguments] and
      (JsPath \ THIRD_ELEMENT).read[MethodCallId]
    ) (Invocation.apply _)

  implicit val invocationWrite: Writes[Invocation] = (invocation: Invocation) =>
    Json.arr(invocation.methodName, invocation.arguments, invocation.methodCallId)
}
