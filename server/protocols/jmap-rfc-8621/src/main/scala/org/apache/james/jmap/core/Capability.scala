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

package org.apache.james.jmap.core

import java.net.{URI, URL}

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Uri
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JAMES_DELEGATION, JAMES_IDENTITY_SORTORDER, JAMES_QUOTA, JAMES_SHARES, JMAP_CORE, JMAP_MAIL, JMAP_MDN, JMAP_QUOTA, JMAP_VACATION_RESPONSE, JMAP_WEBSOCKET}
import org.apache.james.jmap.core.CoreCapabilityProperties.CollationAlgorithm
import org.apache.james.jmap.core.MailCapability.EmailQuerySortOption
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.util.Size
import play.api.libs.json.{JsObject, Json}
import reactor.netty.http.server.HttpServerRequest

import scala.util.{Failure, Success, Try}

sealed trait CapabilityValidationException extends IllegalArgumentException
case class MissingCapabilityException(description: String) extends CapabilityValidationException

object CapabilityIdentifier {
  type CapabilityIdentifier = String Refined Uri
  val JMAP_CORE: CapabilityIdentifier = "urn:ietf:params:jmap:core"
  val JMAP_MAIL: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  val JMAP_VACATION_RESPONSE: CapabilityIdentifier = "urn:ietf:params:jmap:vacationresponse"
  val EMAIL_SUBMISSION: CapabilityIdentifier = "urn:ietf:params:jmap:submission"
  val JMAP_WEBSOCKET: CapabilityIdentifier = "urn:ietf:params:jmap:websocket"
  val JAMES_QUOTA: CapabilityIdentifier = "urn:apache:james:params:jmap:mail:quota"
  val JMAP_QUOTA: CapabilityIdentifier = "urn:ietf:params:jmap:quota"
  val JAMES_SHARES: CapabilityIdentifier = "urn:apache:james:params:jmap:mail:shares"
  val JAMES_IDENTITY_SORTORDER: CapabilityIdentifier = "urn:apache:james:params:jmap:mail:identity:sortorder"
  val JAMES_DELEGATION: CapabilityIdentifier = "urn:apache:james:params:jmap:delegation"
  val JMAP_MDN: CapabilityIdentifier = "urn:ietf:params:jmap:mdn"
}

trait CapabilityProperties {
  def jsonify(): JsObject
}

trait Capability {
  def identifier(): CapabilityIdentifier
  def properties(): CapabilityProperties
}

object UrlPrefixes {
  private val JMAP_PREFIX_HEADER: String = "X-JMAP-PREFIX"
  private val JMAP_WEBSOCKET_PREFIX_HEADER: String = "X-JMAP-WEBSOCKET-PREFIX"

  def from(jmapRfc8621Configuration: JmapRfc8621Configuration, request: HttpServerRequest): UrlPrefixes =
    if (jmapRfc8621Configuration.dynamicJmapPrefixResolutionEnabled) {
      UrlPrefixes(
        safeURL(request.requestHeaders().get(JMAP_PREFIX_HEADER))
          .map(_.toURI)
          .getOrElse(new URI(jmapRfc8621Configuration.urlPrefixString)),
        safeURI(request.requestHeaders().get(JMAP_WEBSOCKET_PREFIX_HEADER))
          .getOrElse(new URI(jmapRfc8621Configuration.websocketPrefixString)))
    } else {
      jmapRfc8621Configuration.urlPrefixes()
    }

  private def safeURI(string: String): Option[URI] = Option(string).flatMap(s => Try(new URI(s)).toOption)

  private def safeURL(string: String): Option[URL] = Option(string).flatMap(s => Try(new URL(s)).toOption)
}

final case class UrlPrefixes(httpUrlPrefix: URI, webSocketURLPrefix: URI)

trait CapabilityFactory {
  def create(urlPrefixes: UrlPrefixes): Capability

  def id(): CapabilityIdentifier
}

final case class CoreCapability(properties: CoreCapabilityProperties,
                                identifier: CapabilityIdentifier = JMAP_CORE) extends Capability

