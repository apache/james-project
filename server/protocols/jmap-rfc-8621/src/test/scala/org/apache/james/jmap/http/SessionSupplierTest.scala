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

package org.apache.james.jmap.http

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.http.SessionSupplierTest.USERNAME
import org.apache.james.jmap.model.Id.Id
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object SessionSupplierTest {
  private val USERNAME = Username.of("username@james.org")
}

class SessionSupplierTest extends AnyWordSpec with Matchers {

  "generate" should {
    "return correct username" in {
      new SessionSupplier().generate(USERNAME).block().username should equal(USERNAME)
    }

    "return correct account" which {
      val accounts = new SessionSupplier().generate(USERNAME).block().accounts

      "has size" in {
        accounts should have size 1
      }

      "has name" in {
        accounts.view.mapValues(_.name).values.toList should equal(List(USERNAME))
      }

      "has id" in {
        val usernameHashCode: Id = "22267206120"
        accounts.keys.toList should equal(List(usernameHashCode))
      }
    }
  }
}
