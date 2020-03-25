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

import java.net.URI

import org.apache.james.jmap.model.CapabilityIdentifier.{JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.model.UnsignedInt.UnsignedInt

final case class CapabilityIdentifier(value: URI) {
  val asString: String = value.toString
}

object CapabilityIdentifier {
  val JMAP_CORE = CapabilityIdentifier(new URI("urn:ietf:params:jmap:core"))
  val JMAP_MAIL = CapabilityIdentifier(new URI("urn:ietf:params:jmap:mail"))
}

sealed trait CapabilityProperties

sealed trait Capability {
  def identifier(): CapabilityIdentifier
  def properties(): CapabilityProperties
}

final case class CoreCapability(identifier: CapabilityIdentifier = JMAP_CORE,
                                properties: CoreCapabilityProperties) extends Capability

case class MaxSizeUpload(value: UnsignedInt)
case class MaxConcurrentUpload(value: UnsignedInt)
case class MaxSizeRequest(value: UnsignedInt)
case class MaxConcurrentRequests(value: UnsignedInt)
case class MaxCallsInRequest(value: UnsignedInt)
case class MaxObjectsInGet(value: UnsignedInt)
case class MaxObjectsInSet(value: UnsignedInt)
case class CollationAlgorithms(value: List[String])

final case class CoreCapabilityProperties(maxSizeUpload: MaxSizeUpload,
                                          maxConcurrentUpload: MaxConcurrentUpload,
                                          maxSizeRequest: MaxSizeRequest,
                                          maxConcurrentRequests: MaxConcurrentRequests,
                                          maxCallsInRequest: MaxCallsInRequest,
                                          maxObjectsInGet: MaxObjectsInGet,
                                          maxObjectsInSet: MaxObjectsInSet,
                                          collationAlgorithms: CollationAlgorithms) extends CapabilityProperties {
}

final case class MailCapability(identifier: CapabilityIdentifier = JMAP_MAIL,
                                properties: MailCapabilityProperties) extends Capability

case class MaxMailboxesPerEmail(value: Option[UnsignedInt])
case class MaxMailboxDepth(value: Option[UnsignedInt])
case class MaxSizeMailboxName(value: UnsignedInt)
case class MaxSizeAttachmentsPerEmail(value: UnsignedInt)
case class EmailQuerySortOptions(value: List[String])
case class MayCreateTopLevelMailbox(value: Boolean)

final case class MailCapabilityProperties(maxMailboxesPerEmail: MaxMailboxesPerEmail,
                                          maxMailboxDepth: MaxMailboxDepth,
                                          maxSizeMailboxName: MaxSizeMailboxName,
                                          maxSizeAttachmentsPerEmail: MaxSizeAttachmentsPerEmail,
                                          emailQuerySortOptions: EmailQuerySortOptions,
                                          mayCreateTopLevelMailbox: MayCreateTopLevelMailbox) extends CapabilityProperties {
}

