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

import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import play.api.libs.json._

case class Invocation(methodName: MethodName, arguments: Arguments, methodCallId: MethodCallId)

object Invocation {
  private[jmap] val METHOD_NAME: Int = 0
  private[jmap] val ARGUMENTS: Int = 1
  private[jmap] val METHOD_CALL: Int = 2

  case class MethodName(value: NonEmptyString)
  case class Arguments(value: JsObject) extends AnyVal
  case class MethodCallId(value: NonEmptyString)


  def error(errorCode: ErrorCode, description: Option[String], methodCallId: MethodCallId): Invocation = {
    Invocation(MethodName("error"),
      Arguments(JsObject(Map("type" -> JsString(errorCode.code), "description" -> JsString(description.getOrElse(errorCode.defaultDescription))))),
      methodCallId)
  }
  def error(errorCode: ErrorCode, methodCallId: MethodCallId): Invocation = {
    Invocation(MethodName("error"),
      Arguments(JsObject(Map("type" -> JsString(errorCode.code)))),
      methodCallId)
  }
}

sealed trait ErrorCode {
  def code: String
  def defaultDescription: String
}

object ErrorCode {
  case object InvalidArguments extends ErrorCode {
    override def code: String = "error"

    override def defaultDescription: String = "One of the arguments is of the wrong type or otherwise invalid, or a required argument is missing."
  }

  case object ServerFail extends ErrorCode {
    override def code: String = "serverFail"

    override def defaultDescription: String = null
  }

  case object UnknownMethod extends ErrorCode {
    override def code: String = "unknownMethod"

    override def defaultDescription: String = null
  }

  case object AccountNotFound extends ErrorCode {
    override def code: String = "accountNotFound"

    override def defaultDescription: String = null
  }
}
