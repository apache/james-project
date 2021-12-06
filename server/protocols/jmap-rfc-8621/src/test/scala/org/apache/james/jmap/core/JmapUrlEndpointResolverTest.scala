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

package org.apache.james.jmap.core

import java.net.{URI, URL}

import org.apache.commons.configuration2.{Configuration, PropertiesConfiguration}
import org.apache.james.jmap.core.JmapConfigProperties.{DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY, URL_PREFIX_PROPERTY, WEBSOCKET_URL_PREFIX_PROPERTY}
import org.apache.james.jmap.core.JmapUrlEndpointResolverTest.{emptyConfiguration, providedConfiguration}
import org.apache.james.jmap.routes.JmapUrlEndpointResolver
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object JmapUrlEndpointResolverTest {
  val emptyConfiguration: Configuration = new PropertiesConfiguration()
  def providedConfiguration(): Configuration = {
    val configuration: Configuration = new PropertiesConfiguration()
    configuration.addProperty(URL_PREFIX_PROPERTY, "http://random-domain.com")
    configuration.addProperty(WEBSOCKET_URL_PREFIX_PROPERTY, "ws://random-domain.com")
    configuration
  }
}

class JmapUrlEndpointResolverTest extends AnyWordSpec with Matchers {
  "JmapUrlEndpointResolverTest" should {
    "succeed to configuration urlPrefix when provided" in {
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(providedConfiguration())
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(jmapRfc8621Configuration)

      testee.apiUrl must be(new URL("http://random-domain.com/jmap"))
      testee.downloadUrl must be(new URL("http://random-domain.com/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(new URL("http://random-domain.com/upload/{accountId}"))
      testee.eventSourceUrl must be(new URL("http://random-domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
      testee.webSocketUrl must be(new URI("ws://random-domain.com/jmap/ws"))
    }

    "load default config for urlPrefix when no configuration provided" in {
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(emptyConfiguration)
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(jmapRfc8621Configuration)

      testee.apiUrl must be(new URL("http://localhost/jmap"))
      testee.downloadUrl must be(new URL("http://localhost/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(new URL("http://localhost/upload/{accountId}"))
      testee.eventSourceUrl must be(new URL("http://localhost/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
      testee.webSocketUrl must be(new URI("ws://localhost/jmap/ws"))
    }

    "dynamic jmap prefix should be set when has prefix request and configuration provided is true" in {
      val configuration: Configuration = providedConfiguration()
      configuration.addProperty(DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY, true)
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(configuration)
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(jmapRfc8621Configuration,
        Some(new URL("http://dynamic.tld/prefix")),
        Some(new URI("ws://dynamic.tld/prefix")))

      testee.apiUrl must be(new URL("http://dynamic.tld/prefix/jmap"))
      testee.downloadUrl must be(new URL("http://dynamic.tld/prefix/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(new URL("http://dynamic.tld/prefix/upload/{accountId}"))
      testee.eventSourceUrl must be(new URL("http://dynamic.tld/prefix/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
      testee.webSocketUrl must be(new URI("ws://dynamic.tld/prefix/jmap/ws"))
    }

    "dynamic jmap prefix should NOT be set when has prefix request and configuration provided is false" in {
      val configuration: Configuration = providedConfiguration()
      configuration.addProperty(DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY, false)
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(configuration)
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(jmapRfc8621Configuration,
        Some(new URL("http://dynamic.tld/prefix")),
        Some(new URI("ws://dynamic.tld/prefix")))

      testee.apiUrl must be(new URL("http://random-domain.com/jmap"))
      testee.downloadUrl must be(new URL("http://random-domain.com/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(new URL("http://random-domain.com/upload/{accountId}"))
      testee.eventSourceUrl must be(new URL("http://random-domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
      testee.webSocketUrl must be(new URI("ws://random-domain.com/jmap/ws"))
    }

    "dynamic jmap prefix should NOT be set when has prefix request and no configuration provided" in {
      val configuration: Configuration = providedConfiguration()
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(configuration)
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(jmapRfc8621Configuration,
        Some(new URL("http://dynamic.tld/prefix")),
        Some(new URI("ws://dynamic.tld/prefix")))

      testee.apiUrl must be(new URL("http://random-domain.com/jmap"))
      testee.downloadUrl must be(new URL("http://random-domain.com/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(new URL("http://random-domain.com/upload/{accountId}"))
      testee.eventSourceUrl must be(new URL("http://random-domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
      testee.webSocketUrl must be(new URI("ws://random-domain.com/jmap/ws"))
    }

    "dynamic jmap prefix should NOT be set when has not prefix request" in {
      val configuration: Configuration = providedConfiguration()
      configuration.addProperty(DYNAMIC_JMAP_PREFIX_RESOLUTION_ENABLED_PROPERTY, true)

      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(configuration)
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(jmapRfc8621Configuration, None, None)

      testee.apiUrl must be(new URL("http://random-domain.com/jmap"))
      testee.downloadUrl must be(new URL("http://random-domain.com/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(new URL("http://random-domain.com/upload/{accountId}"))
      testee.eventSourceUrl must be(new URL("http://random-domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
      testee.webSocketUrl must be(new URI("ws://random-domain.com/jmap/ws"))
    }
  }
}
