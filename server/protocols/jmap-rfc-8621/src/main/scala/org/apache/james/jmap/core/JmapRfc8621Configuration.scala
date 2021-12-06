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

import java.util.Optional

import org.apache.commons.configuration2.Configuration
import org.apache.james.jmap.core.JmapRfc8621Configuration.UPLOAD_LIMIT_DEFAULT
import org.apache.james.jmap.pushsubscription.PushClientConfiguration
import org.apache.james.util.Size

import scala.jdk.OptionConverters._

object JmapConfigProperties {
  val UPLOAD_LIMIT_PROPERTY: String = "upload.max.size"
  val URL_PREFIX_PROPERTY: String = "url.prefix"
  val WEBSOCKET_URL_PREFIX_PROPERTY: String = "websocket.url.prefix"
  val WEB_PUSH_MAX_TIMEOUT_SECONDS_PROPERTY: String = "webpush.maxTimeoutSeconds"
  val WEB_PUSH_MAX_CONNECTIONS_PROPERTY: String = "webpush.maxConnections"
  val DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY: String = "dynamic.jmap.prefix.resolution.enabled"
}

object JmapRfc8621Configuration {
  import JmapConfigProperties._
  val URL_PREFIX_DEFAULT: String = "http://localhost"
  val WEBSOCKET_URL_PREFIX_DEFAULT: String = "ws://localhost"
  val UPLOAD_LIMIT_DEFAULT: MaxSizeUpload = MaxSizeUpload.of(Size.of(30L, Size.Unit.M)).get

  val LOCALHOST_CONFIGURATION: JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = URL_PREFIX_DEFAULT,
    websocketPrefixString = WEBSOCKET_URL_PREFIX_DEFAULT,
    maxUploadSize = UPLOAD_LIMIT_DEFAULT)

  def from(configuration: Configuration): JmapRfc8621Configuration =
    JmapRfc8621Configuration(
      urlPrefixString = Option(configuration.getString(URL_PREFIX_PROPERTY)).getOrElse(URL_PREFIX_DEFAULT),
      websocketPrefixString = Option(configuration.getString(WEBSOCKET_URL_PREFIX_PROPERTY)).getOrElse(WEBSOCKET_URL_PREFIX_DEFAULT),
      dynamicJmapPrefixResolutionEnabled = configuration.getBoolean(DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY, false),
      maxUploadSize = Option(configuration.getString(UPLOAD_LIMIT_PROPERTY, null))
        .map(Size.parse)
        .map(MaxSizeUpload.of(_).get)
        .getOrElse(UPLOAD_LIMIT_DEFAULT),
      maxTimeoutSeconds = Optional.ofNullable(configuration.getInteger(WEB_PUSH_MAX_TIMEOUT_SECONDS_PROPERTY, null)).map(Integer2int).toScala,
      maxConnections = Optional.ofNullable(configuration.getInteger(WEB_PUSH_MAX_CONNECTIONS_PROPERTY, null)).map(Integer2int).toScala)
}

case class JmapRfc8621Configuration(urlPrefixString: String,
                                    websocketPrefixString: String,
                                    dynamicJmapPrefixResolutionEnabled: Boolean = false,
                                    maxUploadSize: MaxSizeUpload = UPLOAD_LIMIT_DEFAULT,
                                    maxTimeoutSeconds: Option[Int] = None,
                                    maxConnections: Option[Int] = None) {

  val webPushConfiguration: PushClientConfiguration = PushClientConfiguration(
    maxTimeoutSeconds = maxTimeoutSeconds,
    maxConnections = maxConnections)
}
