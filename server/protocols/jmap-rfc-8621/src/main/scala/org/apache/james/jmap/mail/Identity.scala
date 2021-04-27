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

package org.apache.james.jmap.mail

import java.nio.charset.StandardCharsets

import com.github.steveash.guavate.Guavate
import com.google.common.hash.Hashing
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{AccountId, Properties, UuidState}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.MailboxSession
import org.apache.james.rrt.api.CanSendFrom

import scala.jdk.CollectionConverters._

object Identity {
  val allProperties: Properties = Properties("id", "name", "email", "replyTo", "bcc", "textSignature", "htmlSignature",
    "mayDelete")
  val idProperty: Properties = Properties("id")
}

case class IdentityName(name: String) extends AnyVal
case class TextSignature(name: String) extends AnyVal
case class HtmlSignature(name: String) extends AnyVal
case class MayDeleteIdentity(value: Boolean) extends AnyVal
case class IdentityId(id: Id)
case class IdentityIds(ids: List[IdentityId]) {
  def contains(identityId: IdentityId): Boolean = ids.contains(identityId)
}

case class Identity(id: IdentityId,
                    name: IdentityName,
                    email: MailAddress,
                    replyTo: Option[List[EmailAddress]],
                    bcc: Option[List[EmailAddress]],
                    textSignature: Option[TextSignature],
                    htmlSignature: Option[HtmlSignature],
                    mayDelete: MayDeleteIdentity)

case class IdentityGetRequest(accountId: AccountId,
                              ids: Option[IdentityIds],
                              properties: Option[Properties]) extends WithAccountId {
  def computeResponse(identities: List[Identity]): IdentityGetResponse = {
    val list: Option[List[Identity]] = Some(identities.filter(identity => isRequested(identity.id))).filter(_.nonEmpty)
    val notFound: Option[List[IdentityId]] = ids.map(ids => ids.ids.filter(id => !identities.map(_.id).contains(id))).filter(_.nonEmpty)

    IdentityGetResponse(
      accountId = accountId,
      state = INSTANCE,
      list = list,
      notFound = notFound.map(IdentityIds))
  }

  private def isRequested(id: IdentityId): Boolean = ids.forall(_.contains(id))
}

case class IdentityGetResponse(accountId: AccountId,
                               state: UuidState,
                               list: Option[List[Identity]],
                               notFound: Option[IdentityIds])

class IdentityFactory @Inject()(canSendFrom: CanSendFrom) {
  def listIdentities(session: MailboxSession): List[Identity] =
    canSendFrom.allValidFromAddressesForUser(session.getUser)
      .collect(Guavate.toImmutableList()).asScala.toList
      .flatMap(address =>
        from(address).map(id =>
          Identity(
            id = IdentityId(id),
            name = IdentityName(address.asString()),
            email = address,
            replyTo = None,
            bcc = None,
            textSignature = None,
            htmlSignature = None,
            mayDelete = MayDeleteIdentity(false))))

  private def from(address: MailAddress): Option[Id] = {
    val sha256String = Hashing.sha256()
      .hashString(address.asString(), StandardCharsets.UTF_8)
      .toString
    val refinedId: Either[String, Id] = refineV(sha256String)
    refinedId.toOption
  }
}
