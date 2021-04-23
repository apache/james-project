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

import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core._
import org.apache.james.jmap.json.Fixture.id
import org.apache.james.jmap.json.MDNSerializationTest.{ACCOUNT_ID, FACTORY, SERIALIZER}
import org.apache.james.jmap.mail._
import org.apache.james.mailbox.model.{MessageId, TestMessageId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsResult, JsValue, Json}

object MDNSerializationTest {
  private val FACTORY: MessageId.Factory = new TestMessageId.Factory

  private val SERIALIZER: MDNSerializer = new MDNSerializer(FACTORY)

  private val ACCOUNT_ID: AccountId = AccountId(id)
}

class MDNSerializationTest extends AnyWordSpec with Matchers {

  "Deserialize MDNSendRequest" should {
    "Request should be success" in {
      val mdnSendRequestActual: JsResult[MDNSendRequest] = SERIALIZER.deserializeMDNSendRequest(
        Json.parse("""{
                     |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
                     |  "identityId": "I64588216",
                     |  "send": {
                     |    "k1546": {
                     |      "forEmailId": "1",
                     |      "subject": "Read receipt for: World domination",
                     |      "textBody": "This receipt",
                     |      "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
                     |      "finalRecipient": "rfc822; tungexplorer@linagora.com",
                     |      "includeOriginalMessage": true,
                     |      "disposition": {
                     |        "actionMode": "manual-action",
                     |        "sendingMode": "mdn-sent-manually",
                     |        "type": "displayed"
                     |      },
                     |      "extensionFields": {
                     |        "EXTENSION-EXAMPLE": "example.com"
                     |      }
                     |    }
                     |  },
                     |  "onSuccessUpdateEmail": {
                     |    "#k1546": {
                     |      "keywords/$$mdnsent": true
                     |    }
                     |  }
                     |}""".stripMargin))

      assert(mdnSendRequestActual.isSuccess)
    }

    "Request should be success with several MDN object" in {
      val mdnSendRequestActual: JsResult[MDNSendRequest] = SERIALIZER.deserializeMDNSendRequest(
        Json.parse("""{
                     |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
                     |    "identityId": "I64588216",
                     |    "send": {
                     |        "k1546": {
                     |            "forEmailId": "1",
                     |            "subject": "Read receipt for: World domination",
                     |            "textBody": "This receipt",
                     |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
                     |            "finalRecipient": "rfc822; tungexplorer@linagora.com",
                     |            "includeOriginalMessage": true,
                     |            "disposition": {
                     |                "actionMode": "manual-action",
                     |                "sendingMode": "mdn-sent-manually",
                     |                "type": "displayed"
                     |            },
                     |            "extensionFields": {
                     |                "EXTENSION-EXAMPLE": "example.com"
                     |            }
                     |        },
                     |        "k1547": {
                     |            "forEmailId": "1",
                     |            "subject": "Read receipt for: World domination",
                     |            "textBody": "This receipt",
                     |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
                     |            "finalRecipient": "rfc822; tungexplorer@linagora.com",
                     |            "includeOriginalMessage": true,
                     |            "disposition": {
                     |                "actionMode": "manual-action",
                     |                "sendingMode": "mdn-sent-manually",
                     |                "type": "displayed"
                     |            },
                     |            "extensionFields": {
                     |                "EXTENSION-EXAMPLE": "example.com"
                     |            }
                     |        },
                     |        "k1548": {
                     |            "forEmailId": "1",
                     |            "subject": "Read receipt for: World domination",
                     |            "textBody": "This receipt",
                     |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
                     |            "finalRecipient": "rfc822; tungexplorer@linagora.com",
                     |            "includeOriginalMessage": true,
                     |            "disposition": {
                     |                "actionMode": "manual-action",
                     |                "sendingMode": "mdn-sent-manually",
                     |                "type": "displayed"
                     |            },
                     |            "extensionFields": {
                     |                "EXTENSION-EXAMPLE": "example.com"
                     |            }
                     |        }
                     |
                     |    }
                     |}""".stripMargin))

      assert(mdnSendRequestActual.isSuccess)
    }

    "EntryRequest should be success" in {
      val entryRequestActual: JsResult[MDNSendCreateRequest] = SERIALIZER.deserializeMDNSendCreateRequest(
        Json.parse("""{
                     |    "forEmailId": "1",
                     |    "subject": "Read receipt for: World domination",
                     |    "textBody": "This receipt",
                     |    "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
                     |    "finalRecipient": "rfc822; tungexplorer@linagora.com",
                     |    "includeOriginalMessage": true,
                     |    "disposition": {
                     |        "actionMode": "manual-action",
                     |        "sendingMode": "mdn-sent-manually",
                     |        "type": "displayed"
                     |    },
                     |    "extensionFields": {
                     |        "EXTENSION-EXAMPLE": "example.com"
                     |    }
                     |}""".stripMargin))

      assert(entryRequestActual.isSuccess)
    }
  }

