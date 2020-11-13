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
package org.apache.james.jmap.core

import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import org.apache.james.jmap.core.RequestLevelErrorType.{DEFAULT_ERROR_TYPE, ErrorTypeIdentifier}

/**
 * Problem Details for HTTP APIs within the JMAP context
 * https://tools.ietf.org/html/rfc7807
 * see https://jmap.io/spec-core.html#errors
 */
case class ProblemDetails(`type`: ErrorTypeIdentifier = DEFAULT_ERROR_TYPE,
                          status: HttpResponseStatus,
                          limit: Option[String] = None,
                          detail: String)

object ProblemDetails {
  def notRequestProblem(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.NOT_REQUEST, BAD_REQUEST, None, message)
  def notJSONProblem(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.NOT_JSON, BAD_REQUEST, None, message)
  def unknownCapabilityProblem(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.UNKNOWN_CAPABILITY, BAD_REQUEST, None, message)
  def invalidResultReference(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.UNKNOWN_CAPABILITY, BAD_REQUEST, None, message)
}