final case class CoreCapabilityFactory(maxUploadSize: MaxSizeUpload) extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JMAP_CORE

  override def create(urlPrefixes: UrlPrefixes): Capability = CoreCapability(CoreCapabilityProperties(
    maxUploadSize,
    MaxConcurrentUpload(4L),
    MaxSizeRequest(10_000_000L),
    MaxConcurrentRequests(4L),
    MaxCallsInRequest(16L),
    MaxObjectsInGet(500L),
    MaxObjectsInSet(500L),
    collationAlgorithms = List("i;unicode-casemap")))
}

case class WebSocketCapability(properties: WebSocketCapabilityProperties, identifier: CapabilityIdentifier = JMAP_WEBSOCKET) extends Capability

case object WebSocketCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JMAP_WEBSOCKET

  override def create(urlPrefixes: UrlPrefixes): Capability = WebSocketCapability(
    WebSocketCapabilityProperties(SupportsPush(true), new URI(urlPrefixes.webSocketURLPrefix + "/jmap/ws")))
}

object MaxSizeUpload {
  def of(size: Size): Try[MaxSizeUpload] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(MaxSizeUpload(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class MaxSizeUpload(value: UnsignedInt)
case class MaxConcurrentUpload(value: UnsignedInt)
case class MaxSizeRequest(value: UnsignedInt)
case class MaxConcurrentRequests(value: UnsignedInt)
case class MaxCallsInRequest(value: UnsignedInt)
case class MaxObjectsInGet(value: UnsignedInt)
case class MaxObjectsInSet(value: UnsignedInt)

object CoreCapabilityProperties {
  type CollationAlgorithm = String Refined NonEmpty
}

final case class CoreCapabilityProperties(maxSizeUpload: MaxSizeUpload,
                                          maxConcurrentUpload: MaxConcurrentUpload,
                                          maxSizeRequest: MaxSizeRequest,
                                          maxConcurrentRequests: MaxConcurrentRequests,
                                          maxCallsInRequest: MaxCallsInRequest,
                                          maxObjectsInGet: MaxObjectsInGet,
                                          maxObjectsInSet: MaxObjectsInSet,
                                          collationAlgorithms: List[CollationAlgorithm]) extends CapabilityProperties {
  override def jsonify(): JsObject = ResponseSerializer.coreCapabilityWrites.writes(this)
}

final case class WebSocketCapabilityProperties(supportsPush: SupportsPush,
                                               url: URI) extends CapabilityProperties {
  override def jsonify(): JsObject = ResponseSerializer.webSocketPropertiesWrites.writes(this)
}

final case class SupportsPush(value: Boolean) extends AnyVal
final case class MaxDelayedSend(value: Int) extends AnyVal
final case class EhloName(value: String) extends AnyVal
final case class EhloArg(value: String) extends AnyVal
final case class EhloArgs(values: List[EhloArg]) extends AnyVal

final case class SubmissionCapability(identifier: CapabilityIdentifier = EMAIL_SUBMISSION,
                                      properties: SubmissionProperties = SubmissionProperties()) extends Capability

case object SubmissionCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = EMAIL_SUBMISSION

  override def create(urlPrefixes: UrlPrefixes): Capability = SubmissionCapability()
}

final case class SubmissionProperties(maxDelayedSend: MaxDelayedSend = MaxDelayedSend(0),
                                      submissionExtensions: Map[EhloName, EhloArgs] = Map()) extends CapabilityProperties {
  override def jsonify(): JsObject = ResponseSerializer.submissionPropertiesWrites.writes(this)
}

object MailCapability {
  type EmailQuerySortOption = String Refined NonEmpty
}

final case class MailCapability(properties: MailCapabilityProperties,
                                identifier: CapabilityIdentifier = JMAP_MAIL) extends Capability

case class MailCapabilityFactory(configuration: JmapRfc8621Configuration) extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JMAP_MAIL

  override def create(urlPrefixes: UrlPrefixes): Capability = MailCapability(MailCapabilityProperties(
    MaxMailboxesPerEmail(Some(10_000_000L)),
    MaxMailboxDepth(None),
    MaxSizeMailboxName(200L),
    configuration.maxSizeAttachmentsPerEmail,
    emailQuerySortOptions = List("receivedAt", "sentAt", "size", "from", "to", "subject"),
    MayCreateTopLevelMailbox(true)))
}


object MaxSizeAttachmentsPerEmail {
  def of(size: Size): Try[MaxSizeAttachmentsPerEmail] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(MaxSizeAttachmentsPerEmail(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class MaxMailboxesPerEmail(value: Option[UnsignedInt])
case class MaxMailboxDepth(value: Option[UnsignedInt])
case class MaxSizeMailboxName(value: UnsignedInt)
case class MaxSizeAttachmentsPerEmail(value: UnsignedInt)
case class MayCreateTopLevelMailbox(value: Boolean) extends AnyVal

final case class MailCapabilityProperties(maxMailboxesPerEmail: MaxMailboxesPerEmail,
                                          maxMailboxDepth: MaxMailboxDepth,
                                          maxSizeMailboxName: MaxSizeMailboxName,
                                          maxSizeAttachmentsPerEmail: MaxSizeAttachmentsPerEmail,
                                          emailQuerySortOptions: List[EmailQuerySortOption],
                                          mayCreateTopLevelMailbox: MayCreateTopLevelMailbox) extends CapabilityProperties {
  override def jsonify(): JsObject = ResponseSerializer.mailCapabilityWrites.writes(this)
}

final case class QuotaCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

final case class QuotaCapability(properties: QuotaCapabilityProperties = QuotaCapabilityProperties(),
                                 identifier: CapabilityIdentifier = JAMES_QUOTA) extends Capability

case object QuotaCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JAMES_QUOTA

  override def create(urlPrefixes: UrlPrefixes): Capability = QuotaCapability()
}

final case class IdentitySortOrderCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

final case class IdentitySortOrderCapability(properties: IdentitySortOrderCapabilityProperties = IdentitySortOrderCapabilityProperties(),
                                             identifier: CapabilityIdentifier = JAMES_IDENTITY_SORTORDER) extends Capability

case object IdentitySortOrderCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JAMES_IDENTITY_SORTORDER

  override def create(urlPrefixes: UrlPrefixes): Capability = IdentitySortOrderCapability()
}

final case class DelegationCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

final case class DelegationCapability(properties: DelegationCapabilityProperties = DelegationCapabilityProperties(),
                                      identifier: CapabilityIdentifier = JAMES_DELEGATION) extends Capability

case object DelegationCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JAMES_DELEGATION

