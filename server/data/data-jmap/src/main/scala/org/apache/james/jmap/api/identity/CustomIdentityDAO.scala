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

package org.apache.james.jmap.api.identity

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.collect.ImmutableList
import javax.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.{EmailAddress, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.rrt.api.CanSendFrom
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._
import scala.util.Try

case class IdentityCreationRequest(name: IdentityName,
                                    email: MailAddress,
                                    replyTo: Option[List[EmailAddress]],
                                    bcc: Option[List[EmailAddress]],
                                    textSignature: Option[TextSignature],
                                    htmlSignature: Option[HtmlSignature]) {
  def asIdentity(id: IdentityId): Identity = Identity(id, name, email, replyTo, bcc, textSignature, htmlSignature, mayDelete = MayDeleteIdentity(true))
}

trait IdentityUpdate {
  def update(identity: Identity): Identity
}
case class IdentityNameUpdate(name: IdentityName) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(name = name)
}
case class IdentityReplyToUpdate(replyTo: Option[List[EmailAddress]]) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(replyTo = replyTo)
}
case class IdentityBccUpdate(bcc: Option[List[EmailAddress]]) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(bcc = bcc)
}
case class IdentityTextSignatureUpdate(textSignature: Option[TextSignature]) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(textSignature = textSignature)
}
case class IdentityHtmlSignatureUpdate(htmlSignature: Option[HtmlSignature]) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(htmlSignature = htmlSignature)
}

case class IdentityUpdateRequest(name: IdentityNameUpdate,
                                 replyTo: IdentityReplyToUpdate,
                                 bcc: IdentityBccUpdate,
                                 textSignature: IdentityTextSignatureUpdate,
                                 htmlSignature: IdentityHtmlSignatureUpdate) {
  def update(identity: Identity): Identity =
    List(name, replyTo, bcc, textSignature, htmlSignature)
      .foldLeft(identity)((acc, update) => update.update(acc))
}

trait CustomIdentityDAO {
  def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity]

  def list(user: Username): Publisher[Identity]

  def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): Publisher[Unit]

  def delete(username: Username, ids: Seq[IdentityId]): Publisher[Unit]
}

class DefaultIdentitySupplier @Inject()(canSendFrom: CanSendFrom) {
  def listIdentities(username: Username): List[Identity] =
    canSendFrom.allValidFromAddressesForUser(username)
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
      .map(IdentityId(_))
}

// This class is intended to merge default (server-set0 identities with (user defined) custom identities
// Using the custom identities we can stores deltas of the default (server-set) identities allowing to modify them.
class IdentityRepository @Inject()(customIdentityDao: CustomIdentityDAO, identityFactory: DefaultIdentitySupplier) {
  def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity] = customIdentityDao.save(user, creationRequest)

  def list(user: Username): Publisher[Identity] = SFlux.merge(Seq(
    customIdentityDao.list(user),
    SMono.fromCallable(() => identityFactory.listIdentities(user))
      .subscribeOn(Schedulers.elastic())
      .flatMapMany(SFlux.fromIterable)))

  def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): Publisher[Unit] = customIdentityDao.update(user, identityId, identityUpdate)

  def delete(username: Username, ids: Seq[IdentityId]): Publisher[Unit] = customIdentityDao.delete(username, ids)
}

case class IdentityNotFound(id: IdentityId) extends RuntimeException(s"$id could not be found")