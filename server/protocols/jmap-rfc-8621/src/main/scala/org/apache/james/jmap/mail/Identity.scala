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
import java.util.UUID

import com.google.common.collect.ImmutableList
import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.mailbox.MailboxSession
import org.apache.james.rrt.api.CanSendFrom

import scala.jdk.CollectionConverters._
import scala.util.Try

case class IdentityName(name: String) extends AnyVal
case class TextSignature(name: String) extends AnyVal
case class HtmlSignature(name: String) extends AnyVal
case class MayDeleteIdentity(value: Boolean) extends AnyVal
case class IdentityId(id: UUID)

case class Identity(id: IdentityId,
                    name: IdentityName,
                    email: MailAddress,
                    replyTo: Option[List[EmailAddress]],
                    bcc: Option[List[EmailAddress]],
                    textSignature: Option[TextSignature],
                    htmlSignature: Option[HtmlSignature],
                    mayDelete: MayDeleteIdentity)

class IdentityFactory @Inject()(canSendFrom: CanSendFrom) {
  def listIdentities(session: MailboxSession): List[Identity] =
    canSendFrom.allValidFromAddressesForUser(session.getUser)
      .collect(ImmutableList.toImmutableList()).asScala.toList
      .flatMap(address =>
        from(address).map(id =>
          Identity(
            id = id,
            name = IdentityName(address.asString()),
            email = address,
            replyTo = None,
            bcc = None,
            textSignature = None,
            htmlSignature = None,
            mayDelete = MayDeleteIdentity(false))))

  private def from(address: MailAddress): Option[IdentityId] =
    Try(UUID.nameUUIDFromBytes(address.asString().getBytes(StandardCharsets.UTF_8)))
      .toEither
      .toOption
      .map(IdentityId)
}
