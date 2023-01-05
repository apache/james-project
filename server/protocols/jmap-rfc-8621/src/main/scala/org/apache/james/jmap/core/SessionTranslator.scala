/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.core

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.method.AccountNotFoundException
import org.apache.james.mailbox.{MailboxSession, SessionProvider}
import org.apache.james.user.api.DelegationStore
import org.apache.james.util.ReactorUtils
import reactor.core.scala.publisher.{SFlux, SMono}

class SessionTranslator  @Inject()(delegationStore: DelegationStore, sessionProvider: SessionProvider) {
  def delegateIfNeeded(session: MailboxSession, targetAccountId: AccountId): SMono[MailboxSession] =
    if (needDelegation(session, targetAccountId)) {
      delegate(session, targetAccountId)
    } else {
      SMono.just(session)
    }

  private def needDelegation(session: MailboxSession, targetAccountId: AccountId): Boolean =
   !hasAccountId(targetAccountId)(session.getUser)

  private def hasAccountId(targetAccountId: AccountId)(username: Username): Boolean =
    AccountId.from(username).toOption.contains(targetAccountId)

  private def delegate(session: MailboxSession, targetAccountId: AccountId): SMono[MailboxSession] =
    SFlux(delegationStore.delegatedUsers(session.getUser))
      .filter(hasAccountId(targetAccountId))
      .flatMap(targetUser => SMono.fromCallable(() => sessionProvider.authenticate(session.getUser).as(targetUser))
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
      .next()
      .switchIfEmpty(SMono.error(AccountNotFoundException()))
}
