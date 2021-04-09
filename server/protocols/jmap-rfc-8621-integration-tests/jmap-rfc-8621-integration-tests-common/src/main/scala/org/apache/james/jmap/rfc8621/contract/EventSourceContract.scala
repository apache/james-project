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

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.client.HttpClient

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

trait EventSourceContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(BOB.asString(), BOB_PASSWORD)
  }

  @Test
  def typesQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response().block().status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def pingQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def closeafterQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectInvalidCloseafter(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeafter=bad")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectInvalidPing(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=bad&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectInvalidTypes(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=bad&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectUnauthenticated(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Email&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.UNAUTHORIZED.code())
  }

  @Test
  def noSSEEventShouldBeSentByDefault(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(500)

    assertThat(seq.asJava).isEmpty()
  }

  @Test
  def sseEventsShouldBeFilteredByTypes(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Email&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    Thread.sleep(200)

    assertThat(seq.asJava).isEmpty()
  }

  @Test
  def allTypesShouldBeSupported(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def pingShouldBeSupported(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=1&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(2000)

    assertThat(seq.size).isGreaterThanOrEqualTo(1)
    assertThat(seq.head).isEqualTo("event: ping\ndata: {\"interval\":1}\n\n")
  }

  @Test
  def sseShouldTransportEvent(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def sseShouldCloseafterEventWhenCloseafterState(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeafter=state")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def sseShouldTransportEvents(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeafter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.elastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(2)
  }
}
