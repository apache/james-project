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
import reactor.netty.ByteBufMono
import reactor.netty.http.client.{HttpClient, HttpClientResponse}
import reactor.netty.resources.ConnectionProvider

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

case class WebPushInvalidRequestException(detailError: String) extends WebPushException(s"Bad request when call to Push Server. $detailError")

case class WebPushTemporarilyUnavailableException(httpCode: Int, detailError: String) extends WebPushException(s"Error when call to Push Server: code $httpCode. $detailError")

object DefaultWebPushClient {
  val PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH: Int = 1024

  private def buildHttpClient(configuration: PushClientConfiguration, ssrfValidator: SSRFValidator): HttpClient = {
    val connectionProviderBuilder: ConnectionProvider.Builder = ConnectionProvider.builder(DefaultWebPushClient.getClass.getName)
    configuration.maxConnections.foreach(configValue => connectionProviderBuilder.maxConnections(configValue))

    val responseTimeout: Duration = configuration.maxTimeoutSeconds
      .map(configValue => Duration.of(configValue, ChronoUnit.SECONDS))
      .getOrElse(DEFAULT_TIMEOUT)

    val httpClient: HttpClient = HttpClient.create(connectionProviderBuilder.build())
      .disableRetry(true)
      // Redirects are not followed (which is the default) as their target would otherwise be reached
      // without the user supplied URL being the one we validated.
      .followRedirect(false)
      .responseTimeout(responseTimeout)
      .headers(builder => {
        builder.add("Content-Type", "application/json charset=utf-8")
      })

    if (configuration.preventServerSideRequestForgery) {
      // The push URL is resolved again when the connection is established: unless the very resolution
      // the connection relies on is validated, a DNS rebinding attack slips through.
      httpClient.resolver(ssrfValidator.addressResolverGroup)
    } else {
      httpClient
    }
  }
}

class DefaultWebPushClient(configuration: PushClientConfiguration, ssrfValidator: SSRFValidator) extends WebPushClient {

  @Inject
  def this(configuration: PushClientConfiguration) = this(configuration, new SSRFValidator())

  val httpClient: HttpClient = buildHttpClient(configuration, ssrfValidator)

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
      ssrfValidator.validate(pushServerUrl)
    } else {
      SMono.just(pushServerUrl)
    }

  private def afterHTTPResponseHandler(httpResponse: HttpClientResponse, dataBuf: ByteBufMono): Mono[Void] =
    Mono.just(httpResponse.status())
      .flatMap {
        case HttpResponseStatus.OK | HttpResponseStatus.CREATED | HttpResponseStatus.ACCEPTED => Mono.empty()
        case HttpResponseStatus.BAD_REQUEST => preProcessingData(dataBuf)
          .flatMap(string => Mono.error(WebPushInvalidRequestException(string)))
        case statusCode: HttpResponseStatus => preProcessingData(dataBuf)
          .flatMap(string => Mono.error(WebPushTemporarilyUnavailableException(statusCode.code, string)))
      }.`then`()

  private def preProcessingData(dataBuf: ByteBufMono): Mono[String] =
    dataBuf.asString(StandardCharsets.UTF_8)
      .switchIfEmpty(Mono.just(""))
      .map(content => content.take(PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH))
}
