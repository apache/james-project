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

object CoreCapabilityProperties {
  def apply(maxSizeUpload: UnsignedInt, maxConcurrentUpload: UnsignedInt,
            maxSizeRequest: UnsignedInt, maxConcurrentRequests: UnsignedInt,
            maxCallsInRequest: UnsignedInt, maxObjectsInGet: UnsignedInt,
            maxObjectsInSet: UnsignedInt, collationAlgorithms: List[String]): CoreCapabilityProperties = {

    new CoreCapabilityProperties(maxSizeUpload, maxConcurrentUpload, maxSizeRequest, maxConcurrentRequests, maxCallsInRequest,
      maxObjectsInGet, maxObjectsInSet, collationAlgorithms)
  }
}

final case class CoreCapabilityProperties private(maxSizeUpload: UnsignedInt, maxConcurrentUpload: UnsignedInt,
                                                  maxSizeRequest: UnsignedInt, maxConcurrentRequests: UnsignedInt,
                                                  maxCallsInRequest: UnsignedInt, maxObjectsInGet: UnsignedInt,
                                                  maxObjectsInSet: UnsignedInt, collationAlgorithms: List[String]) extends CapabilityProperties {
}

final case class MailCapability(identifier: CapabilityIdentifier = JMAP_MAIL,
                                properties: MailCapabilityProperties) extends Capability

object MailCapabilityProperties {
  def apply(maxMailboxesPerEmail: Option[UnsignedInt], maxMailboxDepth: Option[UnsignedInt],
            maxSizeMailboxName: UnsignedInt, maxSizeAttachmentsPerEmail: UnsignedInt,
            emailQuerySortOptions: List[String], mayCreateTopLevelMailbox: Boolean): MailCapabilityProperties = {

    new MailCapabilityProperties(maxMailboxesPerEmail, maxMailboxDepth, maxSizeMailboxName, maxSizeAttachmentsPerEmail,
      emailQuerySortOptions, mayCreateTopLevelMailbox)
  }
}

final case class MailCapabilityProperties private(maxMailboxesPerEmail: Option[UnsignedInt], maxMailboxDepth: Option[UnsignedInt],
                                                  maxSizeMailboxName: UnsignedInt, maxSizeAttachmentsPerEmail: UnsignedInt,
                                                  emailQuerySortOptions: List[String], mayCreateTopLevelMailbox: Boolean) extends CapabilityProperties {
}

