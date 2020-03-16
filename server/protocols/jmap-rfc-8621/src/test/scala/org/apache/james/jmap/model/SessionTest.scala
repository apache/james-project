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

import org.apache.james.core.Username
import org.apache.james.jmap.model.SessionTest._
import org.scalatest.{Matchers, WordSpec}

object SessionTest {
  private val USERNAME = Username.of("bob@james.org")
  private val URL = "http://james.org"
  private val STATE = "fda9342jcm"
  private val UNSIGNED_INT = UnsignedInt(1)
  private val CORE_CAPABILITY = CoreCapability(
    maxSizeUpload = UNSIGNED_INT,
    maxCallsInRequest = UNSIGNED_INT,
    maxConcurrentUpload = UNSIGNED_INT,
    maxSizeRequest = UNSIGNED_INT,
    maxConcurrentRequests = UNSIGNED_INT,
    maxObjectsInGet = UNSIGNED_INT,
    maxObjectsInSet = UNSIGNED_INT,
    collationAlgorithms = List())
  private val MAIL_CAPABILITY = MailCapability(
    maxMailboxDepth = Some(UNSIGNED_INT),
    maxMailboxesPerEmail = Some(UNSIGNED_INT),
    maxSizeMailboxName = UNSIGNED_INT,
    maxSizeAttachmentsPerEmail = UNSIGNED_INT,
    emailQuerySortOptions = List(),
    mayCreateTopLevelMailbox = true)
  private val CAPABILITIES = Map(
    CapabilityIdentifier.JMAP_CORE -> CORE_CAPABILITY,
    CapabilityIdentifier.JMAP_MAIL -> MAIL_CAPABILITY)
}

class SessionTest extends WordSpec with Matchers {

  "Account" should {
    "throw when null name" in {
      the [IllegalArgumentException] thrownBy {
        Account(
          name = null,
          isPersonal = true,
          isReadOnly = false,
          accountCapabilities = Map())
      } should have message "requirement failed: name cannot be null"
    }

    "throw when null accountCapabilities" in {
      the [IllegalArgumentException] thrownBy {
        Account(
          name = USERNAME,
          isPersonal = true,
          isReadOnly = false,
          accountCapabilities = null)
      } should have message "requirement failed: accountCapabilities cannot be null"
    }
  }

  "apply" should {
    "throw when null capabilities" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = null,
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: capabilities cannot be null"
    }

    "throw when missing core capability" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = Map(CapabilityIdentifier.JMAP_MAIL -> MAIL_CAPABILITY),
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: capabilities should contain urn:ietf:params:jmap:core capability"
    }

    "throw when missing mail capability" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = Map(CapabilityIdentifier.JMAP_CORE -> CORE_CAPABILITY),
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: capabilities should contain urn:ietf:params:jmap:mail capability"
    }

    "throw when null accounts" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = null,
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: accounts cannot be null"
    }

    "throw when null primaryAccount" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = null,
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: primaryAccounts cannot be null"
    }

    "throw when null username" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = Map(),
          username = null,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: username cannot be null"
    }

    "throw when null apiUrl" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = null,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: apiUrl cannot be null"
    }

    "throw when null downloadUrl" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = null,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: downloadUrl cannot be null"
    }

    "throw when null uploadUrl" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = null,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: uploadUrl cannot be null"
    }

    "throw when null eventSourceUrl" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = null,
          state = STATE)
      } should have message "requirement failed: eventSourceUrl cannot be null"
    }

    "throw when null state" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = CAPABILITIES,
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = null)
      } should have message "requirement failed: state cannot be null"
    }
  }
}
