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

package org.apache.james.jmap.routes

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core._
import reactor.core.scala.publisher.SMono

class SessionSupplier @Inject() (val configuration: JmapRfc8621Configuration){
  private val maxSizeUpload = configuration.maxUploadSize

  def generate(username: Username): SMono[Session] = {
    accounts(username)
      .map(account => Session(
        DefaultCapabilities.supported(maxSizeUpload),
        List(account),
        primaryAccounts(account.accountId),
        username,
        apiUrl = configuration.apiUrl,
        downloadUrl = configuration.downloadUrl,
        uploadUrl = configuration.uploadUrl,
        eventSourceUrl = configuration.eventSourceUrl))
  }

  private def accounts(username: Username): SMono[Account] = SMono.defer(() =>
    Account.from(username, IsPersonal(true), IsReadOnly(false), DefaultCapabilities.supported(maxSizeUpload).toSet) match {
      case Left(ex: IllegalArgumentException) => SMono.raiseError(ex)
      case Right(account: Account) => SMono.just(account)
    })

  private def primaryAccounts(accountId: AccountId): Map[CapabilityIdentifier, AccountId] =
    DefaultCapabilities.supported(maxSizeUpload).toSet
      .map(capability => (capability.identifier(), accountId))
      .toMap
}
