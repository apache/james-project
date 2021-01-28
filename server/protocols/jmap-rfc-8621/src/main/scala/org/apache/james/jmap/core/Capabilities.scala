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

import java.net.URL

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier

object DefaultCapabilities {
  def coreCapability(maxUploadSize: MaxSizeUpload) = CoreCapability(
    properties = CoreCapabilityProperties(
      maxUploadSize,
      MaxConcurrentUpload(4L),
      MaxSizeRequest(10_000_000L),
      MaxConcurrentRequests(4L),
      MaxCallsInRequest(16L),
      MaxObjectsInGet(500L),
      MaxObjectsInSet(500L),
      collationAlgorithms = List("i;unicode-casemap")))

  def webSocketCapability(url: URL) = WebSocketCapability(
    properties = WebSocketCapabilityProperties(SupportsPush(false), url))

  val MAIL_CAPABILITY = MailCapability(
    properties = MailCapabilityProperties(
      MaxMailboxesPerEmail(Some(10_000_000L)),
      MaxMailboxDepth(None),
      MaxSizeMailboxName(200L),
      MaxSizeAttachmentsPerEmail(20_000_000L),
      emailQuerySortOptions = List("receivedAt", "sentAt"),
      MayCreateTopLevelMailbox(true)))
  val QUOTA_CAPABILITY = QuotaCapability()
  val SHARES_CAPABILITY = SharesCapability()
  val VACATION_RESPONSE_CAPABILITY = VacationResponseCapability()
  val SUBMISSION_CAPABILITY = SubmissionCapability()

  def supported(configuration: JmapRfc8621Configuration): Capabilities = Capabilities(
    coreCapability(configuration.maxUploadSize),
    MAIL_CAPABILITY,
    QUOTA_CAPABILITY,
    SHARES_CAPABILITY,
    VACATION_RESPONSE_CAPABILITY,
    SUBMISSION_CAPABILITY,
    webSocketCapability(configuration.webSocketUrl))
}

case class Capabilities(capabilities: Capability*) {
  def toSet: Set[Capability] = capabilities.toSet

  def ids: Set[CapabilityIdentifier] = toSet.map(_.identifier())
}
