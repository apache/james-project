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

package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.CoreCapabilityProperties.CollationAlgorithm
import org.apache.james.jmap.core.MailCapability.EmailQuerySortOption
import org.apache.james.jmap.core.{Account, Capabilities, CoreCapability, CoreCapabilityProperties, IsPersonal, IsReadOnly, MailCapability, MailCapabilityProperties, MaxCallsInRequest, MaxConcurrentRequests, MaxConcurrentUpload, MaxMailboxDepth, MaxMailboxesPerEmail, MaxObjectsInGet, MaxObjectsInSet, MaxSizeAttachmentsPerEmail, MaxSizeMailboxName, MaxSizeRequest, MaxSizeUpload, MayCreateTopLevelMailbox, QuotaCapability, Session, SharesCapability, UuidState, VacationResponseCapability}
import org.apache.james.jmap.json.SessionSerializationTest.SESSION
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.io.Source
import scala.util.Using

object SessionSerializationTest {
  private val ALGO_1: CollationAlgorithm = "i;ascii-numeric"
  private val ALGO_2: CollationAlgorithm = "i;ascii-casemap"
  private val ALGO_3: CollationAlgorithm = "i;unicode-casemap"
  private val MAX_SIZE_UPLOAD: MaxSizeUpload = MaxSizeUpload(50000000L)
  private val MAX_CONCURRENT_UPLOAD : MaxConcurrentUpload = MaxConcurrentUpload(8L)
  private val MAX_SIZE_REQUEST : MaxSizeRequest = MaxSizeRequest(10000000L)
  private val MAX_CONCURRENT_REQUESTS : MaxConcurrentRequests = MaxConcurrentRequests(10000000L)
  private val MAX_CALLS_IN_REQUEST : MaxCallsInRequest = MaxCallsInRequest(32L)
  private val MAX_OBJECTS_IN_GET : MaxObjectsInGet = MaxObjectsInGet(256L)
  private val MAX_OBJECTS_IN_SET : MaxObjectsInSet = MaxObjectsInSet(128L)
  private val COLLATION_ALGORITHMS : List[CollationAlgorithm] = List(ALGO_1, ALGO_2, ALGO_3)
  private val USER_1 = Username.of("user1@james.org")
  private val USER_2 = Username.of("user2@james.org")
  private val URL = org.apache.james.jmap.core.URL("http://james.org")

  private val MAIL_IDENTIFIER: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  private val CONTACT_IDENTIFIER: CapabilityIdentifier = "urn:ietf:params:jmap:contact"

  private val CORE_CAPABILITY = CoreCapability(properties = CoreCapabilityProperties(
    maxSizeUpload = MAX_SIZE_UPLOAD, maxConcurrentUpload = MAX_CONCURRENT_UPLOAD,
    maxSizeRequest = MAX_SIZE_REQUEST, maxConcurrentRequests = MAX_CONCURRENT_REQUESTS,
    maxCallsInRequest = MAX_CALLS_IN_REQUEST, maxObjectsInGet = MAX_OBJECTS_IN_GET, maxObjectsInSet = MAX_OBJECTS_IN_SET,
    collationAlgorithms = COLLATION_ALGORITHMS))

  private val MAX_MAILBOX_DEPTH : MaxMailboxDepth = MaxMailboxDepth(Some(1432L))
  private val MAX_MAILBOXES_PER_EMAIL : MaxMailboxesPerEmail = MaxMailboxesPerEmail(Some(9359L))
  private val MAX_SIZE_MAILBOX_NAME : MaxSizeMailboxName = MaxSizeMailboxName(9000L)
  private val MAX_SIZE_ATTACHMENTS_PER_EMAIL : MaxSizeAttachmentsPerEmail = MaxSizeAttachmentsPerEmail(890099L)
  private val SIZE_QUERY_SORT_OPTION : EmailQuerySortOption = "size"
  private val EMAIL_QUERY_SORT_OPTIONS : List[EmailQuerySortOption] = List(SIZE_QUERY_SORT_OPTION)
  private val MAY_CREATE_TOP_LEVEL_MAILBOX : MayCreateTopLevelMailbox = MayCreateTopLevelMailbox(true)

  private val MAIL_CAPABILITY = MailCapability(properties = MailCapabilityProperties(
    maxMailboxDepth = MAX_MAILBOX_DEPTH,
    maxMailboxesPerEmail = MAX_MAILBOXES_PER_EMAIL,
    maxSizeMailboxName = MAX_SIZE_MAILBOX_NAME,
    maxSizeAttachmentsPerEmail = MAX_SIZE_ATTACHMENTS_PER_EMAIL,
    emailQuerySortOptions = EMAIL_QUERY_SORT_OPTIONS,
    mayCreateTopLevelMailbox = MAY_CREATE_TOP_LEVEL_MAILBOX))

  private val CAPABILITIES = Capabilities.of(CORE_CAPABILITY, MAIL_CAPABILITY, QuotaCapability(), SharesCapability(), VacationResponseCapability())

  private val IS_PERSONAL : IsPersonal = IsPersonal(true)
  private val IS_NOT_PERSONAL : IsPersonal = IsPersonal(false)
  private val IS_NOT_READ_ONLY : IsReadOnly = IsReadOnly(false)

