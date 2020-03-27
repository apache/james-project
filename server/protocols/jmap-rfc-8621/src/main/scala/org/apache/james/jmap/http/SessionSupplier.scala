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

import com.google.common.annotations.VisibleForTesting
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import org.apache.james.core.Username
import org.apache.james.jmap.http.SessionSupplier.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model._
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}

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
}

class SessionSupplier {
  def generate(username: Username): SMono[Session] =
    SMono.fromPublisher(
      Mono.zip(
        accounts(username).asJava(),
        primaryAccounts(username).asJava()))
      .map(tuple => generate(username, tuple.getT1, tuple.getT2))

  private def accounts(username: Username): SMono[Map[Id, Account]] =
    getId(username)
      .map(id => Map(
        id -> Account(
          username,
          IsPersonal(true),
          IsReadOnly(false),
          accountCapabilities = Set(CORE_CAPABILITY, MAIL_CAPABILITY))))

  private def primaryAccounts(username: Username): SMono[Map[CapabilityIdentifier, Id]] =
    SFlux.just(CORE_CAPABILITY, MAIL_CAPABILITY)
      .flatMap(capability => getId(username)
        .map(id => (capability.identifier, id)))
      .collectMap(getIdentifier, getId)
  private def getIdentifier(tuple : (CapabilityIdentifier, Id)): CapabilityIdentifier = tuple._1
  private def getId(tuple : (CapabilityIdentifier, Id)): Id = tuple._2

  private def getId(username: Username): SMono[Id] = {
    SMono.fromCallable(() => refineId(username))
      .flatMap {
        case Left(errorMessage: String) => SMono.raiseError(new IllegalStateException(errorMessage))
        case Right(id) => SMono.just(id)
      }
  }

  private def refineId(username: Username): Either[String, Id] = refineV(usernameHashCode(username))
  @VisibleForTesting def usernameHashCode(username: Username) = username.asString().hashCode.toOctalString

  private def generate(username: Username,
                       accounts: Map[Id, Account],
                       primaryAccounts: Map[CapabilityIdentifier, Id]): Session = {
    Session(
      Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY),
      accounts,
      primaryAccounts,
      username,
      apiUrl = new URL("http://this-url-is-hardcoded.org/jmap"),
      downloadUrl = new URL("http://this-url-is-hardcoded.org/download"),
      uploadUrl = new URL("http://this-url-is-hardcoded.org/upload"),
      eventSourceUrl = new URL("http://this-url-is-hardcoded.org/eventSource"),
      state = "000001")
  }
}
