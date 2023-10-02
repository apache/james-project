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

import com.google.common.collect.ImmutableMap
import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.delegation.{DelegateSetRequest, DelegationId, UnparsedDelegateId}
import org.apache.james.jmap.method.DelegateSetDeletePerformer.{DelegateDeletionFailure, DelegateDeletionResult, DelegateDeletionResults, DelegateDeletionSuccess}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.user.api.DelegationStore
import org.apache.james.util.{AuditTrail, ReactorUtils}
import reactor.core.scala.publisher.{SFlux, SMono}

object DelegateSetDeletePerformer {
  sealed trait DelegateDeletionResult

  case class DelegateDeletionSuccess(delegationId: DelegationId) extends DelegateDeletionResult

  case class DelegateDeletionFailure(unparsedDelegateId: UnparsedDelegateId, exception: Throwable) extends DelegateDeletionResult {
    def asSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${unparsedDelegateId.id} is not a DelegationId: ${e.getMessage}"))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  case class DelegateDeletionResults(results: Seq[DelegateDeletionResult]) {
    def destroyed: Seq[DelegationId] =
      results.flatMap(result => result match {
        case success: DelegateDeletionSuccess => Some(success)
        case _ => None
      }).map(_.delegationId)

    def retrieveErrors: Map[UnparsedDelegateId, SetError] =
      results.flatMap(result => result match {
        case failure: DelegateDeletionFailure => Some(failure.unparsedDelegateId, failure.asSetError)
        case _ => None
      }).toMap
  }
}

class DelegateSetDeletePerformer @Inject()(delegationStore: DelegationStore) {
  def delete(request: DelegateSetRequest, mailboxSession: MailboxSession): SMono[DelegateDeletionResults] =
    SFlux.fromIterable(request.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession.getUser)
        .onErrorRecover(e => DelegateDeletionFailure(unparsedId, e)),
        maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(DelegateDeletionResults)

  private def delete(unparsedId: UnparsedDelegateId, baseUser: Username): SMono[DelegateDeletionResult] =
    unparsedId.parse
      .fold(e => SMono.error(e),
        id => SFlux(delegationStore.authorizedUsers(baseUser))
          .filter(authorizedUser => DelegationId.from(baseUser, authorizedUser).equals(id))
          .next()
          .flatMap(authorizedUser => SMono(delegationStore.removeAuthorizedUser(baseUser, authorizedUser))
            .doOnSuccess(_ => AuditTrail.entry
              .username(() => baseUser.asString())
              .protocol("JMAP")
              .action("DelegateSet/destroy")
              .parameters(() => ImmutableMap.of("delegator", baseUser.asString(),
                "delegatee", authorizedUser.asString()))
              .log("Delegation removed.")))
          .`then`(SMono.just[DelegateDeletionResult](DelegateDeletionSuccess(id))))
}
