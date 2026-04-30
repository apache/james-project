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

package org.apache.james.jmap.change

import java.util.{Optional, UUID}

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.mail.{AllCustomIdentitiesDeleted, CustomIdentityCreated, CustomIdentityDeleted, CustomIdentityUpdated}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case class EmailAddressDTO(@JsonProperty("name") getName: Optional[String],
                           @JsonProperty("email") getEmail: String)

case class IdentityDTO(@JsonProperty("id") getId: String,
                       @JsonProperty("sortOrder") getSortOrder: Int,
                       @JsonProperty("name") getName: String,
                       @JsonProperty("email") getEmail: String,
                       @JsonProperty("replyTo") getReplyTo: Optional[java.util.List[EmailAddressDTO]],
                       @JsonProperty("bcc") getBcc: Optional[java.util.List[EmailAddressDTO]],
                       @JsonProperty("textSignature") getTextSignature: String,
                       @JsonProperty("htmlSignature") getHtmlSignature: String,
                       @JsonProperty("mayDelete") getMayDelete: Boolean)

case class CustomIdentityCreatedDTO(@JsonProperty("type") getType: String,
                                    @JsonProperty("eventId") getEventId: String,
                                    @JsonProperty("username") getUsername: String,
                                    @JsonProperty("identity") getIdentity: IdentityDTO) extends EventDTO

case class CustomIdentityUpdatedDTO(@JsonProperty("type") getType: String,
                                    @JsonProperty("eventId") getEventId: String,
                                    @JsonProperty("username") getUsername: String,
                                    @JsonProperty("identity") getIdentity: IdentityDTO) extends EventDTO

case class CustomIdentityDeletedDTO(@JsonProperty("type") getType: String,
                                    @JsonProperty("eventId") getEventId: String,
                                    @JsonProperty("username") getUsername: String,
                                    @JsonProperty("identityIds") getIdentityIds: java.util.List[String]) extends EventDTO

case class AllCustomIdentitiesDeletedDTO(@JsonProperty("type") getType: String,
                                         @JsonProperty("eventId") getEventId: String,
                                         @JsonProperty("username") getUsername: String,
                                         @JsonProperty("identityIds") getIdentityIds: java.util.List[String]) extends EventDTO

object IdentityEventsSerializer {
  private def toEmailAddressDTO(ea: EmailAddress): EmailAddressDTO =
    EmailAddressDTO(ea.name.map(_.value).toJava, ea.email.asString)

  private def fromEmailAddressDTO(dto: EmailAddressDTO): EmailAddress =
    EmailAddress(dto.getName.toScala.map(EmailerName(_)), new MailAddress(dto.getEmail))

  def toIdentityDTO(identity: Identity): IdentityDTO =
    IdentityDTO(
      getId = identity.id.id.toString,
      getSortOrder = identity.sortOrder,
      getName = identity.name.name,
      getEmail = identity.email.asString,
      getReplyTo = identity.replyTo.map(_.map(toEmailAddressDTO).asJava).toJava,
      getBcc = identity.bcc.map(_.map(toEmailAddressDTO).asJava).toJava,
      getTextSignature = identity.textSignature.name,
      getHtmlSignature = identity.htmlSignature.name,
      getMayDelete = identity.mayDelete.value)

  def fromIdentityDTO(dto: IdentityDTO): Identity =
    Identity(
      id = IdentityId(UUID.fromString(dto.getId)),
      sortOrder = dto.getSortOrder,
      name = IdentityName(dto.getName),
      email = new MailAddress(dto.getEmail),
      replyTo = dto.getReplyTo.toScala.map(_.asScala.toList.map(fromEmailAddressDTO)),
      bcc = dto.getBcc.toScala.map(_.asScala.toList.map(fromEmailAddressDTO)),
      textSignature = TextSignature(dto.getTextSignature),
      htmlSignature = HtmlSignature(dto.getHtmlSignature),
      mayDelete = MayDeleteIdentity(dto.getMayDelete))

