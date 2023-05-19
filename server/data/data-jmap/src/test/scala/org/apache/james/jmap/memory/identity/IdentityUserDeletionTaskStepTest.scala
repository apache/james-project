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

package org.apache.james.jmap.memory.identity

import org.apache.james.jmap.api.identity.CustomIdentityDAOContract.{CREATION_REQUEST, bob}
import org.apache.james.jmap.api.identity.IdentityUserDeletionTaskStep
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono

class IdentityUserDeletionTaskStepTest {
  var identityDAO: MemoryCustomIdentityDAO = _
  var testee: IdentityUserDeletionTaskStep = _

  @BeforeEach
  def setUp(): Unit = {
    identityDAO = new MemoryCustomIdentityDAO()
    testee = new IdentityUserDeletionTaskStep(identityDAO)
  }

  @Test
  def shouldDeleteUserIdentity(): Unit = {
    SMono(identityDAO
      .save(bob, CREATION_REQUEST))
      .block()

    SMono(testee.deleteUserData(bob)).block()

    assertThat(Flux.from(identityDAO.list(bob)).collectList().block()).isEmpty()
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    assertThatCode(() => SMono(testee.deleteUserData(bob)).block())
      .doesNotThrowAnyException()
  }
}
