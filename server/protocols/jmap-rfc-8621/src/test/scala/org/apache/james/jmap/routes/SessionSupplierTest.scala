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
import org.apache.james.jmap.core.{DefaultCapabilities, JmapRfc8621Configuration}
import org.apache.james.jmap.routes.SessionSupplierTest.USERNAME
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object SessionSupplierTest {
  private val USERNAME = Username.of("username@james.org")
}

class SessionSupplierTest extends AnyWordSpec with Matchers {

  "generate" should {
    "return correct username" in {
      new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION))
        .generate(USERNAME, Set(), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.urlPrefixes()).toOption.get.username should equal(USERNAME)
    }

    "return correct account" which {
      val accounts = new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION))
        .generate(USERNAME, Set(), JmapRfc8621Configuration.LOCALHOST_CONFIGURATION.urlPrefixes()).toOption.get.accounts

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
