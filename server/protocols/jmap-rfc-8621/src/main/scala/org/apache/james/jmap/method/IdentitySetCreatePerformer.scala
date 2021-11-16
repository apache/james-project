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

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.api.identity.{IdentityCreationRequest, IdentityRepository}
import org.apache.james.jmap.api.model.{ForbiddenSendFromException, HtmlSignature, Identity, IdentityName, TextSignature}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.IdentitySerializer
import org.apache.james.jmap.mail.{IdentityCreation, IdentityCreationId, IdentityCreationParseException, IdentityCreationResponse, IdentitySetRequest}
import org.apache.james.jmap.method.IdentitySetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.mailbox.MailboxSession
import play.api.libs.json.{JsObject, JsPath, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

object IdentitySetCreatePerformer {
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
      case e: IdentityCreationParseException => e.setError
      case e: ForbiddenSendFromException => SetError.forbiddenFrom(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
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
    validJsObject <- IdentityCreation.validateProperties(jsObject)
    parsedRequest <- IdentitySerializer.deserializeIdentityCreationRequest(validJsObject).asEither
      .left.map(errors => IdentityCreationParseException(IdentitySetError(errors)))
  } yield {
    parsedRequest
  }

  private def create(clientId: IdentityCreationId, request: IdentityCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] =
    SMono.fromPublisher(identityRepository.save(mailboxSession.getUser, request))
      .map(identity => CreationSuccess(clientId, evaluateCreationResponse(request, identity)))
      .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))
      .subscribeOn(Schedulers.elastic)

  private def evaluateCreationResponse(request: IdentityCreationRequest, identity: Identity): IdentityCreationResponse =
    IdentityCreationResponse(
      id = identity.id,
      name = request.name.fold[Option[IdentityName]](Some(IdentityName.DEFAULT))(_ => None),
      textSignature = request.textSignature.fold[Option[TextSignature]](Some(TextSignature.DEFAULT))(_ => None),
      htmlSignature = request.htmlSignature.fold[Option[HtmlSignature]](Some(HtmlSignature.DEFAULT))(_ => None),
      mayDelete = identity.mayDelete)

  private def IdentitySetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Identity object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) =>
        SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in Identity object"), Some(Properties("email")))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Identity object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }
}
