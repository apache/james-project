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

package org.apache.james.jmap.model

import java.net.URL

import org.apache.commons.configuration2.Configuration

object JmapRfc8621Configuration {
  val LOCALHOST_URL_PREFIX: String = "http://localhost"
  private def from(urlPrefixString: String): JmapRfc8621Configuration = JmapRfc8621Configuration(urlPrefixString)
  var LOCALHOST_CONFIGURATION: JmapRfc8621Configuration = from(LOCALHOST_URL_PREFIX)
  val URL_PREFIX_PROPERTIES: String = "url.prefix"

  def from(configuration: Configuration): JmapRfc8621Configuration =
    JmapRfc8621Configuration(Option(configuration.getString(URL_PREFIX_PROPERTIES)).getOrElse(LOCALHOST_URL_PREFIX))
}

case class JmapRfc8621Configuration(urlPrefixString: String) {
  val urlPrefix: URL = new URL(urlPrefixString)
  val apiUrl: URL = new URL(s"$urlPrefixString/jmap")
  val downloadUrl: URL = new URL(urlPrefixString + "/download/$accountId/$blobId/?type=$type&name=$name")
  val uploadUrl: URL = new URL(s"$urlPrefixString/upload")
  val eventSourceUrl: URL = new URL(s"$urlPrefixString/eventSource")
}