  val customIdentityCreatedModule: EventDTOModule[CustomIdentityCreated, CustomIdentityCreatedDTO] =
    EventDTOModule.forEvent(classOf[CustomIdentityCreated])
      .convertToDTO(classOf[CustomIdentityCreatedDTO])
      .toDomainObjectConverter(dto => CustomIdentityCreated(
        eventId = EventId.of(dto.getEventId),
        username = Username.of(dto.getUsername),
        identity = fromIdentityDTO(dto.getIdentity)))
      .toDTOConverter((event, _) => CustomIdentityCreatedDTO(
        getType = classOf[CustomIdentityCreated].getCanonicalName,
        getEventId = event.eventId.getId.toString,
        getUsername = event.username.asString,
        getIdentity = toIdentityDTO(event.identity)))
      .typeName(classOf[CustomIdentityCreated].getCanonicalName)
      .withFactory(EventDTOModule.apply)

  val customIdentityUpdatedModule: EventDTOModule[CustomIdentityUpdated, CustomIdentityUpdatedDTO] =
    EventDTOModule.forEvent(classOf[CustomIdentityUpdated])
      .convertToDTO(classOf[CustomIdentityUpdatedDTO])
      .toDomainObjectConverter(dto => CustomIdentityUpdated(
        eventId = EventId.of(dto.getEventId),
        username = Username.of(dto.getUsername),
        identity = fromIdentityDTO(dto.getIdentity)))
      .toDTOConverter((event, _) => CustomIdentityUpdatedDTO(
        getType = classOf[CustomIdentityUpdated].getCanonicalName,
        getEventId = event.eventId.getId.toString,
        getUsername = event.username.asString,
        getIdentity = toIdentityDTO(event.identity)))
      .typeName(classOf[CustomIdentityUpdated].getCanonicalName)
      .withFactory(EventDTOModule.apply)

  val customIdentityDeletedModule: EventDTOModule[CustomIdentityDeleted, CustomIdentityDeletedDTO] =
    EventDTOModule.forEvent(classOf[CustomIdentityDeleted])
      .convertToDTO(classOf[CustomIdentityDeletedDTO])
      .toDomainObjectConverter(dto => CustomIdentityDeleted(
        eventId = EventId.of(dto.getEventId),
        username = Username.of(dto.getUsername),
        identityIds = dto.getIdentityIds.asScala.map(id => IdentityId(UUID.fromString(id))).toSet))
      .toDTOConverter((event, _) => CustomIdentityDeletedDTO(
        getType = classOf[CustomIdentityDeleted].getCanonicalName,
        getEventId = event.eventId.getId.toString,
        getUsername = event.username.asString,
        getIdentityIds = event.identityIds.map(_.id.toString).toList.asJava))
      .typeName(classOf[CustomIdentityDeleted].getCanonicalName)
      .withFactory(EventDTOModule.apply)

  val allCustomIdentitiesDeletedModule: EventDTOModule[AllCustomIdentitiesDeleted, AllCustomIdentitiesDeletedDTO] =
    EventDTOModule.forEvent(classOf[AllCustomIdentitiesDeleted])
      .convertToDTO(classOf[AllCustomIdentitiesDeletedDTO])
      .toDomainObjectConverter(dto => AllCustomIdentitiesDeleted(
        eventId = EventId.of(dto.getEventId),
        username = Username.of(dto.getUsername),
        identityIds = dto.getIdentityIds.asScala.map(id => IdentityId(UUID.fromString(id))).toSet))
      .toDTOConverter((event, _) => AllCustomIdentitiesDeletedDTO(
        getType = classOf[AllCustomIdentitiesDeleted].getCanonicalName,
        getEventId = event.eventId.getId.toString,
        getUsername = event.username.asString,
        getIdentityIds = event.identityIds.map(_.id.toString).toList.asJava))
      .typeName(classOf[AllCustomIdentitiesDeleted].getCanonicalName)
      .withFactory(EventDTOModule.apply)
}
