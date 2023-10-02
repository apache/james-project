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

import com.google.common.collect.ImmutableMap
import javax.inject.Inject
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.delegation.DelegationCreation.{knownProperties, serverSetProperty}
import org.apache.james.jmap.delegation.{DelegateCreationId, DelegateCreationRequest, DelegateCreationResponse, DelegateSetParseException, DelegateSetRequest, DelegationCreation, DelegationId}
import org.apache.james.jmap.json.DelegationSerializer
import org.apache.james.jmap.method.DelegateSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.exception.UserDoesNotExistException
import org.apache.james.user.api.{DelegationStore, UsersRepository}
import org.apache.james.util.AuditTrail
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

object DelegateSetCreatePerformer {
  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[DelegateCreationId, DelegateCreationResponse]] =
      Option(results.flatMap {
        case result: CreationSuccess => Some((result.delegateCreationId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[DelegateCreationId, SetError]] =
      Option(results.flatMap {
        case failure: CreationFailure => Some((failure.delegateCreationId, failure.asMessageSetError))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)
  }

  trait CreationResult

  case class CreationSuccess(delegateCreationId: DelegateCreationId, response: DelegateCreationResponse) extends CreationResult

  case class CreationFailure(delegateCreationId: DelegateCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: DelegateSetParseException => e.setError
      case e: UserDoesNotExistException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

class DelegateSetCreatePerformer @Inject()(delegationStore: DelegationStore,
                                           usersRepository: UsersRepository) {
  def create(request: DelegateSetRequest, mailboxSession: MailboxSession): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (delegateCreationId, json) => parseCreate(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(delegateCreationId, e)),
            creationRequest => create(delegateCreationId, creationRequest, mailboxSession))
      }.collectSeq()
      .map(CreationResults)

  private def parseCreate(jsObject: JsObject): Either[Exception, DelegateCreationRequest] = for {
    validJsObject <- DelegationCreation.validateProperties(serverSetProperty, knownProperties, jsObject)
    parsedRequest <- DelegationSerializer.deserializeDelegateCreationRequest(validJsObject).asEither
      .left.map(errors => DelegateSetParseException.from(errors))
  } yield {
    parsedRequest
  }

  private def create(delegateCreationId: DelegateCreationId, request: DelegateCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] =
    SMono.fromPublisher(usersRepository.containsReactive(request.username))
      .filter(bool => bool)
      .flatMap(_ => SMono.fromPublisher(delegationStore.addAuthorizedUser(mailboxSession.getUser, request.username))
        .doOnSuccess(_ => AuditTrail.entry
          .username(() => mailboxSession.getUser.asString())
          .protocol("JMAP")
          .action("DelegateSet/create")
          .parameters(() => ImmutableMap.of("delegator", mailboxSession.getUser.asString(),
            "delegatee", request.username.asString()))
          .log("Delegation added."))
        .`then`(SMono.just[CreationResult](CreationSuccess(delegateCreationId, evaluateCreationResponse(request, mailboxSession))))
        .onErrorResume(e => SMono.just[CreationResult](CreationFailure(delegateCreationId, e))))
      .switchIfEmpty(SMono.just[CreationResult](CreationFailure(delegateCreationId, new UserDoesNotExistException(request.username))))

  private def evaluateCreationResponse(request: DelegateCreationRequest, mailboxSession: MailboxSession): DelegateCreationResponse =
    DelegateCreationResponse(id = DelegationId.from(mailboxSession.getUser, request.username))
}
