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
import java.util.{Optional, UUID}
import javax.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.{EmailAddress, ForbiddenSendFromException, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.rrt.api.CanSendFrom
import org.apache.james.user.api.UsersRepository
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.StreamConverters._
import scala.util.Try
import scala.jdk.OptionConverters._
import scala.jdk.CollectionConverters._

object IdentityCreationRequest {
  def fromJava(mailAddress: MailAddress,
               identityName: Optional[String],
               replyTo: Optional[java.util.List[EmailAddress]],
               bcc: Optional[java.util.List[EmailAddress]],
               sortOrder: Optional[Integer],
               textSignature: Optional[String],
               htmlSignature: Optional[String]): IdentityCreationRequest = {
    IdentityCreationRequest(
      name = identityName.toScala.map(IdentityName(_)),
      email = mailAddress,
      replyTo = replyTo.toScala.map(_.asScala.toList),
      bcc = bcc.toScala.map(_.asScala.toList),
      sortOrder = sortOrder.toScala.map(_.toInt),
      textSignature = textSignature.toScala.map(TextSignature(_)),
      htmlSignature = htmlSignature.toScala.map(HtmlSignature(_)))
  }
}

case class IdentityCreationRequest(name: Option[IdentityName],
                                   email: MailAddress,
                                   replyTo: Option[List[EmailAddress]],
                                   bcc: Option[List[EmailAddress]],
                                   sortOrder: Option[Int] = None,
                                   textSignature: Option[TextSignature],
                                   htmlSignature: Option[HtmlSignature]) {
  def asIdentity(id: IdentityId): Identity = Identity(
    id = id,
    name = name.getOrElse(IdentityName.DEFAULT),
    email = email,
    replyTo = replyTo,
    bcc = bcc,
    textSignature = textSignature.getOrElse(TextSignature.DEFAULT),
    htmlSignature = htmlSignature.getOrElse(HtmlSignature.DEFAULT),
    mayDelete = MayDeleteIdentity(true),
    sortOrder = sortOrder.getOrElse(Identity.DEFAULT_SORTORDER))
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
case class IdentitySortOrderUpdate(sortOrder: Int) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(sortOrder = sortOrder)
}
case class IdentityTextSignatureUpdate(textSignature: TextSignature) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(textSignature = textSignature)
}
case class IdentityHtmlSignatureUpdate(htmlSignature: HtmlSignature) extends IdentityUpdate {
  override def update(identity: Identity): Identity = identity.copy(htmlSignature = htmlSignature)
}

object IdentityUpdateRequest {
  def fromJava(name: Optional[String],
               replyTo: Optional[java.util.List[EmailAddress]],
               bcc: Optional[java.util.List[EmailAddress]],
               sortOrder: Optional[Integer],
               textSignature: Optional[String],
               htmlSignature: Optional[String]): IdentityUpdateRequest = {
    IdentityUpdateRequest(
      name = name.toScala.map(IdentityName(_)).map(IdentityNameUpdate),
      sortOrder = sortOrder.toScala.map(IdentitySortOrderUpdate(_)),
      replyTo = Option(IdentityReplyToUpdate(replyTo.toScala.map(_.asScala.toList))),
      bcc = Option(IdentityBccUpdate(bcc.toScala.map(_.asScala.toList))),
      textSignature = textSignature.toScala.map(TextSignature(_)).map(IdentityTextSignatureUpdate),
      htmlSignature = htmlSignature.toScala.map(HtmlSignature(_)).map(IdentityHtmlSignatureUpdate))
  }
}

case class IdentityUpdateRequest(name: Option[IdentityNameUpdate] = None,
                                 replyTo: Option[IdentityReplyToUpdate] = None,
                                 sortOrder: Option[IdentitySortOrderUpdate] = None,
                                 bcc: Option[IdentityBccUpdate] = None,
                                 textSignature: Option[IdentityTextSignatureUpdate] = None,
                                 htmlSignature: Option[IdentityHtmlSignatureUpdate] = None) extends IdentityUpdate {
  def update(identity: Identity): Identity =
    List(name, replyTo, bcc, textSignature, htmlSignature, sortOrder)
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

  def delete(username: Username, ids: Set[IdentityId]): Publisher[Unit]

  def delete(username: Username): Publisher[Unit]
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
          mayDelete = MayDeleteIdentity(false),
          sortOrder = Identity.DEFAULT_SORTORDER)))

  def userCanSendFrom(username: Username, mailAddress: MailAddress): SMono[Boolean] =
    SMono.fromPublisher(canSendFrom.userCanSendFromReactive(username, usersRepository.getUsername(mailAddress)))
      .map(boolean2Boolean(_))

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
    identityFactory.userCanSendFrom(user, creationRequest.email)
      .filter(bool => bool)
      .flatMap(_ => SMono(customIdentityDao.save(user, creationRequest)))
      .switchIfEmpty(SMono.error(ForbiddenSendFromException(creationRequest.email)))

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
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .map(list => (list.map(_.email).toSet, list))

  private def listCustomIdentity(user: Username, availableMailAddresses: Set[MailAddress]): SFlux[Identity] =
    SFlux.fromPublisher(customIdentityDao.list(user))
      .filter(identity => availableMailAddresses.contains(identity.email))

  def update(user: Username, identityId: IdentityId, identityUpdateRequest: IdentityUpdateRequest): Publisher[Unit] = {
    val findServerSetIdentity: SMono[Option[Identity]] = SMono.fromCallable(() => identityFactory.listIdentities(user)
      .find(identity => identity.id.equals(identityId)))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
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
          identityFactory.userCanSendFrom(user, customIdentity.email)
            .filter(bool => bool)
            .switchIfEmpty(SMono.error(IdentityNotFoundException(identityId)))
            .flatMap(_ => SMono(customIdentityDao.upsert(user, identityUpdateRequest.update(customIdentity))))
      }
      .`then`()
  }

  def delete(username: Username, ids: Set[IdentityId]): Publisher[Unit] =
    SMono.just(ids)
      .handle[Set[IdentityId]]{
        case (ids, sink) => if (identityFactory.isServerSetIdentity(username, ids.head)) {
          sink.error(IdentityForbiddenDeleteException(ids.head))
        } else {
          sink.next(ids)
        }
      }
      .flatMap(ids => SMono.fromPublisher(customIdentityDao.delete(username, ids)))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
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

    override def merge(other: IdentityWithOrigin): IdentityWithOrigin = CustomIdentityOrigin(identity.copy(mayDelete = MayDeleteIdentity(false)))
  }

  case class ServerSetIdentityOrigin(inputIdentity: Identity) extends IdentityWithOrigin {
    override def identity: Identity = inputIdentity

    override def merge(other: IdentityWithOrigin): IdentityWithOrigin = CustomIdentityOrigin(other.identity.copy(mayDelete = MayDeleteIdentity(false)))
  }

  def fromCustom(identity: Identity): IdentityWithOrigin = CustomIdentityOrigin(identity)

  def fromServerSet(identity: Identity): IdentityWithOrigin = ServerSetIdentityOrigin(identity)

  def merge(value1: IdentityWithOrigin, value2: IdentityWithOrigin): IdentityWithOrigin = value1.merge(value2)
}