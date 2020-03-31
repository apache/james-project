/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.http

import java.net.URL

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.http.SessionSupplier.{CORE_CAPABILITY, HARD_CODED_URL_PREFIX, MAIL_CAPABILITY}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model._
import reactor.core.scala.publisher.SMono

object SessionSupplier {
  private val CORE_CAPABILITY = CoreCapability(
    properties = CoreCapabilityProperties(
      MaxSizeUpload(10_000_000L),
      MaxConcurrentUpload(4L),
      MaxSizeRequest(10_000_000L),
      MaxConcurrentRequests(4L),
      MaxCallsInRequest(16L),
      MaxObjectsInGet(500L),
      MaxObjectsInSet(500L),
      collationAlgorithms = List("i;unicode-casemap")))

  private val MAIL_CAPABILITY = MailCapability(
    properties = MailCapabilityProperties(
      MaxMailboxesPerEmail(Some(10_000_000L)),
      MaxMailboxDepth(None),
      MaxSizeMailboxName(200L),
      MaxSizeAttachmentsPerEmail(20_000_000L),
      emailQuerySortOptions = List("receivedAt", "cc", "from", "to", "subject", "size", "sentAt", "hasKeyword", "uid", "Id"),
      MayCreateTopLevelMailbox(true)
    ))

  private val HARD_CODED_URL_PREFIX = "http://this-url-is-hardcoded.org"
}

class SessionSupplier {
  def generate(username: Username): SMono[Session] = {
    accounts(username)
      .map(account => Session(
        Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY),
        List(account),
        primaryAccounts(account.accountId),
        username,
        apiUrl = new URL(s"$HARD_CODED_URL_PREFIX/jmap"),
        downloadUrl = new URL(s"$HARD_CODED_URL_PREFIX/download"),
        uploadUrl = new URL(s"$HARD_CODED_URL_PREFIX/upload"),
        eventSourceUrl = new URL(s"$HARD_CODED_URL_PREFIX/eventSource")))
  }

  private def accounts(username: Username): SMono[Account] = SMono.defer(() =>
    Account.from(username, IsPersonal(true), IsReadOnly(false), Set(CORE_CAPABILITY, MAIL_CAPABILITY)) match {
      case Left(ex: IllegalArgumentException) => SMono.raiseError(ex)
      case Right(account: Account) => SMono.just(account)
    })

  private def primaryAccounts(accountId: AccountId): Map[CapabilityIdentifier, AccountId] =
    Map(CORE_CAPABILITY.identifier -> accountId, MAIL_CAPABILITY.identifier -> accountId)
}
