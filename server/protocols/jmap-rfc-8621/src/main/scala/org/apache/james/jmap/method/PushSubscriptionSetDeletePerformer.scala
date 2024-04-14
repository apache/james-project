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

import jakarta.inject.Inject
import org.apache.james.jmap.api.model.PushSubscriptionId
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{PushSubscriptionSetRequest, SetError, UnparsedPushSubscriptionId}
import org.apache.james.jmap.method.PushSubscriptionSetDeletePerformer.{PushSubscriptionDeletionFailure, PushSubscriptionDeletionResult, PushSubscriptionDeletionResults, PushSubscriptionDeletionSuccess}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

object PushSubscriptionSetDeletePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[PushSubscriptionSetDeletePerformer])
  sealed trait PushSubscriptionDeletionResult
  case class PushSubscriptionDeletionSuccess(PushSubscriptionId: PushSubscriptionId) extends PushSubscriptionDeletionResult
  case class PushSubscriptionDeletionFailure(PushSubscriptionId: UnparsedPushSubscriptionId, exception: Throwable) extends PushSubscriptionDeletionResult {
    def asPushSubscriptionSetError: SetError = exception match {
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in PushSubscription/set delete", e)
        SetError.invalidArguments(SetErrorDescription(s"${PushSubscriptionId.id} is not a PushSubscriptionId: ${e.getMessage}"))
      case e =>
        LOGGER.error("Failed to delete Push subscription", e)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class PushSubscriptionDeletionResults(results: Seq[PushSubscriptionDeletionResult]) {
    def destroyed: Seq[PushSubscriptionId] =
      results.flatMap(result => result match {
        case success: PushSubscriptionDeletionSuccess => Some(success)
        case _ => None
      }).map(_.PushSubscriptionId)

    def retrieveErrors: Map[UnparsedPushSubscriptionId, SetError] =
      results.flatMap(result => result match {
        case failure: PushSubscriptionDeletionFailure => Some(failure.PushSubscriptionId, failure.asPushSubscriptionSetError)
        case _ => None
      })
        .toMap
  }
}

class PushSubscriptionSetDeletePerformer @Inject()(pushSubscriptionRepository: PushSubscriptionRepository) {
  def deletePushSubscriptions(pushSubscriptionSetRequest: PushSubscriptionSetRequest, mailboxSession: MailboxSession): SMono[PushSubscriptionDeletionResults] =
    SFlux.fromIterable(pushSubscriptionSetRequest.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession)
        .onErrorRecover(e => PushSubscriptionDeletionFailure(unparsedId, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(PushSubscriptionDeletionResults)

  private def delete(unparsedId: UnparsedPushSubscriptionId, mailboxSession: MailboxSession): SMono[PushSubscriptionDeletionResult] =
    unparsedId.parse
      .fold(e => SMono.error(e),
        id => SMono.fromPublisher(pushSubscriptionRepository.revoke(mailboxSession.getUser, id))
          .`then`(SMono.just[PushSubscriptionDeletionResult](PushSubscriptionDeletionSuccess(id))))
}
