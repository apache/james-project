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

package org.apache.james.jmap.http

import java.time.Duration

import org.apache.james.core.Username
import org.apache.james.domainlist.api.DomainList
import org.apache.james.mailbox.{MailboxSession, MailboxSessionUtil}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.apache.james.user.api.{UsersRepository, UsersRepositoryException}
import org.apache.james.user.memory.MemoryUsersRepository
import org.apache.james.util.concurrency.ConcurrentTestRunner
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.{mock, verify, verifyNoMoreInteractions, when}

object UserProvisioningTest {
  private val USERNAME: Username = Username.of("username")
  private val USERNAME_WITH_DOMAIN: Username = Username.of("username@james.org")
  private val NO_DOMAIN_LIST: DomainList = null
}

class UserProvisioningTest {
  import UserProvisioningTest._

  var testee: UserProvisioning = _
  var usersRepository: MemoryUsersRepository = _

  @BeforeEach
  def setup(): Unit = {
    usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST)
    testee = new UserProvisioning(usersRepository, new RecordingMetricFactory)
  }

  @Test
  def filterShouldDoNothingOnNullSession(): Unit = {
    testee.provisionUser(null).block()

    assertThat(usersRepository.list).toIterable
      .isEmpty
  }

  @Test
  def filterShouldAddUsernameWhenNoVirtualHostingAndMailboxSessionContainsUsername(): Unit = {
    usersRepository.setEnableVirtualHosting(false)
    val mailboxSession: MailboxSession = MailboxSessionUtil.create(USERNAME)

    testee.provisionUser(mailboxSession).block()

    assertThat(usersRepository.list).toIterable
      .contains(USERNAME)
  }

  @Test
  def filterShouldFailOnInvalidVirtualHosting(): Unit = {
    usersRepository.setEnableVirtualHosting(false)
    val mailboxSession: MailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN)

    assertThatThrownBy(() => testee.provisionUser(mailboxSession).block())
      .hasCauseInstanceOf(classOf[UsersRepositoryException])
  }

  @Test
  def filterShouldNotTryToAddUserWhenReadOnlyUsersRepository(): Unit = {
    val usersRepository: UsersRepository = mock(classOf[UsersRepository])
    when(usersRepository.isReadOnly).thenReturn(true)
    testee = new UserProvisioning(usersRepository, new RecordingMetricFactory)

    val mailboxSession: MailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN)
    testee.provisionUser(mailboxSession).block()

    verify(usersRepository).isReadOnly
    verifyNoMoreInteractions(usersRepository)
  }

  @Test
  def testConcurrentAccessToFilterShouldNotThrow(): Unit = {
    val session: MailboxSession = MailboxSessionUtil.create(USERNAME)

    ConcurrentTestRunner.builder
      .operation((threadNumber: Int, step: Int) => testee.provisionUser(session))
      .threadCount(2)
      .runSuccessfullyWithin(Duration.ofMinutes(1))
  }
}
