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
package org.apache.james.jmap.method

import org.apache.james.jmap.json.Fixture.{invocation1, invocation2}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.{CapabilityIdentifier, Invocation}
import org.apache.james.mailbox.MailboxSession
import org.mockito.Mockito.mock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactor.core.scala.publisher.SMono

class CoreEchoMethodTest extends AnyWordSpec with Matchers {
  private val echoMethod: CoreEchoMethod = new CoreEchoMethod()
  private val mockedSession: MailboxSession = mock(classOf[MailboxSession])
  private val capabilities: Set[CapabilityIdentifier] = Set(CapabilityIdentifier.JMAP_CORE, CapabilityIdentifier.JMAP_MAIL)

  "CoreEcho" should {
    "Process" should {
      "success and return the same with parameters as the invocation request" in {
        val expectedResponse: Invocation = invocation1
        val dataResponse = SMono.fromPublisher(echoMethod.process(capabilities, invocation1, mockedSession)).block()

        dataResponse shouldBe expectedResponse
      }

      "success and not return anything else different than the original invocation" in {
        val wrongExpected: Invocation = invocation2
        val dataResponse = SMono.fromPublisher(echoMethod.process(capabilities, invocation1, mockedSession)).block()
        
        dataResponse should not be(wrongExpected)
      }
    }
  }
}
