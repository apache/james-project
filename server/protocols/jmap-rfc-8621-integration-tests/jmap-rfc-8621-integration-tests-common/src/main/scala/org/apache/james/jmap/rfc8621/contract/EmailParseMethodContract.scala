/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DownloadContract.accountId
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

trait EmailParseMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build()
  }

  def randomMessageId: MessageId

  @Test
  def parseShouldSuccess(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.serialize}",
           |    "methodResponses": [
           |        [
           |            "Email/parse",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "parsed": {
           |                    "${messageId.serialize()}": {
           |                        "inReplyTo": null,
           |                        "to": [
           |                            {
           |                                "email": "to@linagora.com"
           |                            }
           |                        ],
           |                        "references": null,
           |                        "textBody": [
           |                            {
           |                                "charset": "utf-8",
           |                                "size": 39,
           |                                "partId": "2",
           |                                "blobId": "${messageId.serialize()}_2",
           |                                "type": "text/html"
           |                            }
           |                        ],
           |                        "sentAt": "2017-02-27T04:24:48Z",
           |                        "hasAttachment": true,
           |                        "attachments": [
           |                            {
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment",
           |                                "size": 271,
           |                                "partId": "3",
           |                                "blobId": "${messageId.serialize()}_3",
           |                                "name": "text1",
           |                                "type": "text/plain"
           |                            },
           |                            {
           |                                "charset": "us-ascii",
           |                                "disposition": "attachment",
           |                                "size": 398,
           |                                "partId": "4",
           |                                "blobId": "${messageId.serialize()}_4",
           |                                "name": "text2",
           |                                "type": "application/vnd.ms-publisher"
           |                            },
           |                            {
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment",
           |                                "size": 412,
           |                                "partId": "5",
           |                                "blobId": "${messageId.serialize()}_5",
           |                                "name": "text3",
           |                                "type": "text/plain"
           |                            }
           |                        ],
           |                        "subject": "MultiAttachment",
           |                        "size": 2725,
           |                        "blobId": "${messageId.serialize()}",
           |                        "preview": "Send concerted from html",
           |                        "htmlBody": [
           |                            {
           |                                "charset": "utf-8",
           |                                "size": 39,
           |                                "partId": "2",
           |                                "blobId": "${messageId.serialize()}_2",
           |                                "type": "text/html"
           |                            }
           |                        ],
           |                        "bodyValues": {
           |
           |                        },
           |                        "messageId": [
           |                            "13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com"
           |                        ],
           |                        "from": [
           |                            {
           |                                "name": "Lina",
           |                                "email": "from@linagora.com"
           |                            }
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def attachmentsOfNestedMessagesShouldBeDownloadable(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/nested.eml")))
      .getMessageId

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3_3")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
      .extract
      .body
      .asString

    assertThat(response)
      .isEqualTo("test attachment\n")
  }

  @Test
  def parseShouldSuccessWhenAttachment(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/nested.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}_3" ],
         |      "properties":["bodyValues", "preview", "hasAttachment", "attachments", "blobId", "size", "headers", "references", "subject", "inReplyTo", "messageId", "from", "to", "sentAt"],
         |      "fetchTextBodyValues": true,
         |      "fetchHTMLBodyValues": true,
         |      "bodyProperties":["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.serialize}",
           |    "methodResponses": [
           |        [
           |            "Email/parse",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "parsed": {
           |                    "${messageId.serialize()}_3": {
           |                        "inReplyTo": null,
           |                        "to": [
           |                            {
           |                                "name": "Me ME",
           |                                "email": "me@linagora.com"
           |                            }
           |                        ],
           |                        "references": null,
           |                        "bodyValues": {
           |                            "2": {
           |                                "value": "test body\\n",
           |                                "isEncodingProblem": false,
           |                                "isTruncated": false
           |                            }
           |                        },
           |                        "sentAt": "2023-05-03T00:36:49Z",
           |                        "headers": [
           |                            {
           |                                "name": "Return-Path",
           |                                "value": " <me@linagora.com>"
           |                            },
           |                            {
           |                                "name": "Delivered-To",
           |                                "value": " me@linagora.com"
           |                            },
           |                            {
           |                                "name": "Content-Type",
           |                                "value": " multipart/mixed; boundary=\\"------------F6Ykx4Sf21a3Ovh9U5C4q1Qb\\""
           |                            },
           |                            {
           |                                "name": "Message-ID",
           |                                "value": " <ed24d4ad-53c0-48c7-2fc9-39f762e4d98d@linagora.com>"
           |                            },
           |                            {
           |                                "name": "Date",
           |                                "value": " Wed, 3 May 2023 07:36:49 +0700"
           |                            },
           |                            {
           |                                "name": "MIME-Version",
           |                                "value": " 1.0"
           |                            },
           |                            {
           |                                "name": "To",
           |                                "value": " Me ME <me@linagora.com>"
           |                            },
           |                            {
           |                                "name": "From",
           |                                "value": " Me ME <me@linagora.com>"
           |                            },
           |                            {
           |                                "name": "Subject",
           |                                "value": " test subject"
           |                            }
           |                        ],
           |                        "hasAttachment": true,
           |                        "attachments": [
           |                            {
           |                                "partId": "3",
           |                                "blobId": "${messageId.serialize()}_3_3",
           |                                "size": 16,
           |                                "name": "whatever.txt",
           |                                "type": "text/plain",
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment"
           |                            }
           |                        ],
           |                        "subject": "test subject",
           |                        "size": 797,
           |                        "blobId": "${messageId.serialize()}_3",
           |                        "preview": "test body",
           |                        "messageId": [
           |                            "ed24d4ad-53c0-48c7-2fc9-39f762e4d98d@linagora.com"
           |                        ],
           |                        "from": [
           |                            {
           |                                "name": "Me ME",
           |                                "email": "me@linagora.com"
           |                            }
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def parseShouldSupportAllProperties(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ],
         |      "properties": ["blobId", "size", "headers", "references", "subject", "inReplyTo", "messageId", "from", "to", "sentAt"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val contentType = " multipart/mixed;\\r\\n boundary=\\\"------------64D8D789FC30153D6ED18258\\\""
    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "size": 2725,
           |             "blobId": "${messageId.serialize()}",
           |             "headers": [
           |               {
           |                 "name": "Return-Path",
           |                 "value": " <from@linagora.com>"
           |               },
           |               {
           |                 "name": "To",
           |                 "value": " to@linagora.com"
           |               },
           |               {
           |                 "name": "From",
           |                 "value": " Lina <from@linagora.com>"
           |               },
           |               {
           |                 "name": "Subject",
           |                 "value": " MultiAttachment"
           |               },
           |               {
           |                 "name": "Message-ID",
           |                 "value": " <13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com>"
           |               },
           |               {
           |                 "name": "Date",
           |                 "value": " Mon, 27 Feb 2017 11:24:48 +0700"
           |               },
           |               {
           |                 "name": "User-Agent",
           |                 "value": " Mozilla/5.0 (X11; Linux x86_64; rv:45.0) Gecko/20100101\\r\\n Thunderbird/45.2.0"
           |               },
           |               {
           |                 "name": "MIME-Version",
           |                 "value": " 1.0"
           |               },
           |               {
           |                 "name": "Content-Type",
           |                 "value": "$contentType"
           |               }
           |             ],
           |             "references": null,
           |             "subject": "MultiAttachment",
           |             "inReplyTo": null,
           |             "messageId": [ "13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com"],
           |             "from": [
           |               {
           |                 "name": "Lina",
           |                  "email": "from@linagora.com"
           |               }
           |             ],
           |             "sentAt": "2017-02-27T04:24:48Z",
           |             "to": [
           |               {
           |                  "email": "to@linagora.com"
           |               }
           |             ]
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldFilterProperties(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ],
         |      "properties": ["blobId"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "blobId": "${messageId.serialize()}"
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldAllowRetrievingBody(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ],
         |      "properties":["bodyValues", "preview", "hasAttachment", "attachments"],
         |      "fetchTextBodyValues": true,
         |      "fetchHTMLBodyValues": true,
         |      "bodyProperties":["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location", "subParts", "headers"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed":{
           |         "${messageId.serialize()}": {
           |           "preview": "Send concerted from html",
           |           "bodyValues": {
           |             "2": {
           |               "value": "Send\\nconcerted from html\\n\\n\\r\\n\\r\\n",
           |               "isEncodingProblem": false,
           |               "isTruncated": false
           |             }
           |           },
           |           "hasAttachment": true,
           |                        "attachments": [
           |                            {
           |                                "partId": "3",
           |                                "blobId": "${messageId.serialize()}_3",
           |                                "headers": [
           |                                    {
           |                                        "name": "Content-Type",
           |                                        "value": " text/plain; charset=UTF-8;\\r\\n name=\\"text1\\""
           |                                    },
           |                                    {
           |                                        "name": "Content-Transfer-Encoding",
           |                                        "value": " base64"
           |                                    },
           |                                    {
           |                                        "name": "Content-Disposition",
           |                                        "value": " attachment;\\r\\n filename=\\"text1\\""
           |                                    }
           |                                ],
           |                                "size": 271,
           |                                "name": "text1",
           |                                "type": "text/plain",
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment"
           |                            },
           |                            {
           |                                "partId": "4",
           |                                "blobId": "${messageId.serialize()}_4",
           |                                "headers": [
           |                                    {
           |                                        "name": "Content-Type",
           |                                        "value": " application/vnd.ms-publisher;\\r\\n name=\\"text2\\""
           |                                    },
           |                                    {
           |                                        "name": "Content-Transfer-Encoding",
           |                                        "value": " base64"
           |                                    },
           |                                    {
           |                                        "name": "Content-Disposition",
           |                                        "value": " attachment;\\r\\n filename=\\"text2\\""
           |                                    }
           |                                ],
           |                                "size": 398,
           |                                "name": "text2",
           |                                "type": "application/vnd.ms-publisher",
           |                                "charset": "us-ascii",
           |                                "disposition": "attachment"
           |                            },
           |                            {
           |                                "partId": "5",
           |                                "blobId": "${messageId.serialize()}_5",
           |                                "headers": [
           |                                    {
           |                                        "name": "Content-Type",
           |                                        "value": " text/plain; charset=UTF-8;\\r\\n name=\\"text3\\""
           |                                    },
           |                                    {
           |                                        "name": "Content-Transfer-Encoding",
           |                                        "value": " base64"
           |                                    },
           |                                    {
           |                                        "name": "Content-Disposition",
           |                                        "value": " attachment;\\r\\n filename=\\"text3\\""
           |                                    }
           |                                ],
           |                                "size": 412,
           |                                "name": "text3",
           |                                "type": "text/plain",
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment"
           |                            }
           |                        ]
           |         }}
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldApplyBodyFiltering(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ],
         |      "properties":["bodyValues", "preview", "hasAttachment", "attachments"],
         |      "fetchTextBodyValues": true,
         |      "fetchHTMLBodyValues": true,
         |      "bodyProperties":["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed":{
           |         "${messageId.serialize()}": {
           |           "preview": "Send concerted from html",
           |           "bodyValues": {
           |             "2": {
           |               "value": "Send\\nconcerted from html\\n\\n\\r\\n\\r\\n",
           |               "isEncodingProblem": false,
           |               "isTruncated": false
           |             }
           |           },
           |           "hasAttachment": true,
           |                        "attachments": [
           |                            {
           |                                "partId": "3",
           |                                "blobId": "${messageId.serialize()}_3",
           |                                "size": 271,
           |                                "name": "text1",
           |                                "type": "text/plain",
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment"
           |                            },
           |                            {
           |                                "partId": "4",
           |                                "blobId": "${messageId.serialize()}_4",
           |                                "size": 398,
           |                                "name": "text2",
           |                                "type": "application/vnd.ms-publisher",
           |                                "charset": "us-ascii",
           |                                "disposition": "attachment"
           |                            },
           |                            {
           |                                "partId": "5",
           |                                "blobId": "${messageId.serialize()}_5",
           |                                "size": 412,
           |                                "name": "text3",
           |                                "type": "text/plain",
           |                                "charset": "UTF-8",
           |                                "disposition": "attachment"
           |                            }
           |                        ]
           |         }}
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "unknownAccountId",
         |      "blobIds": [ "0f9f65ab-dc7b-4146-850f-6e4881093965" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBlobDoNotExist(): Unit = {
    val blobIdShouldNotFound = randomMessageId.serialize()
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "$blobIdShouldNotFound" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "Email/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "parsed":{},
         |        "notFound": ["$blobIdShouldNotFound"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBadBlobId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "invalid" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "Email/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "parsed":{},
         |        "notFound": ["invalid"]
         |      },
         |      "c1"
         |    ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mail"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mail, urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
