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
import org.apache.james.jmap.delegation.{DelegatedAccountSetRequest, DelegationId, UnparsedDelegateId}
import org.apache.james.jmap.method.DelegatedAccountDeletePerformer.{DelegatedAccountDeletionFailure, DelegatedAccountDeletionResult, DelegatedAccountDeletionResults, DelegatedAccountDeletionSuccess}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.user.api.DelegationStore
import org.apache.james.util.{AuditTrail, ReactorUtils}
import reactor.core.scala.publisher.{SFlux, SMono}

object DelegatedAccountDeletePerformer {
  sealed trait DelegatedAccountDeletionResult

  case class DelegatedAccountDeletionSuccess(delegationId: DelegationId) extends DelegatedAccountDeletionResult

  case class DelegatedAccountDeletionFailure(unparsedDelegateId: UnparsedDelegateId, exception: Throwable) extends DelegatedAccountDeletionResult {
    def asSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${unparsedDelegateId.id} is not a DelegationId: ${e.getMessage}"))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  case class DelegatedAccountDeletionResults(results: Seq[DelegatedAccountDeletionResult]) {
    def destroyed: Seq[DelegationId] =
      results.flatMap(result => result match {
        case success: DelegatedAccountDeletionSuccess => Some(success)
        case _ => None
      }).map(_.delegationId)

    def retrieveErrors: Map[UnparsedDelegateId, SetError] =
      results.flatMap(result => result match {
        case failure: DelegatedAccountDeletionFailure => Some(failure.unparsedDelegateId, failure.asSetError)
        case _ => None
      }).toMap
  }
}

class DelegatedAccountDeletePerformer @Inject()(delegationStore: DelegationStore) {
  def delete(request: DelegatedAccountSetRequest, mailboxSession: MailboxSession): SMono[DelegatedAccountDeletionResults] =
    SFlux.fromIterable(request.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession.getUser)
        .onErrorRecover(e => DelegatedAccountDeletionFailure(unparsedId, e)),
        maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(DelegatedAccountDeletionResults)

  private def delete(unparsedId: UnparsedDelegateId, baseUser: Username): SMono[DelegatedAccountDeletionResult] =
    unparsedId.parse
      .fold(e => SMono.error(e),
        id => SFlux(delegationStore.delegatedUsers(baseUser))
          .filter(delegatedUser => DelegationId.from(delegatedUser, baseUser).equals(id))
          .next()
          .flatMap(delegatedUser => SMono(delegationStore.removeDelegatedUser(baseUser, delegatedUser))
            .doOnSuccess(_ => AuditTrail.entry
              .username(() => baseUser.asString())
              .protocol("JMAP")
              .action("DelegatedAccountSet/destroy")
              .parameters(() => ImmutableMap.of("delegator", delegatedUser.asString(),
                "delegatee", baseUser.asString()))
              .log("Delegation removed.")))
          .`then`(SMono.just[DelegatedAccountDeletionResult](DelegatedAccountDeletionSuccess(id))))
}