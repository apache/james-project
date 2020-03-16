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

final case class CapabilityIdentifier(value: URI) {
  def asString(): String = {
    value.toString
  }
}

object CapabilityIdentifier {
  val JMAP_CORE = CapabilityIdentifier(new URI("urn:ietf:params:jmap:core"))
  val JMAP_MAIL = CapabilityIdentifier(new URI("urn:ietf:params:jmap:mail"))
}

sealed trait Capability

final case class CoreCapability(maxSizeUpload: UnsignedInt, maxConcurrentUpload: UnsignedInt,
                                maxSizeRequest: UnsignedInt, maxConcurrentRequests: UnsignedInt,
                                maxCallsInRequest: UnsignedInt, maxObjectsInGet: UnsignedInt,
                                maxObjectsInSet: UnsignedInt, collationAlgorithms: List[String]) extends Capability {
  require(Option(maxSizeUpload).isDefined, "maxSizeUpload cannot be null")
  require(Option(maxConcurrentUpload).isDefined, "maxConcurrentUpload cannot be null")
  require(Option(maxSizeRequest).isDefined, "maxSizeRequest cannot be null")
  require(Option(maxConcurrentRequests).isDefined, "maxConcurrentRequests cannot be null")
  require(Option(maxCallsInRequest).isDefined, "maxCallsInRequest cannot be null")
  require(Option(maxObjectsInGet).isDefined, "maxObjectsInGet cannot be null")
  require(Option(maxObjectsInSet).isDefined, "maxObjectsInSet cannot be null")
  require(Option(collationAlgorithms).isDefined, "collationAlgorithms cannot be null")
}

final case class MailCapability(maxMailboxesPerEmail: Option[UnsignedInt], maxMailboxDepth: Option[UnsignedInt],
                                maxSizeMailboxName: UnsignedInt, maxSizeAttachmentsPerEmail: UnsignedInt,
                                emailQuerySortOptions: List[String], mayCreateTopLevelMailbox: Boolean) extends Capability {
  require(Option(maxSizeMailboxName).isDefined, "maxSizeMailboxName cannot be null")
  require(Option(maxSizeAttachmentsPerEmail).isDefined, "maxSizeAttachmentsPerEmail cannot be null")
  require(Option(emailQuerySortOptions).isDefined, "emailQuerySortOptions cannot be null")
}

