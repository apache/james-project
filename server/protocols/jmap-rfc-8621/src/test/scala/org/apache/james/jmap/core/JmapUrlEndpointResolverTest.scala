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

import java.net.URI

import org.apache.commons.configuration2.{Configuration, PropertiesConfiguration}
import org.apache.james.jmap.core.JmapConfigProperties.{URL_PREFIX_PROPERTY, WEBSOCKET_URL_PREFIX_PROPERTY}
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
      val testee : JmapUrlEndpointResolver= new JmapUrlEndpointResolver(UrlPrefixes(new URI("http://random-domain.com"), new URI("ws://random-domain.com")))

      testee.apiUrl must be(URL("http://random-domain.com/jmap"))
      testee.downloadUrl must be(URL("http://random-domain.com/download/{accountId}/{blobId}?type={type}&name={name}"))
      testee.uploadUrl must be(URL("http://random-domain.com/upload/{accountId}"))
      testee.eventSourceUrl must be(URL("http://random-domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}"))
    }
  }
}