  override def create(urlPrefixes: UrlPrefixes): Capability = DelegationCapability()
}

final case class SharesCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object SharesCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JAMES_SHARES

  override def create(urlPrefixes: UrlPrefixes): Capability = SharesCapability()
}

final case class SharesCapability(properties: SharesCapabilityProperties = SharesCapabilityProperties(),
                                  identifier: CapabilityIdentifier = JAMES_SHARES) extends Capability

final case class MDNCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object MDNCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JMAP_MDN

  override def create(urlPrefixes: UrlPrefixes): Capability = MDNCapability()
}

final case class MDNCapability(properties: MDNCapabilityProperties = MDNCapabilityProperties(),
                               identifier: CapabilityIdentifier = JMAP_MDN) extends Capability

final case class VacationResponseCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object VacationResponseCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JMAP_VACATION_RESPONSE

  override def create(urlPrefixes: UrlPrefixes): Capability = VacationResponseCapability()
}

final case class VacationResponseCapability(properties: VacationResponseCapabilityProperties = VacationResponseCapabilityProperties(),
                                            identifier: CapabilityIdentifier = JMAP_VACATION_RESPONSE) extends Capability

final case class JmapQuotaCapability(properties: JmapQuotaCapabilityProperties = JmapQuotaCapabilityProperties(),
                                     identifier: CapabilityIdentifier = JMAP_QUOTA) extends Capability

final case class JmapQuotaCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object JmapQuotaCapabilityFactory extends CapabilityFactory {
  override def id(): CapabilityIdentifier = JMAP_QUOTA

  override def create(urlPrefixes: UrlPrefixes): Capability = JmapQuotaCapability()
}