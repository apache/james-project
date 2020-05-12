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
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.jmap.json.Fixture._
import org.apache.james.jmap.json.MailboxGetSerializationTest._
import org.apache.james.jmap.json.MailboxSerializationTest.MAILBOX
import org.apache.james.jmap.mail._
import org.apache.james.jmap.model.AccountId
import org.apache.james.mailbox.model.{MailboxId, TestId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}

object MailboxGetSerializationTest {
  private val FACTORY: MailboxId.Factory = new TestId.Factory

  private val SERIALIZER: Serializer = new Serializer(FACTORY)

  private val ACCOUNT_ID: AccountId = AccountId(id)

  private val MAILBOX_ID_1: MailboxId = FACTORY.fromString("1")
  private val MAILBOX_ID_2: MailboxId = FACTORY.fromString("2")

  private val PROPERTIES: Properties = Properties(List("name", "role"))
}

class MailboxGetSerializationTest extends AnyWordSpec with Matchers {
  "Deserialize MailboxGetRequest" should {
    "fail when MailboxId.Factory can't deserialize MailboxId" in {
      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["ab#?"]
          |}
          |""".stripMargin) shouldBe a [JsError]
    }

    "succeed when properties are missing" in {
      val expectedRequestObject = MailboxGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(Ids(List(MAILBOX_ID_1))),
        properties = None)

      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["1"]
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are null" in {
      val expectedRequestObject = MailboxGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(Ids(List(MAILBOX_ID_1))),
        properties = None)

      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["1"],
          |  "properties": null
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are empty" in {
      val expectedRequestObject = MailboxGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(Ids(List(MAILBOX_ID_1))),
        properties = Some(Properties(Nil)))

      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["1"],
          |  "properties": []
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when ids is empty" in {
      val expectedRequestObject = MailboxGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(Ids(Nil)),
        properties = None)

      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": []
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when ids is null" in {
      val expectedRequestObject = MailboxGetRequest(
        accountId = ACCOUNT_ID,
        ids = None,
        properties = None)

      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": null
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed" in {
      val expectedRequestObject = MailboxGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(Ids(List(MAILBOX_ID_1, MAILBOX_ID_2))),
        properties = Some(PROPERTIES))

      SERIALIZER.deserializeMailboxGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["1", "2"],
          |  "properties": ["name", "role"]
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }
  }

  "Serialize MailboxGetResponse" should {
    "succeed" in {
      val actualValue: MailboxGetResponse = MailboxGetResponse(
        accountId = ACCOUNT_ID,
        state = "75128aab4b1b",
        list = List(MAILBOX),
        notFound = NotFound(List(MAILBOX_ID_1, MAILBOX_ID_2)))

      val expectedJson = Json.parse(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "state": "75128aab4b1b",
          |  "list": [{
          |    "id":"2",
          |    "name":"inbox",
          |    "parentId":"1",
          |    "role":"inbox",
          |    "sortOrder":10,
          |    "totalEmails":1234,
          |    "unreadEmails":123,
          |    "totalThreads":58,
          |    "unreadThreads":22,
          |    "myRights":{
          |      "mayReadItems":false,
          |      "mayAddItems":true,
          |      "mayRemoveItems":false,
          |      "maySetSeen":true,
          |      "maySetKeywords":false,
          |      "mayCreateChild":true,
          |      "mayRename":true,
          |      "mayDelete":false,
          |      "maySubmit":false
          |    },
          |    "isSubscribed":true,
          |    "namespace":"Personal",
          |    "rights":{
          |      "bob":["e","l"],
          |      "alice":["r","w"]
          |    },
          |    "quotas":{
          |      "quotaRoot":{
          |        "Message":{"used":18,"max":42},
          |        "Storage":{"used":12}
          |      },
          |      "quotaRoot2@localhost":{
          |        "Message":{"used":14,"max":43},
          |        "Storage":{"used":19}
          |      }
          |    }
          |  }],
          |  "notFound": ["1", "2"]
          |}
          |""".stripMargin)

      assertThatJson(SERIALIZER.serialize(actualValue)).isEqualTo(expectedJson)
    }
  }
}
