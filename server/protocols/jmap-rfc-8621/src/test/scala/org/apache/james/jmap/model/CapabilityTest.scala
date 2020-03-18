/** **************************************************************
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

import org.apache.james.jmap.model.CapabilityTest.UNSIGNED_INT
import org.scalatest.{Matchers, WordSpec}

object CapabilityTest {
  private val UNSIGNED_INT = UnsignedInt(1)
}

class CapabilityTest extends WordSpec with Matchers {

  "CapabilityIdentifier" should {
    "return raw value" in {
      val uri = "apache:james:jmap:filter"
      val asString = CapabilityIdentifier(new URI(uri)).asString()
      asString should equal(uri)
    }
  }

  "CoreCapabilityProperties" should {
    "throw when null maxSizeUpload" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = null,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = List())
      } should have message "requirement failed: maxSizeUpload cannot be null"
    }

    "throw when null maxCallsInRequest" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = null,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = List())
      } should have message "requirement failed: maxCallsInRequest cannot be null"
    }

    "throw when null maxConcurrentUpload" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = null,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = List())
      } should have message "requirement failed: maxConcurrentUpload cannot be null"
    }

    "throw when null maxSizeRequest" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = null,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = List())
      } should have message "requirement failed: maxSizeRequest cannot be null"
    }

    "throw when null maxConcurrentRequests" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = null,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = List())
      } should have message "requirement failed: maxConcurrentRequests cannot be null"
    }

    "throw when null maxObjectsInGet" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = null,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = List())
      } should have message "requirement failed: maxObjectsInGet cannot be null"
    }

    "throw when null maxObjectsInSet" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = null,
          collationAlgorithms = List())
      } should have message "requirement failed: maxObjectsInSet cannot be null"
    }

    "throw when null collationAlgorithms" in {
      the [IllegalArgumentException] thrownBy {
        CoreCapabilityProperties(
          maxSizeUpload = UNSIGNED_INT,
          maxCallsInRequest = UNSIGNED_INT,
          maxConcurrentUpload = UNSIGNED_INT,
          maxSizeRequest = UNSIGNED_INT,
          maxConcurrentRequests = UNSIGNED_INT,
          maxObjectsInGet = UNSIGNED_INT,
          maxObjectsInSet = UNSIGNED_INT,
          collationAlgorithms = null)
      } should have message "requirement failed: collationAlgorithms cannot be null"
    }
  }

  "MailCapabilityProperties" should {
    "throw when null maxSizeMailboxName" in {
      the [IllegalArgumentException] thrownBy {
        MailCapabilityProperties(
          maxMailboxesPerEmail = Some(UNSIGNED_INT),
          maxMailboxDepth = Some(UNSIGNED_INT),
          maxSizeMailboxName = null,
          maxSizeAttachmentsPerEmail = UNSIGNED_INT,
          emailQuerySortOptions = List(),
          mayCreateTopLevelMailbox = true)
      } should have message "requirement failed: maxSizeMailboxName cannot be null"
    }

    "throw when null maxSizeAttachmentsPerEmail" in {
      the [IllegalArgumentException] thrownBy {
        MailCapabilityProperties(
          maxMailboxesPerEmail = Some(UNSIGNED_INT),
          maxMailboxDepth = Some(UNSIGNED_INT),
          maxSizeMailboxName = UNSIGNED_INT,
          maxSizeAttachmentsPerEmail = null,
          emailQuerySortOptions = List(),
          mayCreateTopLevelMailbox = true)
      } should have message "requirement failed: maxSizeAttachmentsPerEmail cannot be null"
    }

    "throw when null mayCreateTopLevelMailbox" in {
      the [IllegalArgumentException] thrownBy {
        MailCapabilityProperties(
          maxMailboxesPerEmail = Some(UNSIGNED_INT),
          maxMailboxDepth = Some(UNSIGNED_INT),
          maxSizeMailboxName = UNSIGNED_INT,
          maxSizeAttachmentsPerEmail = UNSIGNED_INT,
          emailQuerySortOptions = null,
          mayCreateTopLevelMailbox = true)
      } should have message "requirement failed: emailQuerySortOptions cannot be null"
    }
  }
}
