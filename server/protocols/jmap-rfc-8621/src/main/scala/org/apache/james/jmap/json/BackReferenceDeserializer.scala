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

import org.apache.james.jmap.model.Invocation.{MethodCallId, MethodName}
import org.apache.james.jmap.routes.{BackReference, JsonPath}
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads}

object BackReferenceDeserializer {
  private implicit val jsonPathReadRead: Reads[JsonPath] = {
    case JsString(path) => JsSuccess(JsonPath.parse(path))
    case _ => JsError("JsonPath objects are represented by JsonString")
  }

  private implicit val methodNameFormat: Format[MethodName] = Json.valueFormat[MethodName]
  private implicit val methodCallIdFormat: Format[MethodCallId] = Json.valueFormat[MethodCallId]
  private implicit val backReferenceReads: Reads[BackReference] = Json.reads[BackReference]

  def deserializeBackReference(input: JsValue): JsResult[BackReference] = Json.fromJson[BackReference](input)
}