  private val ACCOUNT_1: Account = Account.from(
    name = USER_1,
    isPersonal = IS_PERSONAL,
    isReadOnly = IS_NOT_READ_ONLY,
    accountCapabilities = Set(CORE_CAPABILITY)).toOption.get

  private val ACCOUNT_2: Account = Account.from(
    name = USER_2,
    isPersonal = IS_NOT_PERSONAL,
    isReadOnly = IS_NOT_READ_ONLY,
    accountCapabilities = Set(CORE_CAPABILITY)).toOption.get

  private val PRIMARY_ACCOUNTS = Map(
    MAIL_IDENTIFIER -> ACCOUNT_1.accountId,
    CONTACT_IDENTIFIER -> ACCOUNT_1.accountId
  )

  private val SESSION = Session(
    capabilities = CAPABILITIES,
    accounts = List(ACCOUNT_1, ACCOUNT_2),
    primaryAccounts = PRIMARY_ACCOUNTS,
    username = USER_1,
    apiUrl = URL,
    downloadUrl = URL,
    uploadUrl = URL,
    eventSourceUrl = URL,
    state = UuidState.INSTANCE)

  def readResource(resourceFileName: String): String = {
    Using(Source.fromURL(getClass.getResource(resourceFileName), "UTF-8")) { source =>
      source.mkString
    }.get
  }
}

class SessionSerializationTest extends AnyWordSpec with Matchers {

  "sessionWrites" should {
    "serialize session" in {
      val json = Json.parse(
        s"""{
          |  "capabilities": {
          |    "urn:ietf:params:jmap:core": {
          |      "maxSizeUpload": 50000000,
          |      "maxConcurrentUpload": 8,
          |      "maxSizeRequest": 10000000,
          |      "maxConcurrentRequests": 10000000,
          |      "maxCallsInRequest": 32,
          |      "maxObjectsInGet": 256,
          |      "maxObjectsInSet": 128,
          |      "collationAlgorithms": [
          |        "i;ascii-numeric",
          |        "i;ascii-casemap",
          |        "i;unicode-casemap"
          |      ]
          |    },
          |    "urn:ietf:params:jmap:mail": {
          |      "maxMailboxesPerEmail": 9359,
          |      "maxMailboxDepth": 1432,
          |      "maxSizeMailboxName": 9000,
          |      "maxSizeAttachmentsPerEmail": 890099,
          |      "emailQuerySortOptions": ["size"],
          |      "mayCreateTopLevelMailbox": true
          |    },
          |    "urn:apache:james:params:jmap:mail:quota":{},
          |    "urn:apache:james:params:jmap:mail:shares":{},
          |    "urn:ietf:params:jmap:vacationresponse":{}
          |  },
          |  "accounts": {
          |    "807a5306ccb4527af7790a0f9b48a776514bdbfba064e355461a76bcffbf2c90": {
          |      "name": "user1@james.org",
          |      "isPersonal": true,
          |      "isReadOnly": false,
          |      "accountCapabilities": {
          |        "urn:ietf:params:jmap:core": {
          |          "maxSizeUpload": 50000000,
          |          "maxConcurrentUpload": 8,
          |          "maxSizeRequest": 10000000,
          |          "maxConcurrentRequests": 10000000,
          |          "maxCallsInRequest": 32,
          |          "maxObjectsInGet": 256,
          |          "maxObjectsInSet": 128,
          |          "collationAlgorithms": [
          |            "i;ascii-numeric",
          |            "i;ascii-casemap",
          |            "i;unicode-casemap"
          |          ]
          |        }
          |      }
          |    },
          |    "a9b46834e106ff73268a40a34ffba9fcfeee8bdb601939d1a96ef9199dc2695c": {
          |      "name": "user2@james.org",
          |      "isPersonal": false,
          |      "isReadOnly": false,
          |      "accountCapabilities": {
          |        "urn:ietf:params:jmap:core": {
          |          "maxSizeUpload": 50000000,
          |          "maxConcurrentUpload": 8,
          |          "maxSizeRequest": 10000000,
          |          "maxConcurrentRequests": 10000000,
          |          "maxCallsInRequest": 32,
          |          "maxObjectsInGet": 256,
          |          "maxObjectsInSet": 128,
          |          "collationAlgorithms": [
          |            "i;ascii-numeric",
          |            "i;ascii-casemap",
          |            "i;unicode-casemap"
          |          ]
          |        }
          |      }
          |    }
          |  },
          |  "primaryAccounts": {
          |    "urn:ietf:params:jmap:mail": "807a5306ccb4527af7790a0f9b48a776514bdbfba064e355461a76bcffbf2c90",
          |    "urn:ietf:params:jmap:contact": "807a5306ccb4527af7790a0f9b48a776514bdbfba064e355461a76bcffbf2c90"
          |  },
          |  "username": "user1@james.org",
          |  "apiUrl": "http://james.org",
          |  "downloadUrl": "http://james.org",
          |  "uploadUrl": "http://james.org",
          |  "eventSourceUrl": "http://james.org",
          |  "state": "${UuidState.INSTANCE.value}"
          |}""".stripMargin)
      ResponseSerializer.serialize(SESSION) should equal(json)
    }
  }
}
