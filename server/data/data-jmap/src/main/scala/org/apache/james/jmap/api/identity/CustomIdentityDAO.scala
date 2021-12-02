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

import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.{EmailAddress, ForbiddenSendFromException, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.rrt.api.CanSendFrom
import org.apache.james.user.api.UsersRepository
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import scala.jdk.StreamConverters._
import scala.util.Try

case class IdentityCreationRequest(name: Option[IdentityName],
                                   email: MailAddress,
                                   replyTo: Option[List[EmailAddress]],
                                   bcc: Option[List[EmailAddress]],
                                   textSignature: Option[TextSignature],
                                   htmlSignature: Option[HtmlSignature]) {
  def asIdentity(id: IdentityId): Identity = Identity(id, name.getOrElse(IdentityName.DEFAULT), email, replyTo, bcc, textSignature.getOrElse(TextSignature.DEFAULT), htmlSignature.getOrElse(HtmlSignature.DEFAULT), mayDelete = MayDeleteIdentity(true))
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
case class IdentityTextSignatureUpdate(textSignature: TextSignature) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(textSignature = textSignature)
}
case class IdentityHtmlSignatureUpdate(htmlSignature: HtmlSignature) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(htmlSignature = htmlSignature)
}

case class IdentityUpdateRequest(name: Option[IdentityNameUpdate] = None,
                                 replyTo: Option[IdentityReplyToUpdate] = None,
                                 bcc: Option[IdentityBccUpdate] = None,
                                 textSignature: Option[IdentityTextSignatureUpdate] = None,
                                 htmlSignature: Option[IdentityHtmlSignatureUpdate] = None) extends IdentityUpdate {
  def update(identity: Identity): Identity =
    List(name, replyTo, bcc, textSignature, htmlSignature)
      .flatten
      .foldLeft(identity)((acc, update) => update.update(acc))

  def asCreationRequest(email: MailAddress): IdentityCreationRequest =
    IdentityCreationRequest(
      name = name.map(_.name),
      email = email,
      replyTo = replyTo.flatMap(_.replyTo),
      bcc = bcc.flatMap(_.bcc),
      textSignature = textSignature.map(_.textSignature),
      htmlSignature = htmlSignature.map(_.htmlSignature))
}

trait CustomIdentityDAO {
  def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity]

  def save(user: Username, identityId: IdentityId, creationRequest: IdentityCreationRequest): Publisher[Identity]

  def list(user: Username): Publisher[Identity]

  def findByIdentityId(user: Username, identityId: IdentityId): SMono[Identity]

  def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): Publisher[Unit]

  def upsert(user: Username, patch: Identity): SMono[Unit]

  def delete(username: Username, ids: Seq[IdentityId]): Publisher[Unit]
}

class DefaultIdentitySupplier @Inject()(canSendFrom: CanSendFrom, usersRepository: UsersRepository) {
  def listIdentities(username: Username): List[Identity] = canSendFrom.allValidFromAddressesForUser(username)
    .toScala(LazyList).toList
    .flatMap(address =>
      from(address).map(id =>
        Identity(
          id = id,
          name = IdentityName(address.asString()),
          email = address,
          replyTo = None,
          bcc = None,
          textSignature = TextSignature.DEFAULT,
          htmlSignature = HtmlSignature.DEFAULT,
          mayDelete = MayDeleteIdentity(false))))

  def userCanSendFrom(username: Username, mailAddress: MailAddress): Boolean =
    canSendFrom.userCanSendFrom(username, usersRepository.getUsername(mailAddress))

  def isServerSetIdentity(username: Username, id: IdentityId): Boolean =
    listIdentities(username).map(_.id).contains(id)

  private def from(address: MailAddress): Option[IdentityId] =
    Try(UUID.nameUUIDFromBytes(address.asString().getBytes(StandardCharsets.UTF_8)))
      .toEither
      .toOption
      .map(IdentityId(_))
}

