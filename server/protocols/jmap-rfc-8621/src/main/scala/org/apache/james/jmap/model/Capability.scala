/** *************************************************************
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

package org.apache.james.jmap.model

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Uri
import org.apache.james.jmap.model.CapabilityIdentifier.{CapabilityIdentifier, JAMES_QUOTA, JAMES_SHARES, JMAP_CORE, JMAP_MAIL, JMAP_VACATION_RESPONSE}
import org.apache.james.jmap.model.CoreCapabilityProperties.CollationAlgorithm
import org.apache.james.jmap.model.MailCapability.EmailQuerySortOption
import org.apache.james.jmap.model.UnsignedInt.UnsignedInt

object CapabilityIdentifier {
  type CapabilityIdentifier = String Refined Uri
  val JMAP_CORE: CapabilityIdentifier = "urn:ietf:params:jmap:core"
  val JMAP_MAIL: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  val JMAP_VACATION_RESPONSE: CapabilityIdentifier = "urn:ietf:params:jmap:vacationresponse"
  val JAMES_QUOTA: CapabilityIdentifier = "urn:apache:james:params:jmap:mail:quota"
  val JAMES_SHARES: CapabilityIdentifier = "urn:apache:james:params:jmap:mail:shares"
}

sealed trait CapabilityProperties

trait Capability {
  def identifier(): CapabilityIdentifier
  def properties(): CapabilityProperties
}

final case class CoreCapability(properties: CoreCapabilityProperties,
                                identifier: CapabilityIdentifier = JMAP_CORE) extends Capability

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
}

object MailCapability {
  type EmailQuerySortOption = String Refined NonEmpty
}

final case class MailCapability(properties: MailCapabilityProperties,
                                identifier: CapabilityIdentifier = JMAP_MAIL) extends Capability

case class MaxMailboxesPerEmail(value: Option[UnsignedInt])
case class MaxMailboxDepth(value: Option[UnsignedInt])
case class MaxSizeMailboxName(value: UnsignedInt)
case class MaxSizeAttachmentsPerEmail(value: UnsignedInt)
case class MayCreateTopLevelMailbox(value: Boolean)

final case class MailCapabilityProperties(maxMailboxesPerEmail: MaxMailboxesPerEmail,
                                          maxMailboxDepth: MaxMailboxDepth,
                                          maxSizeMailboxName: MaxSizeMailboxName,
                                          maxSizeAttachmentsPerEmail: MaxSizeAttachmentsPerEmail,
                                          emailQuerySortOptions: List[EmailQuerySortOption],
                                          mayCreateTopLevelMailbox: MayCreateTopLevelMailbox) extends CapabilityProperties

final case class QuotaCapabilityProperties() extends CapabilityProperties

final case class QuotaCapability(properties: QuotaCapabilityProperties = QuotaCapabilityProperties(),
                                 identifier: CapabilityIdentifier = JAMES_QUOTA) extends Capability

final case class SharesCapabilityProperties() extends CapabilityProperties

final case class SharesCapability(properties: SharesCapabilityProperties = SharesCapabilityProperties(),
                                  identifier: CapabilityIdentifier = JAMES_SHARES) extends Capability

final case class VacationResponseCapabilityProperties() extends CapabilityProperties

final case class VacationResponseCapability(properties: VacationResponseCapabilityProperties = VacationResponseCapabilityProperties(),
                                            identifier: CapabilityIdentifier = JMAP_VACATION_RESPONSE) extends Capability
