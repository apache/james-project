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

package org.apache.james.jmap.routes

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

import com.google.common.collect.{ImmutableSet, Sets}
import org.apache.james.core.{Domain, Username}
import org.apache.james.dnsservice.api.DNSService
import org.apache.james.domainlist.lib.DomainListConfiguration
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.events.EventBus
import org.apache.james.jmap.api.change.{EmailChangeRepository, MailboxChangeRepository, TypeStateFactory}
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.http.{Authenticator, BasicAuthenticationStrategy, UserProvisioning}
import org.apache.james.jmap.json.PushSerializer
import org.apache.james.jmap.{JMAPConfiguration, JMAPRoutesHandler, JMAPServer, Version, VersionParser}
import org.apache.james.mailbox.extension.PreDeletionHook
import org.apache.james.mailbox.inmemory.MemoryMailboxManagerProvider
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.apache.james.user.api.DelegationStore
import org.apache.james.user.memory.MemoryUsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import org.mockito.Mockito.mock
import reactor.core.publisher.Flux
import reactor.netty.http.client.{HttpClient, WebsocketClientSpec}

class WebSocketRoutesDisconnectTest {
  private val user: Username = Username.of("user1")
  private var webSocketRoutes: WebSocketRoutes = _
  private var server: JMAPServer = _

  @BeforeEach
  def setUp(): Unit = {
    val domainList = new MemoryDomainList(mock(classOf[DNSService]))
    domainList.configure(DomainListConfiguration.DEFAULT)
    domainList.addDomain(Domain.of("james.org"))
    val usersRepository = MemoryUsersRepository.withoutVirtualHosting(domainList)
    usersRepository.addUser(user, "password")
    val mailboxManager = MemoryMailboxManagerProvider.provideMailboxManager(ImmutableSet.of[PreDeletionHook]())
    val authenticator = Authenticator.of(new RecordingMetricFactory, new BasicAuthenticationStrategy(usersRepository, mailboxManager))
    val configuration = JmapRfc8621Configuration("http://127.0.0.1", "ws://127.0.0.1")
    val typeStateFactory = TypeStateFactory(Sets.newHashSet[TypeName]())

    webSocketRoutes = new WebSocketRoutes(authenticator, configuration, new UserProvisioning(usersRepository, new RecordingMetricFactory),
      mock(classOf[EventBus]), mock(classOf[JMAPApi]), mock(classOf[MailboxChangeRepository]), mock(classOf[EmailChangeRepository]),
      PushSerializer(typeStateFactory), typeStateFactory, mock(classOf[DelegationStore]), new RecordingMetricFactory)
    server = new JMAPServer(JMAPConfiguration.builder().enable().randomPort().build(),
      ImmutableSet.of(new JMAPRoutesHandler(Version.RFC8621, webSocketRoutes)),
      new VersionParser(ImmutableSet.of(Version.RFC8621), JMAPConfiguration.DEFAULT))
    server.start()
  }

  @AfterEach
  def tearDown(): Unit = server.stop()

  @Test
  def disconnectShouldCloseTheWebSocket(): Unit = {
    val received = new ConcurrentLinkedQueue[String]()
    val credentials = Base64.getEncoder.encodeToString("user1:password".getBytes(StandardCharsets.UTF_8))
    val closed = HttpClient.create()
      .headers(headers => headers.add("Authorization", s"Basic $credentials"))
      .websocket(WebsocketClientSpec.builder().protocols("jmap").build())
      .uri(s"ws://127.0.0.1:${server.getPort.getValue}/jmap/ws")
      .handle((in, out) => {
        out.sendString(Flux.just("not a JMAP message").concatWith(Flux.never())).`then`().subscribe()
        in.receive().asString().doOnNext(message => received.add(message))
      })
      .`then`()
      .toFuture

    // The error response tells the connection is registered
    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() => !received.isEmpty)

    webSocketRoutes.disconnect(_ => true)

    closed.get(10, TimeUnit.SECONDS)
    assertThat(closed).isDone
  }

  private def connect(messages: Flux[String]): java.util.concurrent.CompletableFuture[Void] = {
    val credentials = Base64.getEncoder.encodeToString("user1:password".getBytes(StandardCharsets.UTF_8))
    HttpClient.create()
      .headers(headers => headers.add("Authorization", s"Basic $credentials"))
      .websocket(WebsocketClientSpec.builder().protocols("jmap").build())
      .uri(s"ws://127.0.0.1:${server.getPort.getValue}/jmap/ws")
      .handle((in, out) => {
        out.sendString(messages.concatWith(Flux.never())).`then`().subscribe()
        in.receive().asString()
      })
      .`then`()
      .toFuture
  }

  @Test
  def disconnectShouldCloseAWebSocketThatSentNothing(): Unit = {
    val closed = connect(Flux.empty())
    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() => webSocketRoutes.describeConnections().count() == 1)

    webSocketRoutes.disconnect(_ => true)

    closed.get(10, TimeUnit.SECONDS)
    assertThat(closed).isDone
  }

  @Test
  def disconnectShouldCloseAWebSocketThatDisabledPush(): Unit = {
    val closed = connect(Flux.just("""{"@type":"WebSocketPushDisable"}"""))
    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() => webSocketRoutes.describeConnections().count() == 1)
    // Lets the push disabling be handled
    Thread.sleep(500)

    webSocketRoutes.disconnect(_ => true)

    closed.get(10, TimeUnit.SECONDS)
    assertThat(closed).isDone
  }
}
