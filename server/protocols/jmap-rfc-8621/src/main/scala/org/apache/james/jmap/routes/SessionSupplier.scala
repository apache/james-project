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

import java.net.{URI, URL}

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Account, AccountId, Capabilities, Capability, IsPersonal, IsReadOnly, JmapRfc8621Configuration, Session, WebSocketCapability}

import scala.jdk.CollectionConverters._

class SessionSupplier(val configuration: JmapRfc8621Configuration, defaultCapabilities: Set[Capability]) {
  @Inject
  def this(configuration: JmapRfc8621Configuration, defaultCapabilities: java.util.Set[Capability]) {
    this(configuration, defaultCapabilities.asScala.toSet)
  }

  def validate(username: Username, accountId: AccountId): Boolean = AccountId.from(username)
    .map(accountId.equals(_))
    .toOption
    .getOrElse(false)

  def generate(username: Username, urlPrefix: Option[URL] = None, webSocketUriPrefix: Option[URI] = None): Either[IllegalArgumentException, Session] = {
    val urlEndpointResolver: JmapUrlEndpointResolver = new JmapUrlEndpointResolver(configuration, urlPrefix, webSocketUriPrefix)
    val capabilities: Set[Capability] = defaultCapabilities
      .map {
        case websocketCapability: WebSocketCapability =>
          websocketCapability.copy(properties = websocketCapability.properties.copy(url = urlEndpointResolver.webSocketUrl))
        case another => another
      }
    accounts(username)
      .map(account => Session(
        Capabilities(capabilities),
        List(account),
        primaryAccounts(account.accountId),
        username,
        apiUrl = urlEndpointResolver.apiUrl,
        downloadUrl = urlEndpointResolver.downloadUrl,
        uploadUrl = urlEndpointResolver.uploadUrl,
        eventSourceUrl = urlEndpointResolver.eventSourceUrl))
  }

  private def accounts(username: Username): Either[IllegalArgumentException, Account] =
    Account.from(username, IsPersonal(true), IsReadOnly(false), defaultCapabilities)

  private def primaryAccounts(accountId: AccountId): Map[CapabilityIdentifier, AccountId] =
    defaultCapabilities
      .map(capability => (capability.identifier(), accountId))
      .toMap
}

object JmapUrlEndpointResolver {
  def from(configuration: JmapRfc8621Configuration): JmapUrlEndpointResolver = new JmapUrlEndpointResolver(configuration)
}

class JmapUrlEndpointResolver(val configuration: JmapRfc8621Configuration,
                              urlPrefixRequest: Option[URL] = None,
                              uriWebSocketPrefixRequest: Option[URI] = None) {

  val urlPrefix: String = Some(configuration.dynamicJmapPrefixResolutionEnabled)
    .filter(enabled => enabled)
    .flatMap(_ => urlPrefixRequest.map(_.toString))
    .getOrElse(configuration.urlPrefixString)

  val uriWebSocketPrefix: String = Some(configuration.dynamicJmapPrefixResolutionEnabled)
    .filter(enabled => enabled)
    .flatMap(_ => uriWebSocketPrefixRequest.map(_.toString))
    .getOrElse(configuration.websocketPrefixString)

  val apiUrl: URL = new URL(s"$urlPrefix/jmap")

  val downloadUrl: URL = new URL(s"$urlPrefix/download/{accountId}/{blobId}?type={type}&name={name}")

  val uploadUrl: URL = new URL(s"$urlPrefix/upload/{accountId}")

  val webSocketUrl: URI = new URI(s"$uriWebSocketPrefix/jmap/ws")

  val eventSourceUrl: URL = new URL(s"$urlPrefix/eventSource?types={types}&closeAfter={closeafter}&ping={ping}")
}