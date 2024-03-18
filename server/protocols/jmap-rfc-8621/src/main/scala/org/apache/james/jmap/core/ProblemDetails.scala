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

import com.fasterxml.jackson.core.JsonParseException
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import org.apache.james.jmap.core.RequestLevelErrorType.{DEFAULT_ERROR_TYPE, ErrorTypeIdentifier}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.routes.UnsupportedCapabilitiesException
import org.slf4j.{Logger, LoggerFactory}
import reactor.netty.channel.AbortedException

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
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[ProblemDetails])

  def forThrowable(throwable: Throwable): ProblemDetails = throwable match {
    case exception: AbortedException =>
      LOGGER.info("The connection was aborted: {}", exception.getMessage)
      ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = exception.getMessage)
    case exception: IllegalArgumentException =>
      LOGGER.info("The request was successfully parsed as JSON but did not match the type signature of the Request object: {}", exception.getMessage)
      notRequestProblem(
        s"The request was successfully parsed as JSON but did not match the type signature of the Request object: ${exception.getMessage}")
    case e: UnauthorizedException =>
      LOGGER.info("Unauthorized", e)
      ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage)
    case exception: JsonParseException =>
      LOGGER.info("The content type of the request was not application/json or the request did not parse as I-JSON: {}", exception.getMessage)
      notJSONProblem(
        s"The content type of the request was not application/json or the request did not parse as I-JSON: ${exception.getMessage}")
    case exception: UnsupportedCapabilitiesException =>
      LOGGER.info(s"The request used unsupported capabilities: ${exception.capabilities}")
      unknownCapabilityProblem(s"The request used unsupported capabilities: ${exception.capabilities}")
    case e =>
      LOGGER.error("Unexpected error upon API request", e)
      ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage)
  }

  def notRequestProblem(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.NOT_REQUEST, BAD_REQUEST, None, message)
  def notJSONProblem(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.NOT_JSON, BAD_REQUEST, None, message)
  def unknownCapabilityProblem(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.UNKNOWN_CAPABILITY, BAD_REQUEST, None, message)
  def invalidResultReference(message: String): ProblemDetails = ProblemDetails(RequestLevelErrorType.UNKNOWN_CAPABILITY, BAD_REQUEST, None, message)
}
