/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.routes

import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.JMAP_WEBPUSH_VAPID
import org.apache.james.jmap.core.{DefaultCapabilities, JmapRfc8621Configuration, UuidState, WebPushVapidCapabilityProperties}
import org.apache.james.jmap.pushsubscription.VapidTestFixture
import org.apache.james.jmap.routes.SessionSupplierTest.USERNAME
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object SessionSupplierTest {
  private val USERNAME = Username.of("username@james.org")
}

class SessionSupplierTest extends AnyWordSpec with Matchers {

  "generate" should {
    "return correct username" in {
      new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION)
        .generate(USERNAME, Set(), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.urlPrefixes()).blockOption().get.username should equal(USERNAME)
    }

    "not advertise VAPID when no key pair is configured" in {
      new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION)
        .generate(USERNAME, Set(), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.urlPrefixes()).blockOption().get
        .capabilities.ids should not contain JMAP_WEBPUSH_VAPID
    }

    "advertise the VAPID application server key when a key pair is configured" in {
      val configuration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.copy(vapid = Some(VapidTestFixture.CONFIGURATION))

      new SessionSupplier(DefaultCapabilities.supported(configuration), configuration)
        .generate(USERNAME, Set(), configuration.urlPrefixes()).blockOption().get
        .capabilities.capabilities
        .filter(_.identifier() == JMAP_WEBPUSH_VAPID)
        .map(_.properties()) should equal(Set(WebPushVapidCapabilityProperties(VapidTestFixture.PUBLIC_KEY)))
    }

    "not advertise VAPID when Web Push is disabled" in {
      val configuration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.copy(vapid = Some(VapidTestFixture.CONFIGURATION), webPushEnabled = false)

      new SessionSupplier(DefaultCapabilities.supported(configuration), configuration)
        .generate(USERNAME, Set(), configuration.urlPrefixes()).blockOption().get
        .capabilities.ids should not contain JMAP_WEBPUSH_VAPID
    }

    "keep the session state when no key pair is configured" in {
      new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION)
        .generate(USERNAME, Set(), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.urlPrefixes()).blockOption().get
        .state should equal(UuidState.INSTANCE)
    }

    "keep the session state when Web Push is disabled" in {
      val configuration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.copy(vapid = Some(VapidTestFixture.CONFIGURATION), webPushEnabled = false)

      new SessionSupplier(DefaultCapabilities.supported(configuration), configuration)
        .generate(USERNAME, Set(), configuration.urlPrefixes()).blockOption().get
        .state should equal(UuidState.INSTANCE)
    }

    "change the session state with the VAPID key" in {
      val configuration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.copy(vapid = Some(VapidTestFixture.CONFIGURATION))
      val rotated = configuration.copy(vapid = Some(VapidTestFixture.CONFIGURATION.copy(applicationServerKey = "another key")))

      def state(configuration: JmapRfc8621Configuration): UuidState =
        new SessionSupplier(DefaultCapabilities.supported(configuration), configuration)
          .generate(USERNAME, Set(), configuration.urlPrefixes()).blockOption().get.state

      state(configuration) should not equal UuidState.INSTANCE
      state(configuration) should equal(state(configuration))
      state(rotated) should not equal state(configuration)
    }

    "return correct account" which {
      val accounts = new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION)
        .generate(USERNAME, Set(), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.urlPrefixes()).blockOption().get.accounts

      "has size" in {
        accounts should have size 1
      }

      "has name" in {
        accounts.map(_.name) should equal(List(USERNAME))
      }

      "has accountId being hash of username in string" in {
        accounts.map(_.accountId)
          .map(_.id.value) should equal(List("0cb33e029628ea603d1b988f0f81b069d89b6c5a093e12b275ecdc626bd7458c"))
      }
    }
  }
}