// This class is intended to merge default (server-set0 identities with (user defined) custom identities
// Using the custom identities we can stores deltas of the default (server-set) identities allowing to modify them.
class IdentityRepository @Inject()(customIdentityDao: CustomIdentityDAO, identityFactory: DefaultIdentitySupplier) {
  def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity] =
    if (identityFactory.userCanSendFrom(user, creationRequest.email)) {
      customIdentityDao.save(user, creationRequest)
    } else {
      SMono.error(ForbiddenSendFromException(creationRequest.email))
    }

  def list(user: Username): Publisher[Identity] =
    listServerSetIdentity(user)
      .flatMapMany { case (mailAddressSet, identityList) => listCustomIdentity(user, mailAddressSet)
        .map(IdentityWithOrigin.fromCustom)
        .mergeWith(SFlux.fromIterable(identityList)
          .map(IdentityWithOrigin.fromServerSet))
      }
      .groupBy(_.identity.id)
      .flatMap(_.reduce(IdentityWithOrigin.merge))
      .map(_.identity)

  private def listServerSetIdentity(user: Username): SMono[(Set[MailAddress], List[Identity])] =
    SMono.fromCallable(() => identityFactory.listIdentities(user))
      .subscribeOn(Schedulers.elastic())
      .map(list => (list.map(_.email).toSet, list))

  private def listCustomIdentity(user: Username, availableMailAddresses: Set[MailAddress]): SFlux[Identity] =
    SFlux.fromPublisher(customIdentityDao.list(user))
      .filter(identity => availableMailAddresses.contains(identity.email))

  def update(user: Username, identityId: IdentityId, identityUpdateRequest: IdentityUpdateRequest): Publisher[Unit] = {
    val findServerSetIdentity: SMono[Option[Identity]] = SMono.fromCallable(() => identityFactory.listIdentities(user)
      .find(identity => identity.id.equals(identityId)))
    val findCustomIdentity: SMono[Option[Identity]] = SMono(customIdentityDao.findByIdentityId(user, identityId))
      .map(Some(_))
      .switchIfEmpty(SMono.just(None))

    SFlux.zip(findServerSetIdentity, findCustomIdentity)
      .next()
      .flatMap {
        case (None, None) => SMono.error(IdentityNotFoundException(identityId))
        case (Some(_), Some(customIdentity)) => customIdentityDao.upsert(user, identityUpdateRequest.update(customIdentity))
        case (Some(serverSetIdentity), None) => SMono(customIdentityDao.save(user, identityId, identityUpdateRequest.asCreationRequest(serverSetIdentity.email)))
        case (None, Some(customIdentity)) =>
          if (identityFactory.userCanSendFrom(user, customIdentity.email)) {
            customIdentityDao.upsert(user, identityUpdateRequest.update(customIdentity))
          } else {
            SMono.error(IdentityNotFoundException(identityId))
          }
      }
      .`then`()
  }

  def delete(username: Username, ids: Seq[IdentityId]): Publisher[Unit] =
    SMono.just(ids)
      .handle[Seq[IdentityId]]{
        case (ids, sink) => if (identityFactory.isServerSetIdentity(username, ids.head)) {
          sink.error(IdentityForbiddenDeleteException(ids.head))
        } else {
          sink.next(ids)
        }
      }
      .flatMap(ids => SMono.fromPublisher(customIdentityDao.delete(username, ids)))
      .subscribeOn(Schedulers.elastic())
}

case class IdentityNotFoundException(id: IdentityId) extends RuntimeException(s"$id could not be found")
case class IdentityForbiddenDeleteException(id: IdentityId) extends IllegalArgumentException(s"User do not have permission to delete $id")

object IdentityWithOrigin {
  sealed trait IdentityWithOrigin {
    def identity: Identity

    def merge(other: IdentityWithOrigin): IdentityWithOrigin
  }

  case class CustomIdentityOrigin(inputIdentity: Identity) extends IdentityWithOrigin {
    override def identity: Identity = inputIdentity

    override def merge(other: IdentityWithOrigin): IdentityWithOrigin = this
  }

  case class ServerSetIdentityOrigin(inputIdentity: Identity) extends IdentityWithOrigin {
    override def identity: Identity = inputIdentity

    override def merge(other: IdentityWithOrigin): IdentityWithOrigin = other
  }

  def fromCustom(identity: Identity): IdentityWithOrigin = CustomIdentityOrigin(identity)

  def fromServerSet(identity: Identity): IdentityWithOrigin = ServerSetIdentityOrigin(identity)

  def merge(value1: IdentityWithOrigin, value2: IdentityWithOrigin): IdentityWithOrigin = value1.merge(value2)
}