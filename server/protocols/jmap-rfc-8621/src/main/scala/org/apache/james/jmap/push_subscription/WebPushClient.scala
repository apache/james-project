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

package org.apache.james.jmap.push_subscription

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.push_subscription.DefaultWebPushClient.{PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH, buildHttpClient}
import org.apache.james.jmap.push_subscription.WebPushClientHeader.{DEFAULT_TIMEOUT, MESSAGE_URGENCY, TIME_TO_LIVE, TOPIC}
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.ByteBufMono
import reactor.netty.http.client.{HttpClient, HttpClientResponse}
import reactor.netty.resources.ConnectionProvider

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.temporal.ChronoUnit

trait WebPushClient {
  def push(pushServerUrl: PushSubscriptionServerURL, request: PushRequest): Publisher[Unit]
}

case class PushClientConfiguration(maxTimeoutSeconds: Option[Int],
                                   maxConnections: Option[Int],
                                   maxRetryTimes: Option[Int],
                                   requestPerSeconds: Option[Int],
                                   scheduler: reactor.core.scheduler.Scheduler)

object WebPushClientHeader {
  val TIME_TO_LIVE: String = "TTL"
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
    configuration.maxTimeoutSeconds.foreach(configValue => connectionProviderBuilder.pendingAcquireMaxCount(configValue))

    val responseTimeout: Duration = configuration.maxTimeoutSeconds
      .map(configValue => Duration.of(configValue, ChronoUnit.SECONDS))
      .getOrElse(DEFAULT_TIMEOUT)

    HttpClient.create(connectionProviderBuilder.build())
      .responseTimeout(responseTimeout)
      .headers(builder => {
        builder.add("Content-Type", "application/json charset=utf-8")
      })
  }
}

class DefaultWebPushClient(configuration: PushClientConfiguration) extends WebPushClient {

  val httpClient: HttpClient = buildHttpClient(configuration)

  override def push(pushServerUrl: PushSubscriptionServerURL, request: PushRequest): Publisher[Unit] =
    httpClient
      .headers(builder => {
        builder.add(TIME_TO_LIVE, request.ttl.value)
        builder.add(MESSAGE_URGENCY, request.urgency.getOrElse(PushUrgency.default).value)
        request.topic.foreach(t => builder.add(TOPIC, t.value))
      })
      .post()
      .uri(pushServerUrl.value.toString)
      .send(SMono.just(Unpooled.wrappedBuffer(request.payload)))
      .responseSingle((httpResponse, dataBuf) => afterHTTPResponseHandler(httpResponse, dataBuf))
      .thenReturn(SMono.empty)

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
