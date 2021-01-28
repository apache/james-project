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
package org.apache.james.jmap.rfc8621.contract

import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

trait WebSocketContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def apiRequestsShouldBeProcessed(): Unit = {
    /*
    * TODO test an echo response - request (success)
    * */
  }

  @Test
  def nonJsonPayloadShouldTriggerError(): Unit = {
    /*
    * TODO send 'the quick brown fox' and get an error level error
    * */
  }

  @Test
  def handshakeShouldBeAuthenticated(): Unit = {
    /*
    * TODO set up no auth
    * */
  }

  @Test
  def noTypeFiledShouldTriggerError(): Unit = {
    /*
    * TODO send something without @type and get an error level error
    * */
  }

  @Test
  def badTypeFieldShouldTriggerError(): Unit = {
    /*
    * TODO send something with @type being a JsNumber and get an error level error
    * */
  }

  @Test
  def unknownTypeFieldShouldTriggerError(): Unit = {
    /*
    * TODO send something with @type being a JsString("unknown") and get an error level error
    * */
  }

  @Test
  def requestLevelErrorShouldReturnAPIError(): Unit = {
    /*
    * TODO send a request triggering a method level error (eg Mailbox/get with an invalid JSON payload)
    * */
  }
}
