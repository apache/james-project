/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.pushsubscription

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.temporal.ChronoUnit

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import jakarta.inject.Inject
import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.DefaultWebPushClient.{PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH, buildHttpClient}
import org.apache.james.jmap.pushsubscription.WebPushClientHeader.{CONTENT_ENCODING, DEFAULT_TIMEOUT, MESSAGE_URGENCY, TIME_TO_LIVE, TOPIC}
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.ByteBufMono
import reactor.netty.http.client.{HttpClient, HttpClientResponse}
import reactor.netty.resources.ConnectionProvider

import scala.util.{Failure, Success, Try}

trait WebPushClient {
  def push(pushServerUrl: PushSubscriptionServerURL, request: PushRequest): Publisher[Unit]
}

object PushClientConfiguration {
  val UNSAFE_DEFAULT: PushClientConfiguration = PushClientConfiguration(
    maxTimeoutSeconds = Some(10),
    maxConnections = Some(10),
    preventServerSideRequestForgery = false)
}

case class PushClientConfiguration(maxTimeoutSeconds: Option[Int],
                                   maxConnections: Option[Int],
                                   preventServerSideRequestForgery: Boolean = true)

object WebPushClientHeader {
  val TIME_TO_LIVE: String = "TTL"
  val CONTENT_ENCODING: String = "Content-Encoding"
  val MESSAGE_URGENCY: String = "Urgency"
  val TOPIC: String = "Topic"
  val DEFAULT_TIMEOUT: Duration = Duration.of(30, ChronoUnit.SECONDS)
}

sealed abstract class WebPushException(message: String) extends RuntimeException(message)

case class WebPushInvalidRequestException(detailError: String) extends WebPushException(s"Bad request when call to Push Server. ${detailError}")

case class WebPushTemporarilyUnavailableException(detailError: String) extends WebPushException(s"Error when call to Push Server. ${detailError}")

object DefaultWebPushClient {
  val PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH: Int = 1024

  private def buildHttpClient(configuration: PushClientConfiguration): HttpClient = {
    val connectionProviderBuilder: ConnectionProvider.Builder = ConnectionProvider.builder(DefaultWebPushClient.getClass.getName)
    configuration.maxConnections.foreach(configValue => connectionProviderBuilder.maxConnections(configValue))

    val responseTimeout: Duration = configuration.maxTimeoutSeconds
      .map(configValue => Duration.of(configValue, ChronoUnit.SECONDS))
      .getOrElse(DEFAULT_TIMEOUT)

    HttpClient.create(connectionProviderBuilder.build())
      .disableRetry(true)
      .responseTimeout(responseTimeout)
      .headers(builder => {
        builder.add("Content-Type", "application/json charset=utf-8")
      })
  }
}

class DefaultWebPushClient @Inject()(configuration: PushClientConfiguration) extends WebPushClient {

  val httpClient: HttpClient = buildHttpClient(configuration)

  override def push(pushServerUrl: PushSubscriptionServerURL, request: PushRequest): Publisher[Unit] =
    validate(pushServerUrl)
      .flatMap(url => SMono(httpClient
        .headers(builder => {
          builder.add(TIME_TO_LIVE, request.ttl.value)
          builder.add(MESSAGE_URGENCY, request.urgency.getOrElse(PushUrgency.default).value)
          request.topic.foreach(t => builder.add(TOPIC, t.value))
          request.contentCoding.foreach(f => builder.add(CONTENT_ENCODING, f.value))
        })
        .post()
        .uri(url.value.toString)
        .send(SMono.just(Unpooled.wrappedBuffer(request.payload)))
        .responseSingle((httpResponse, dataBuf) => afterHTTPResponseHandler(httpResponse, dataBuf))
        .thenReturn(SMono.empty)))

  private def validate(pushServerUrl: PushSubscriptionServerURL): SMono[PushSubscriptionServerURL] =
    if (configuration.preventServerSideRequestForgery) {
      SMono.just(pushServerUrl.value.getHost)
        .flatMap(host => SMono.fromCallable(() => InetAddress.getByName(host)).subscribeOn(Schedulers.boundedElastic()))
        .handle[InetAddress]((inetAddress, sink) => validate(pushServerUrl, inetAddress).fold(sink.error, sink.next))
        .`then`(SMono.just(pushServerUrl))
    } else {
      SMono.just(pushServerUrl)
    }

  private def validate(pushServerUrl: PushSubscriptionServerURL, inetAddress: InetAddress): Try[InetAddress] = inetAddress match {
    case address if address.isSiteLocalAddress => Failure(new IllegalArgumentException(s"JMAP Push subscription $pushServerUrl is targeting a site local address $inetAddress. This could be an attempt for server-side request forgery."))
    case address if address.isLoopbackAddress => Failure(new IllegalArgumentException(s"JMAP Push subscription $pushServerUrl is targeting a loopback address $inetAddress. This could be an attempt for server-side request forgery."))
    case address if address.isLinkLocalAddress => Failure(new IllegalArgumentException(s"JMAP Push subscription $pushServerUrl is targeting a link local address $inetAddress. This could be an attempt for server-side request forgery."))
    case _ => Success(inetAddress)
  }

  private def afterHTTPResponseHandler(httpResponse: HttpClientResponse, dataBuf: ByteBufMono): Mono[Void] =
    Mono.just(httpResponse.status())
      .flatMap {
        case HttpResponseStatus.CREATED => Mono.empty()
        case HttpResponseStatus.BAD_REQUEST => preProcessingData(dataBuf)
          .flatMap(string => Mono.error(WebPushInvalidRequestException(string)))
        case _ => preProcessingData(dataBuf)
          .flatMap(string => Mono.error(WebPushTemporarilyUnavailableException(string)))
      }.`then`()

  private def preProcessingData(dataBuf: ByteBufMono): Mono[String] =
    dataBuf.asString(StandardCharsets.UTF_8)
      .switchIfEmpty(Mono.just(""))
      .map(content => if (content.length > PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH) {
        content.substring(PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH)
      } else {
        content
      })
}
