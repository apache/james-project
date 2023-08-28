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

package org.apache.james.jmap.core

import java.net.URI
import java.util.Optional

import org.apache.commons.configuration2.Configuration
import org.apache.james.jmap.core.JmapRfc8621Configuration.{JMAP_UPLOAD_QUOTA_LIMIT_DEFAULT, MAX_SIZE_ATTACHMENTS_PER_MAIL_DEFAULT, UPLOAD_LIMIT_DEFAULT}
import org.apache.james.jmap.pushsubscription.PushClientConfiguration
import org.apache.james.util.Size

import scala.jdk.OptionConverters._

object JmapConfigProperties {
  val UPLOAD_LIMIT_PROPERTY: String = "upload.max.size"
  val MAX_SIZE_ATTACHMENTS_PER_MAIL_PROPERTY: String = "max.size.attachments.per.mail"
  val URL_PREFIX_PROPERTY: String = "url.prefix"
  val WEBSOCKET_URL_PREFIX_PROPERTY: String = "websocket.url.prefix"
  val WEB_PUSH_MAX_TIMEOUT_SECONDS_PROPERTY: String = "webpush.maxTimeoutSeconds"
  val WEB_PUSH_MAX_CONNECTIONS_PROPERTY: String = "webpush.maxConnections"
  val WEB_PUSH_PREVENT_SERVER_SIDE_REQUEST_FORGERY: String = "webpush.prevent.server.side.request.forgery"
  val DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY: String = "dynamic.jmap.prefix.resolution.enabled"
  val DELAY_SENDS_ENABLED: String = "delay.sends.enabled"
  val AUTHENTICATION_STRATEGIES: String = "authentication.strategy.rfc8621"
  val JMAP_UPLOAD_QUOTA_LIMIT_PROPERTY: String = "upload.quota.limit"
}

object JmapRfc8621Configuration {
  import JmapConfigProperties._
  val URL_PREFIX_DEFAULT: String = "http://localhost"
  val WEBSOCKET_URL_PREFIX_DEFAULT: String = "ws://localhost"
  val UPLOAD_LIMIT_DEFAULT: MaxSizeUpload = MaxSizeUpload.of(Size.of(30L, Size.Unit.M)).get
  val MAX_SIZE_ATTACHMENTS_PER_MAIL_DEFAULT: MaxSizeAttachmentsPerEmail = MaxSizeAttachmentsPerEmail.of(Size.of(20_000_000L, Size.Unit.B)).get
  val JMAP_UPLOAD_QUOTA_LIMIT_DEFAULT: JmapUploadQuotaLimit = JmapUploadQuotaLimit.of(Size.of(200L, Size.Unit.M)).get

  val LOCALHOST_CONFIGURATION: JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = URL_PREFIX_DEFAULT,
    websocketPrefixString = WEBSOCKET_URL_PREFIX_DEFAULT,
    maxUploadSize = UPLOAD_LIMIT_DEFAULT)

  def from(configuration: Configuration): JmapRfc8621Configuration =
    JmapRfc8621Configuration(
      urlPrefixString = Option(configuration.getString(URL_PREFIX_PROPERTY)).getOrElse(URL_PREFIX_DEFAULT),
      websocketPrefixString = Option(configuration.getString(WEBSOCKET_URL_PREFIX_PROPERTY)).getOrElse(WEBSOCKET_URL_PREFIX_DEFAULT),
      dynamicJmapPrefixResolutionEnabled = configuration.getBoolean(DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY, false),
      supportsDelaySends = configuration.getBoolean(DELAY_SENDS_ENABLED, false),
      maxUploadSize = Option(configuration.getString(UPLOAD_LIMIT_PROPERTY, null))
        .map(Size.parse)
        .map(MaxSizeUpload.of(_).get)
        .getOrElse(UPLOAD_LIMIT_DEFAULT),
      maxSizeAttachmentsPerEmail = Option(configuration.getString(MAX_SIZE_ATTACHMENTS_PER_MAIL_PROPERTY, null))
        .map(Size.parse)
        .map(MaxSizeAttachmentsPerEmail.of(_).get)
        .getOrElse(MAX_SIZE_ATTACHMENTS_PER_MAIL_DEFAULT),
      jmapUploadQuotaLimit = Option(configuration.getString(JMAP_UPLOAD_QUOTA_LIMIT_PROPERTY, null))
        .map(Size.parse)
        .map(JmapUploadQuotaLimit.of(_).get)
        .getOrElse(JMAP_UPLOAD_QUOTA_LIMIT_DEFAULT),
      maxTimeoutSeconds = Optional.ofNullable(configuration.getInteger(WEB_PUSH_MAX_TIMEOUT_SECONDS_PROPERTY, null)).map(Integer2int).toScala,
      maxConnections = Optional.ofNullable(configuration.getInteger(WEB_PUSH_MAX_CONNECTIONS_PROPERTY, null)).map(Integer2int).toScala,
      preventServerSideRequestForgery = Optional.ofNullable(configuration.getBoolean(WEB_PUSH_PREVENT_SERVER_SIDE_REQUEST_FORGERY, null)).orElse(true),
      authenticationStrategies = Optional.ofNullable(configuration.getList(classOf[String], AUTHENTICATION_STRATEGIES, null)).toScala)
}

case class JmapRfc8621Configuration(urlPrefixString: String,
                                    websocketPrefixString: String,
                                    dynamicJmapPrefixResolutionEnabled: Boolean = false,
                                    supportsDelaySends: Boolean = false,
                                    maxUploadSize: MaxSizeUpload = UPLOAD_LIMIT_DEFAULT,
                                    maxSizeAttachmentsPerEmail: MaxSizeAttachmentsPerEmail = MAX_SIZE_ATTACHMENTS_PER_MAIL_DEFAULT,
                                    jmapUploadQuotaLimit: JmapUploadQuotaLimit = JMAP_UPLOAD_QUOTA_LIMIT_DEFAULT,
                                    maxTimeoutSeconds: Option[Int] = None,
                                    maxConnections: Option[Int] = None,
                                    authenticationStrategies: Option[java.util.List[String]] = None,
                                    preventServerSideRequestForgery: Boolean = true) {

  val webPushConfiguration: PushClientConfiguration = PushClientConfiguration(
    maxTimeoutSeconds = maxTimeoutSeconds,
    maxConnections = maxConnections,
    preventServerSideRequestForgery = preventServerSideRequestForgery)

  def urlPrefixes(): UrlPrefixes = UrlPrefixes(new URI(urlPrefixString), new URI(websocketPrefixString))

  def getAuthenticationStrategiesAsJava(): Optional[java.util.List[String]] = authenticationStrategies.toJava

  def withAuthenticationStrategies(list: Optional[java.util.List[String]]): JmapRfc8621Configuration =
    this.copy(authenticationStrategies = list.toScala)
}
