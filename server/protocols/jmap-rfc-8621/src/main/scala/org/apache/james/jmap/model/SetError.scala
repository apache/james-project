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

package org.apache.james.jmap.model

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.model.SetError.{SetErrorDescription, SetErrorType}

object SetError {
  type SetErrorType = String Refined NonEmpty
  case class SetErrorDescription(description: String) extends AnyVal

  val invalidArgumentValue: SetErrorType = "invalidArguments"
  val serverFailValue: SetErrorType = "serverFail"
  val invalidPatchValue: SetErrorType = "invalidPatch"
  val notFoundValue: SetErrorType = "notFound"
  val forbiddenValue: SetErrorType = "forbidden"

  def invalidArgument(description: Option[SetErrorDescription], properties: Option[Properties]) = SetError(invalidArgumentValue, description, properties)
  def serverFail(description: Option[SetErrorDescription], properties: Option[Properties]) = SetError(serverFailValue, description, properties)
  def notFound(description: Option[SetErrorDescription]) = SetError(notFoundValue, description, None)
  def invalidPatch(description: Option[SetErrorDescription]) = SetError(invalidPatchValue, description, None)
  def forbidden(description: Option[SetErrorDescription], properties: Option[Properties]) = SetError(forbiddenValue, description, properties)
}

case class SetError(`type`: SetErrorType, description: Option[SetErrorDescription], properties: Option[Properties])
