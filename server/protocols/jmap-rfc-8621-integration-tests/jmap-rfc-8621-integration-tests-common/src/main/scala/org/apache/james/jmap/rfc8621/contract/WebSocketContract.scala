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
package org.apache.james.jmap.rfc8621.contract

import java.net.URI
import java.util

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

trait WebSocketContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
  }

  class ExampleClient(uri: URI) extends WebSocketClient(uri) {
    val receivedResponses: util.LinkedList[String] = new util.LinkedList[String]()
    var closeCode: Option[Integer] = None
    var closeString: Option[String] = None

    override def onOpen(serverHandshake: ServerHandshake): Unit = {
      println(s"handshake ${serverHandshake.getHttpStatus}")
    }

    override def onMessage(s: String): Unit = {
      println(s"Received: $s")
      receivedResponses.add(s)
    }

    override def onClose(i: Int, s: String, b: Boolean): Unit = {
      closeCode = Some(i)
      closeString = Some(s)
      println(s"Closing connection $i $s $b")
    }

    override def onError(e: Exception): Unit = {
      println("Error: " + e.getMessage)
    }
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def apiRequestsShouldBeProcessed(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send("""{
                  |  "@type": "Request",
                  |  "requestId": "req-36",
                  |  "using": [ "urn:ietf:params:jmap:core"],
                  |  "methodCalls": [
                  |    [
                  |      "Core/echo",
                  |      {
                  |        "arg1": "arg1data",
                  |        "arg2": "arg2data"
                  |      },
                  |      "c1"
                  |    ]
                  |  ]
                  |}""".stripMargin)

    Thread.sleep(500)


    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "@type":"Response",
        |  "requestId":"req-36",
        |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
        |  "methodResponses":[["Core/echo",{"arg1":"arg1data","arg2":"arg2data"},"c1"]]
        |}
        |""".stripMargin)
  }

  @Test
  def apiRequestsShouldBeProcessedWhenNoRequestId(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send("""{
                  |  "@type": "Request",
                  |  "using": [ "urn:ietf:params:jmap:core"],
                  |  "methodCalls": [
                  |    [
                  |      "Core/echo",
                  |      {
                  |        "arg1": "arg1data",
                  |        "arg2": "arg2data"
                  |      },
                  |      "c1"
                  |    ]
                  |  ]
                  |}""".stripMargin)

    Thread.sleep(500)


    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "@type":"Response",
        |  "requestId":null,
        |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
        |  "methodResponses":[["Core/echo",{"arg1":"arg1data","arg2":"arg2data"},"c1"]]
        |}
        |""".stripMargin)
  }

  @Test
  def nonJsonPayloadShouldTriggerError(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send("The quick brown fox".stripMargin)

    Thread.sleep(500)

    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "status":400,
        |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Unrecognized token 'The': was expecting ('true', 'false' or 'null')\n at [Source: (String)\"The quick brown fox\"; line: 1, column: 4]),ArraySeq()))))",
        |  "type":"urn:ietf:params:jmap:error:notRequest",
        |  "requestId":null,
        |  "@type":"RequestError"
        |}
        |""".stripMargin)
  }

  @Test
  def handshakeShouldBeAuthenticated(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(100)

    assertThat(client.isClosed).isTrue
    assertThat(client.closeString).isEqualTo(Some("Invalid status code received: 401 Status line: HTTP/1.1 401 Unauthorized"))
  }

  @Test
  def noTypeFiledShouldTriggerError(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send("""{
                  |  "requestId": "req-36",
                  |  "using": [ "urn:ietf:params:jmap:core"],
                  |  "methodCalls": [
                  |    [
                  |      "Core/echo",
                  |      {
                  |        "arg1": "arg1data",
                  |        "arg2": "arg2data"
                  |      },
                  |      "c1"
                  |    ]
                  |  ]
                  |}""".stripMargin)

    Thread.sleep(500)


    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "status":400,
        |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Missing @type filed on a webSocket inbound message),ArraySeq()))))",
        |  "type":"urn:ietf:params:jmap:error:notRequest",
        |  "requestId":null,
        |  "@type":"RequestError"
        |}
        |""".stripMargin
    )
  }

  @Test
  def badTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send("""{
                  |  "@type": 42,
                  |  "requestId": "req-36",
                  |  "using": [ "urn:ietf:params:jmap:core"],
                  |  "methodCalls": [
                  |    [
                  |      "Core/echo",
                  |      {
                  |        "arg1": "arg1data",
                  |        "arg2": "arg2data"
                  |      },
                  |      "c1"
                  |    ]
                  |  ]
                  |}""".stripMargin)

    Thread.sleep(500)

    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "status":400,
        |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Invalid @type filed on a webSocket inbound message: expecting a JsString, got 42),ArraySeq()))))",
        |  "type":"urn:ietf:params:jmap:error:notRequest",
        |  "requestId":null,
        |  "@type":"RequestError"
        |}
        |""".stripMargin
    )

  }

  @Test
  def unknownTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send(
      """{
        |  "@type": "unknown",
        |  "requestId": "req-36",
        |  "using": [ "urn:ietf:params:jmap:core"],
        |  "methodCalls": [
        |    [
        |      "Core/echo",
        |      {
        |        "arg1": "arg1data",
        |        "arg2": "arg2data"
        |      },
        |      "c1"
        |    ]
        |  ]
        |}""".stripMargin)

    Thread.sleep(500)

    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "status":400,
        |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Unknown @type filed on a webSocket inbound message: unknown),ArraySeq()))))",
        |  "type":"urn:ietf:params:jmap:error:notRequest",
        |  "requestId":null,
        |  "@type":"RequestError"
        |}
        |""".stripMargin
    )
  }


  @Test
  def clientSendingARespondTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send(
      """{
        |  "@type": "Response",
        |  "requestId": "req-36",
        |  "using": [ "urn:ietf:params:jmap:core"],
        |  "methodCalls": [
        |    [
        |      "Core/echo",
        |      {
        |        "arg1": "arg1data",
        |        "arg2": "arg2data"
        |      },
        |      "c1"
        |    ]
        |  ]
        |}""".stripMargin)

    Thread.sleep(500)

    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "status":400,
        |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Unknown @type filed on a webSocket inbound message: Response),ArraySeq()))))",
        |  "type":"urn:ietf:params:jmap:error:notRequest",
        |  "requestId":null,
        |  "@type":"RequestError"
        |}
        |""".stripMargin
    )

  }

  @Test
  def requestLevelErrorShouldReturnAPIError(server: GuiceJamesServer): Unit = {
    println("started")
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    val client = new ExampleClient(new URI(s"ws://127.0.0.1:$port/jmap/ws"))
    client.addHeader("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
    client.addHeader("Accept", ACCEPT_RFC8621_VERSION_HEADER)

    client.connectBlocking()

    Thread.sleep(500)

    client.send(s"""{
                   |  "@type": "Request",
                   |  "using": [
                   |    "urn:ietf:params:jmap:core",
                   |    "urn:ietf:params:jmap:mail"],
                   |  "methodCalls": [[
                   |      "Mailbox/get",
                   |      {
                   |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                   |        "properties": ["invalidProperty"]
                   |      },
                   |      "c1"]]
                   |}""".stripMargin)

    Thread.sleep(500)


    assertThat(client.receivedResponses).hasSize(1)
    assertThatJson(client.receivedResponses.get(0)).isEqualTo(
      """
        |{
        |  "@type": "Response",
        |  "requestId": null,
        |  "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
        |  "methodResponses": [["error",{"type":"invalidArguments","description":"The following properties [invalidProperty] do not exist."},"c1"]]
        |}
        |""".stripMargin)
  }
}