  "Serialize MDNSendResponse" should {
    "MDNSendResponse should be success" in {
      val mdn: MDNSendCreateResponse = MDNSendCreateResponse(
        subject = Some(SubjectField("Read receipt for: World domination")),
        textBody = Some(TextBodyField("This receipt")),
        reportingUA = Some(ReportUAField("joes-pc.cs.example.com; Foomail 97.1")),
        finalRecipient = Some(FinalRecipientField("rfc822; tungexplorer@linagora.com")),
        originalRecipient = Some(OriginalRecipientField("rfc822; tungexplorer@linagora.com")),
        mdnGateway = Some(MDNGatewayField("mdn gateway 1")),
        error = None,
        includeOriginalMessage = Some(IncludeOriginalMessageField(false)),
        originalMessageId = Some(OriginalMessageIdField("<199509192301.23456@example.org>")))

      val idSent: MDNSendCreationId = MDNSendCreationId(Id.validate("k1546").toOption.get)
      val idNotSent: MDNSendCreationId = MDNSendCreationId(Id.validate("k01").toOption.get)

      val response: MDNSendResponse = MDNSendResponse(
        accountId = ACCOUNT_ID,
        sent = Some(Map(idSent -> mdn)),
        notSent = Some(Map(idNotSent -> SetError(SetError.mdnAlreadySentValue,
          SetErrorDescription("mdnAlreadySent description"),
          None))))

      val actualValue: JsValue = SERIALIZER.serializeMDNSendResponse(response)

      val expectedValue: JsValue = Json.parse(
        """{
          |  "accountId" : "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "sent" : {
          |    "k1546" : {
          |      "subject" : "Read receipt for: World domination",
          |      "textBody" : "This receipt",
          |      "reportingUA" : "joes-pc.cs.example.com; Foomail 97.1",
          |      "mdnGateway" : "mdn gateway 1",
          |      "originalRecipient" : "rfc822; tungexplorer@linagora.com",
          |      "finalRecipient" : "rfc822; tungexplorer@linagora.com",
          |      "includeOriginalMessage" : false,
          |      "originalMessageId": "<199509192301.23456@example.org>"
          |    }
          |  },
          |  "notSent" : {
          |    "k01" : {
          |      "type" : "mdnAlreadySent",
          |      "description" : "mdnAlreadySent description"
          |    }
          |  }
          |}""".stripMargin)
      actualValue should equal(expectedValue)
    }
  }

  "Serialize MDNParseResponse" should {
    "MDNParseResponse should success" in {
      val mdnParse: MDNParsed = MDNParsed(
        forEmailId = Some(ForEmailIdField(FACTORY.fromString("1"))),
        subject = Some(SubjectField("Read: test")),
        textBody = Some(TextBodyField("To: magiclan@linagora.com\\r\\nSubject: test\\r\\nMessage was displayed on Tue Mar 30 2021 10:31:50 GMT+0700 (Indochina Time)")),
        reportingUA = Some(ReportUAField("OpenPaaS Unified Inbox; UA_Product")),
        finalRecipient = FinalRecipientField("rfc822; tungexplorer@linagora.com"),
        originalMessageId = Some(OriginalMessageIdField("<633c6811-f897-ec7c-642a-2360366e1b93@linagora.com>")),
        originalRecipient = Some(OriginalRecipientField("rfc822; tungexplorer@linagora.com")),
        includeOriginalMessage = IncludeOriginalMessageField(true),
        disposition = MDNDisposition(
          actionMode = "manual-action",
          sendingMode = "mdn-sent-manually",
          `type` = "displayed"),
        error = Some(Seq(ErrorField("Message1"), ErrorField("Message2"))),
        extensionFields = Some(Map("X-OPENPAAS-IP" -> " 177.177.177.77", "X-OPENPAAS-PORT" -> " 8000")))

      val mdnParseResponse: MDNParseResponse = MDNParseResponse(
        accountId = AccountId(Id.validate("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6").toOption.get),
        parsed = Some(Map(BlobId(Id.validate("1").toOption.get) -> mdnParse)),
        notFound = Some(MDNNotFound(Set(Id.validate("123").toOption.get))),
        notParsable = Some(MDNNotParsable(Set(Id.validate("456").toOption.get))))

      val actualValue: JsValue = SERIALIZER.serializeMDNParseResponse(mdnParseResponse)

      val expectedValue: JsValue = Json.parse(
        """{
          |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |    "parsed": {
          |        "1": {
          |            "forEmailId": "1",
          |            "subject": "Read: test",
          |            "textBody": "To: magiclan@linagora.com\\r\\nSubject: test\\r\\nMessage was displayed on Tue Mar 30 2021 10:31:50 GMT+0700 (Indochina Time)",
          |            "reportingUA": "OpenPaaS Unified Inbox; UA_Product",
          |            "disposition": {
          |                "actionMode": "manual-action",
          |                "sendingMode": "mdn-sent-manually",
          |                "type": "displayed"
          |            },
          |            "finalRecipient": "rfc822; tungexplorer@linagora.com",
          |            "originalMessageId": "<633c6811-f897-ec7c-642a-2360366e1b93@linagora.com>",
          |            "originalRecipient": "rfc822; tungexplorer@linagora.com",
          |            "includeOriginalMessage": true,
          |            "error": [
          |                "Message1",
          |                "Message2"
          |            ],
          |            "extensionFields": {
          |                "X-OPENPAAS-IP": " 177.177.177.77",
          |                "X-OPENPAAS-PORT": " 8000"
          |            }
          |        }
          |    },
          |    "notFound": [
          |        "123"
          |    ],
          |    "notParsable": [
          |        "456"
          |    ]
          |}""".stripMargin)
      actualValue should equal(expectedValue)
    }
  }
}
