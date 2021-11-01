/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import com.google.common.collect.ImmutableSet
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{PushSubscription, PushSubscriptionId, PushSubscriptionNotFoundException, TypeName, VerificationCode}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, PushSubscriptionPatchObject, PushSubscriptionSetRequest, PushSubscriptionUpdateResponse, SetError, UnparsedPushSubscriptionId, ValidatedPushSubscriptionPatchObject}
import org.apache.james.jmap.mail.{InvalidPropertyException, InvalidUpdateException, UnsupportedPropertyUpdatedException}
import org.apache.james.jmap.method.PushSubscriptionSetUpdatePerformer.{PushSubscriptionUpdateFailure, PushSubscriptionUpdateResult, PushSubscriptionUpdateResults, PushSubscriptionUpdateSuccess, WrongVerificationCodeException}
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object PushSubscriptionSetUpdatePerformer {
  case class WrongVerificationCodeException() extends RuntimeException()
  sealed trait PushSubscriptionUpdateResult
  case class PushSubscriptionUpdateSuccess(id: PushSubscriptionId) extends PushSubscriptionUpdateResult
  case class PushSubscriptionUpdateFailure(id: UnparsedPushSubscriptionId, exception: Throwable) extends PushSubscriptionUpdateResult {
    def asSetError: SetError = exception match {
      case _: WrongVerificationCodeException => SetError.invalidProperties(SetErrorDescription("Wrong verification code"), Some(Properties("verificationCode")))
      case e: UnsupportedPropertyUpdatedException => SetError.invalidArguments(SetErrorDescription(s"${e.property} property do not exist thus cannot be updated"), Some(Properties(e.property)))
      case e: InvalidPropertyException => SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
      case e: InvalidUpdateException => SetError.invalidArguments(SetErrorDescription(s"${e.cause}"), Some(Properties(e.property)))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
      case e: PushSubscriptionNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class PushSubscriptionUpdateResults(results: Seq[PushSubscriptionUpdateResult]) {
    def updated: Map[PushSubscriptionId, PushSubscriptionUpdateResponse] =
      results.flatMap(result => result match {
        case success: PushSubscriptionUpdateSuccess => Some((success.id, PushSubscriptionUpdateResponse.empty))
        case _ => None
      }).toMap
    def notUpdated: Map[UnparsedPushSubscriptionId, SetError] = results.flatMap(result => result match {
      case failure: PushSubscriptionUpdateFailure => Some(failure.id, failure.asSetError)
      case _ => None
    }).toMap
  }
}

class PushSubscriptionUpdatePerformer @Inject()(pushSubscriptionRepository: PushSubscriptionRepository,
                                               typeStateFactory: TypeStateFactory) {
  def update(pushSubscriptionSetRequest: PushSubscriptionSetRequest,
             mailboxSession: MailboxSession): SMono[PushSubscriptionUpdateResults] =
    SFlux.fromIterable(pushSubscriptionSetRequest.update.getOrElse(Map()))
      .flatMap({
        case (unparsedId: UnparsedPushSubscriptionId, patch: PushSubscriptionPatchObject) =>
          val either = for {
            id <- unparsedId.parse
            validatedPatch <- patch.validate(typeStateFactory)
          } yield {
            updatePushSubscription(id, validatedPatch, mailboxSession)
          }
          either.fold(e => SMono.just(PushSubscriptionUpdateFailure(unparsedId, e)),
            smono => smono
              .onErrorResume(e => SMono.just(PushSubscriptionUpdateFailure(unparsedId, e))))
      }, maxConcurrency = 5)
      .collectSeq()
      .map(PushSubscriptionUpdateResults)

  private def updatePushSubscription(id: PushSubscriptionId,
                                     validatedPatch: ValidatedPushSubscriptionPatchObject,
                                     mailboxSession: MailboxSession): SMono[PushSubscriptionUpdateResult] =
    if (validatedPatch.shouldUpdate) {
      SMono(pushSubscriptionRepository.get(mailboxSession.getUser, ImmutableSet.of(id)))
        .switchIfEmpty(SMono.error(PushSubscriptionNotFoundException(id)))
        .flatMap(pushSubscription => SFlux.concat(
          validatedPatch.verificationCodeUpdate
            .map(verificationCode => updateVerificationCode(pushSubscription, verificationCode, mailboxSession))
            .getOrElse(SMono.empty),
          validatedPatch.typesUpdate
            .map(types => updateTypes(pushSubscription, types, mailboxSession))
            .getOrElse(SMono.empty))
          .last())
    } else {
      SMono.empty
    }

  private def updateVerificationCode(pushSubscription: PushSubscription, verificationCode: VerificationCode, mailboxSession: MailboxSession): SMono[PushSubscriptionUpdateResult] =
    if (verificationCode.equals(pushSubscription.verificationCode)) {
      SMono(pushSubscriptionRepository.validateVerificationCode(mailboxSession.getUser, pushSubscription.id))
        .`then`(SMono.just(PushSubscriptionUpdateSuccess(pushSubscription.id)))
    } else {
      SMono.error[PushSubscriptionUpdateResult](WrongVerificationCodeException())
    }

  private def updateTypes(pushSubscription: PushSubscription, types: Set[TypeName], mailboxSession: MailboxSession): SMono[PushSubscriptionUpdateResult] =
    SMono(pushSubscriptionRepository.updateTypes(mailboxSession.getUser, pushSubscription.id, types.asJava))
      .`then`(SMono.just(PushSubscriptionUpdateSuccess(pushSubscription.id)))
}
