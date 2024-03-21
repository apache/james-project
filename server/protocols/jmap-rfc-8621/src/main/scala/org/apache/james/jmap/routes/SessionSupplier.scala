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

import java.net.URL

import cats.data.Validated
import cats.implicits.toTraverseOps
import cats.instances.list._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Account, AccountId, Capabilities, Capability, CapabilityFactory, IsPersonal, IsReadOnly, Session, UrlPrefixes}

import scala.jdk.CollectionConverters._

class SessionSupplier(capabilityFactories: Set[CapabilityFactory]) {
  @Inject
  def this(defaultCapabilities: java.util.Set[CapabilityFactory]) = {
    this(defaultCapabilities.asScala.toSet)
  }

  def validate(username: Username, accountId: AccountId): Boolean = AccountId.from(username)
    .map(accountId.equals(_))
    .toOption
    .getOrElse(false)

  def generate(username: Username, delegatedUsers: Set[Username], urlPrefixes: UrlPrefixes): Either[IllegalArgumentException, Session] = {
    val urlEndpointResolver: JmapUrlEndpointResolver = new JmapUrlEndpointResolver(urlPrefixes)
    val capabilities: Set[Capability] = capabilityFactories
      .map(cf => cf.create(urlPrefixes))

    for {
      account <- accounts(username, capabilities)
      delegatedAccounts <- delegatedAccounts(delegatedUsers, capabilities)
    } yield {
      Session(
        Capabilities(capabilities),
        List(account) ++ delegatedAccounts,
        primaryAccounts(account.accountId, capabilities),
        username,
        apiUrl = urlEndpointResolver.apiUrl,
        downloadUrl = urlEndpointResolver.downloadUrl,
        uploadUrl = urlEndpointResolver.uploadUrl,
        eventSourceUrl = urlEndpointResolver.eventSourceUrl)
    }
  }

  private def accounts(username: Username, capabilities: Set[Capability]): Either[IllegalArgumentException, Account] =
    Account.from(username, IsPersonal(true), IsReadOnly(false), capabilities)

  private def delegatedAccounts(delegatedUsers: Set[Username], capabilities: Set[Capability]): Either[IllegalArgumentException, List[Account]] =
    delegatedUsers.map(user => AccountId.from(user)
      .map(Account(_, user, IsPersonal(false), IsReadOnly(false), capabilities)))
      .toList.traverse(x => Validated.fromEither(x.left.map(List(_))))
      .toEither
      .left.map(_.head)

  private def primaryAccounts(accountId: AccountId, capabilities: Set[Capability]): Map[CapabilityIdentifier, AccountId] =
    capabilities
      .map(capability => (capability.identifier(), accountId))
      .toMap
}

class JmapUrlEndpointResolver(val urlPrefixes: UrlPrefixes) {
  val apiUrl: URL = new URL(urlPrefixes.httpUrlPrefix.toString + "/jmap")

  val downloadUrl: URL = new URL(urlPrefixes.httpUrlPrefix.toString + "/download/{accountId}/{blobId}?type={type}&name={name}")

  val uploadUrl: URL = new URL(urlPrefixes.httpUrlPrefix.toString + "/upload/{accountId}")

  val eventSourceUrl: URL = new URL(urlPrefixes.httpUrlPrefix.toString + "/eventSource?types={types}&closeAfter={closeafter}&ping={ping}")
}