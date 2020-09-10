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

package org.apache.james.jmap.model

import java.net.URL

import org.apache.commons.configuration2.{Configuration, PropertiesConfiguration}
import org.apache.james.jmap.model.JmapRfc8621Configuration.URL_PREFIX_PROPERTIES
import org.apache.james.jmap.model.JmapRfc8621ConfigurationTest.{emptyConfiguration, providedConfiguration}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object JmapRfc8621ConfigurationTest {
  val emptyConfiguration: Configuration = new PropertiesConfiguration()
  def providedConfiguration(): Configuration = {
    val configuration: Configuration = new PropertiesConfiguration()
    configuration.addProperty(URL_PREFIX_PROPERTIES, "http://random-domain.com")
    configuration
  }
}

class JmapRfc8621ConfigurationTest extends AnyWordSpec with Matchers {
  "JmapRfc8621ConfigurationTest" should {
    "succeed to configuration urlPrefix when provided" in {
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(providedConfiguration())

      jmapRfc8621Configuration.apiUrl must be(new URL("http://random-domain.com/jmap"))
      jmapRfc8621Configuration.downloadUrl must be(new URL("http://random-domain.com/download/$accountId/$blobId/?type=$type&name=$name "))
      jmapRfc8621Configuration.uploadUrl must be(new URL("http://random-domain.com/upload"))
      jmapRfc8621Configuration.eventSourceUrl must be(new URL("http://random-domain.com/eventSource"))
    }

    "load default config for urlPrefix when no configuration provided" in {
      val jmapRfc8621Configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.from(emptyConfiguration)

      jmapRfc8621Configuration.apiUrl must be(new URL("http://localhost/jmap"))
      jmapRfc8621Configuration.downloadUrl must be(new URL("http://localhost/download/$accountId/$blobId/?type=$type&name=$name"))
      jmapRfc8621Configuration.uploadUrl must be(new URL("http://localhost/upload"))
      jmapRfc8621Configuration.eventSourceUrl must be(new URL("http://localhost/eventSource"))
    }
  }
}
