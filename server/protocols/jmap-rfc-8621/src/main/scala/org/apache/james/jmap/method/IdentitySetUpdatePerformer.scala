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

package org.apache.james.jmap.method

import javax.inject.Inject
import org.apache.james.jmap.api.identity.{IdentityNotFoundException, IdentityRepository, IdentityUpdateRequest}
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.json.IdentitySerializer
import org.apache.james.jmap.mail.{IdentitySet, IdentitySetParseException, IdentitySetRequest, InvalidPropertyException, UnparsedIdentityId}
import org.apache.james.jmap.method.IdentitySetUpdatePerformer.IdentityUpdate.{knownProperties, serverSetProperty}
import org.apache.james.jmap.method.IdentitySetUpdatePerformer.{IdentitySetUpdateResults, UpdateFailure, UpdateResult, UpdateSuccess}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, JsValue}
import reactor.core.scala.publisher.{SFlux, SMono}

object IdentitySetUpdatePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[IdentitySetUpdatePerformer])
  sealed trait UpdateResult

  case class UpdateSuccess(id: IdentityId) extends UpdateResult

  case class UpdateFailure(id: UnparsedIdentityId, exception: Throwable) extends UpdateResult {
    def asSetError: SetError = exception match {
      case e: IdentitySetParseException =>
        LOGGER.info("Error parsing Identity/set update", e)
        e.setError
      case e: IdentityNotFoundException =>
        LOGGER.info("Identity not found in Identity/set update {}", e.getMessage)
        SetError.notFound(SetErrorDescription(e.getMessage))
      case e: InvalidPropertyException =>
        LOGGER.info("Invalid property in Identity/set update {}", e.getCause.getMessage)
        SetError.invalidPatch(SetErrorDescription(e.getCause.getMessage))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in Identity/set update", e)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
      case e =>
        LOGGER.error("Failed to delete identity", e)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  object IdentitySetUpdateResponse {
    def empty: IdentitySetUpdateResponse = IdentitySetUpdateResponse(JsObject(Map[String, JsValue]()))
  }

  case class IdentitySetUpdateResponse(value: JsObject)

  case class IdentitySetUpdateResults(results: Seq[UpdateResult]) {
    def updated: Map[IdentityId, IdentitySetUpdateResponse] =
      results.flatMap(result => result match {
        case success: UpdateSuccess => Some((success.id, IdentitySetUpdateResponse.empty))
        case _ => None
      }).toMap

    def notUpdated: Map[UnparsedIdentityId, SetError] = results.flatMap(result => result match {
      case failure: UpdateFailure => Some(failure.id, failure.asSetError)
      case _ => None
    }).toMap
  }

  object IdentityUpdate {
    val serverSetProperty: Set[String] = Set("id", "mayDelete", "email")
    val assignableProperties: Set[String] = Set("name", "replyTo", "bcc", "textSignature", "htmlSignature", "sortOrder")
    val knownProperties: Set[String] = assignableProperties ++ serverSetProperty
  }
}

class IdentitySetUpdatePerformer @Inject()(identityRepository: IdentityRepository) {
  def update(request: IdentitySetRequest, mailboxSession: MailboxSession): SMono[IdentitySetUpdateResults] =
    SFlux.fromIterable(request.update.getOrElse(Map()))
      .concatMap {
        case (unparsedId, json) =>
          val either: Either[Exception, SMono[UpdateResult]] = for {
            identityId <- unparsedId.validate
            updateRequest <- parseRequest(json)
          } yield {
            update(identityId, updateRequest, mailboxSession)
              .onErrorResume(error => SMono.just[UpdateResult](UpdateFailure(unparsedId, error)))
          }
          either.fold(error => SMono.just[UpdateResult](UpdateFailure(unparsedId, error)),
            updateRequestResult => updateRequestResult)
      }.collectSeq()
      .map(IdentitySetUpdateResults)

  private def parseRequest(jsObject: JsObject): Either[Exception, IdentityUpdateRequest] = for {
    validJsObject <- IdentitySet.validateProperties(serverSetProperty, knownProperties, jsObject)
    parsedRequest <- IdentitySerializer.deserializeIdentityUpdateRequest(validJsObject)
      .asEither
      .left.map(errors => IdentitySetParseException.from(errors))
  } yield parsedRequest

  private def update(identityId: IdentityId, updateRequest: IdentityUpdateRequest, mailboxSession: MailboxSession): SMono[UpdateResult] =
    SMono.fromPublisher(identityRepository.update(mailboxSession.getUser, identityId, updateRequest))
      .`then`(SMono.just[UpdateResult](UpdateSuccess(identityId)))

}
