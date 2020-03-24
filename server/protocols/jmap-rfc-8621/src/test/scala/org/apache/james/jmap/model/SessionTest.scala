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

import java.net.{URL => JavaNetURL}

import org.apache.james.core.Username
import org.apache.james.jmap.model.SessionTest._
import org.scalatest.{Matchers, WordSpec}
import eu.timepit.refined.auto._
import org.apache.james.jmap.model.UnsignedInt.UnsignedInt

object SessionTest {
  private val USERNAME = Username.of("bob@james.org")
  private val URL = new JavaNetURL("http://james.org")
  private val STATE = State("fda9342jcm")
  private val UNSIGNED_INT : UnsignedInt = 1L
  private val CORE_CAPABILITY = CoreCapability(properties = CoreCapabilityProperties(
    maxSizeUpload = UNSIGNED_INT,
    maxCallsInRequest = UNSIGNED_INT,
    maxConcurrentUpload = UNSIGNED_INT,
    maxSizeRequest = UNSIGNED_INT,
    maxConcurrentRequests = UNSIGNED_INT,
    maxObjectsInGet = UNSIGNED_INT,
    maxObjectsInSet = UNSIGNED_INT,
    collationAlgorithms = List()))
  private val MAIL_CAPABILITY = MailCapability(properties = MailCapabilityProperties(
    maxMailboxDepth = Some(UNSIGNED_INT),
    maxMailboxesPerEmail = Some(UNSIGNED_INT),
    maxSizeMailboxName = UNSIGNED_INT,
    maxSizeAttachmentsPerEmail = UNSIGNED_INT,
    emailQuerySortOptions = List(),
    mayCreateTopLevelMailbox = true))
  private val ANOTHER_MAIL_CAPABILITY = MailCapability(properties = MailCapabilityProperties(
    maxMailboxDepth = Some(UNSIGNED_INT),
    maxMailboxesPerEmail = Some(UNSIGNED_INT),
    maxSizeMailboxName = UNSIGNED_INT,
    maxSizeAttachmentsPerEmail = UNSIGNED_INT,
    emailQuerySortOptions = List("flags", "subject"),
    mayCreateTopLevelMailbox = false))
}

class SessionTest extends WordSpec with Matchers {


  "State" should {

    "throw when empty value" in {
      the [IllegalArgumentException] thrownBy {
        State("")
      } should have message "requirement failed: value cannot be empty"
    }
  }

  "apply" should {

    "throw when missing core capability" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = Set(MAIL_CAPABILITY),
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
          capabilities = Set(CORE_CAPABILITY),
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

    "throw when missing duplicate capability identifier" in {
      the [IllegalArgumentException] thrownBy {
        Session(
          capabilities = Set(CORE_CAPABILITY, MAIL_CAPABILITY, ANOTHER_MAIL_CAPABILITY),
          accounts = Map(),
          primaryAccounts = Map(),
          username = USERNAME,
          apiUrl = URL,
          downloadUrl = URL,
          uploadUrl = URL,
          eventSourceUrl = URL,
          state = STATE)
      } should have message "requirement failed: capabilities should not be duplicated"
    }
  }
}
