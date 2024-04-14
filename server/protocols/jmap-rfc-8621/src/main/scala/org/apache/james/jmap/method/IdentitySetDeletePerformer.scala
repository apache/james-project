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

import jakarta.inject.Inject
import org.apache.james.jmap.api.identity.{IdentityForbiddenDeleteException, IdentityRepository}
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.{IdentitySetRequest, UnparsedIdentityId}
import org.apache.james.jmap.method.IdentitySetDeletePerformer.{IdentityDeletionFailure, IdentityDeletionResult, IdentityDeletionResults, IdentityDeletionSuccess}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

object IdentitySetDeletePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[IdentitySetDeletePerformer])
  sealed trait IdentityDeletionResult
  case class IdentityDeletionSuccess(identityId: IdentityId) extends IdentityDeletionResult
  case class IdentityDeletionFailure(identityId: UnparsedIdentityId, exception: Throwable) extends IdentityDeletionResult {
    def asIdentitySetError: SetError = exception match {
      case e: IdentityForbiddenDeleteException =>
        LOGGER.info("Forbidden to delete server set identity")
        SetError.forbidden(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in Identity/set delete", e)
        SetError.invalidArguments(SetErrorDescription(s"${identityId.id} is not a IdentityId: ${e.getMessage}"))
      case e =>
        LOGGER.error("Failed to delete identity", e)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class IdentityDeletionResults(results: Seq[IdentityDeletionResult]) {
    def destroyed: Seq[IdentityId] =
      results.flatMap(result => result match {
        case success: IdentityDeletionSuccess => Some(success)
        case _ => None
      }).map(_.identityId)

    def retrieveErrors: Map[UnparsedIdentityId, SetError] =
      results.flatMap(result => result match {
        case failure: IdentityDeletionFailure => Some(failure.identityId, failure.asIdentitySetError)
        case _ => None
      })
        .toMap
  }
}

class IdentitySetDeletePerformer @Inject()(identityRepository: IdentityRepository) {
  def destroy(identitySetRequest: IdentitySetRequest, mailboxSession: MailboxSession): SMono[IdentityDeletionResults] =
    SFlux.fromIterable(identitySetRequest.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession)
        .onErrorRecover(e => IdentityDeletionFailure(unparsedId, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(IdentityDeletionResults)

  private def delete(unparsedId: UnparsedIdentityId, mailboxSession: MailboxSession): SMono[IdentityDeletionResult] =
    unparsedId.validate
      .fold(e => SMono.error(e),
        id => SMono.fromPublisher(identityRepository.delete(mailboxSession.getUser, Set(id)))
          .`then`(SMono.just[IdentityDeletionResult](IdentityDeletionSuccess(id))))
}