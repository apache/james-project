/** ****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 * *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 * **************************************************************** */

package org.apache.james.jmap.api.pushsubscription

import java.net.URL
import java.time.Clock

import org.apache.james.jmap.api.identity.CustomIdentityDAOContract.bob
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscriptionCreationRequest, PushSubscriptionServerURL}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepositoryContract.ALICE
import org.apache.james.jmap.memory.pushsubscription.MemoryPushSubscriptionRepository
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono

class PushDeleteUserDataTaskStepTest {
  var pushSubscriptionRepository: PushSubscriptionRepository = _
  var testee: PushDeleteUserDataTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    pushSubscriptionRepository = new MemoryPushSubscriptionRepository(Clock.systemUTC())
    testee = new PushDeleteUserDataTaskStep(pushSubscriptionRepository)
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    assertThatCode(() => SMono(testee.deleteUserData(bob)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def shouldDeleteUserData(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    SMono.fromPublisher(pushSubscriptionRepository.save(ALICE, validRequest)).block().id

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(Flux.from(pushSubscriptionRepository.list(ALICE)).collectList().block())
      .isEmpty()
  }
}
