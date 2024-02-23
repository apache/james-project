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
import org.apache.james.jmap.api.identity.{IdentityCreationRequest, IdentityRepository}
import org.apache.james.jmap.api.model.{ForbiddenSendFromException, HtmlSignature, Identity, IdentityName, TextSignature}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.json.IdentitySerializer
import org.apache.james.jmap.mail.IdentityCreation.{knownProperties, serverSetProperty}
import org.apache.james.jmap.mail.{IdentityCreationId, IdentityCreationResponse, IdentitySet, IdentitySetParseException, IdentitySetRequest}
import org.apache.james.jmap.method.IdentitySetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.util.ReactorUtils
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

object IdentitySetCreatePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[IdentitySetCreatePerformer])
  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[IdentityCreationId, IdentityCreationResponse]] =
      Option(results.flatMap {
        case result: CreationSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[IdentityCreationId, SetError]] =
      Option(results.flatMap {
        case failure: CreationFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }
        .toMap)
        .filter(_.nonEmpty)
  }

  trait CreationResult

  case class CreationSuccess(clientId: IdentityCreationId, response: IdentityCreationResponse) extends CreationResult

  case class CreationFailure(clientId: IdentityCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: IdentitySetParseException =>
        LOGGER.info("Failure to parse Identity/set create", e)
        e.setError
      case e: ForbiddenSendFromException =>
        LOGGER.warn(s"Attempt to send an identity whose From field not allowed for connected user: ${e.mailAddress}")
        SetError.forbiddenFrom(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in Identity/set create", e)
        SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ =>
        LOGGER.error("Failed to create identity", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

class IdentitySetCreatePerformer @Inject()(identityRepository: IdentityRepository) {
  def create(request: IdentitySetRequest, mailboxSession: MailboxSession): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => parseCreate(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
            creationRequest => create(clientId, creationRequest, mailboxSession))
      }.collectSeq()
      .map(CreationResults)

  private def parseCreate(jsObject: JsObject): Either[Exception, IdentityCreationRequest] = for {
    validJsObject <- IdentitySet.validateProperties(serverSetProperty, knownProperties, jsObject)
    parsedRequest <- IdentitySerializer.deserializeIdentityCreationRequest(validJsObject).asEither
      .left.map(errors => IdentitySetParseException.from(errors))
  } yield {
    parsedRequest
  }

  private def create(clientId: IdentityCreationId, request: IdentityCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] =
    SMono.fromPublisher(identityRepository.save(mailboxSession.getUser, request))
      .map(identity => CreationSuccess(clientId, evaluateCreationResponse(request, identity)))
      .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def evaluateCreationResponse(request: IdentityCreationRequest, identity: Identity): IdentityCreationResponse =
    IdentityCreationResponse(
      id = identity.id,
      name = request.name.fold[Option[IdentityName]](Some(IdentityName.DEFAULT))(_ => None),
      textSignature = request.textSignature.fold[Option[TextSignature]](Some(TextSignature.DEFAULT))(_ => None),
      htmlSignature = request.htmlSignature.fold[Option[HtmlSignature]](Some(HtmlSignature.DEFAULT))(_ => None),
      mayDelete = identity.mayDelete)
}
