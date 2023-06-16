/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap

import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.json.ResponseSerializer
import play.api.libs.json.{JsError, JsPath, JsResult, JsonValidationError}

import scala.language.implicitConversions

package object method {

  def standardErrorMessage(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): String =
    errors.head match {
      case (path, Seq()) => s"'$path' property is not valid"
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => s"Missing '$path' property"
      case (path, Seq(JsonValidationError(Seq("error.expected.jsarray")))) => s"'$path' property need to be an array"
      case (path, Seq(JsonValidationError(Seq(message)))) => s"'$path' property is not valid: $message"
      case (path, _) => s"Unknown error on property '$path'"
      case _ => ResponseSerializer.serialize(JsError(errors)).toString()
    }

  def standardError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    SetError.invalidArguments(SetErrorDescription(standardErrorMessage(errors)))

  implicit class AsEitherRequest[T](val jsResult: JsResult[T]) {
    def asEitherRequest: Either[IllegalArgumentException, T] =
      jsResult.asEither.left.map(errors => new IllegalArgumentException(standardErrorMessage(errors)))
  }
}