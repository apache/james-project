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
import java.util.function.Predicate

import com.github.fge.lambdas.Throwing
import com.google.common.collect.ImmutableList
import org.apache.james.core.Username
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.apache.james.mailbox.{DefaultMailboxes, MailboxSession, MailboxSessionUtil}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.apache.james.util.concurrency.ConcurrentTestRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

object MailboxesProvisionerTest {
  private val USERNAME: Username = Username.of("username")
}

class MailboxesProvisionerTest {
  import MailboxesProvisionerTest._

  var testee: MailboxesProvisioner = _
  var session: MailboxSession = _
  var mailboxManager: InMemoryMailboxManager = _
  var subscriptionManager: StoreSubscriptionManager = _

  @BeforeEach
  def setup(): Unit = {
    session = MailboxSessionUtil.create(USERNAME)
    mailboxManager = InMemoryIntegrationResources.defaultResources.getMailboxManager
    subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory, mailboxManager.getMapperFactory, mailboxManager.getEventBus)
    testee = new MailboxesProvisioner(mailboxManager, new RecordingMetricFactory)
  }

  @Test
  def createMailboxesIfNeededShouldCreateSystemMailboxes(): Unit = {
    testee.createMailboxesIfNeeded(session).block()

    assertThat(mailboxManager.list(session))
      .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES
        .stream
        .map((mailboxName: String) => MailboxPath.forUser(USERNAME, mailboxName))
        .collect(ImmutableList.toImmutableList()))
  }

  @Test
  def createMailboxesIfNeededShouldCreateSpamWhenOtherSystemMailboxesExist(): Unit = {
    DefaultMailboxes.DEFAULT_MAILBOXES
      .stream
      .filter(Predicate.not(Predicate.isEqual(DefaultMailboxes.SPAM)))
      .forEach(Throwing.consumer((mailbox: String) => mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, mailbox), session)))

    testee.createMailboxesIfNeeded(session).block()

    assertThat(mailboxManager.list(session))
      .contains(MailboxPath.forUser(USERNAME, DefaultMailboxes.SPAM))
  }

  @Test
  def createMailboxesIfNeededShouldSubscribeMailboxes(): Unit = {
    testee.createMailboxesIfNeeded(session).block()

    assertThat(subscriptionManager.subscriptions(session))
      .containsOnlyElementsOf(DefaultMailboxes.defaultMailboxesAsPath(USERNAME))
  }

  @Test
  def createMailboxesIfNeededShouldNotGenerateExceptionsInConcurrentEnvironment(): Unit = {
    ConcurrentTestRunner.builder
      .operation((threadNumber: Int, step: Int) => testee.createMailboxesIfNeeded(session).block())
      .threadCount(10)
      .runSuccessfullyWithin(Duration.ofSeconds(10))

    assertThat(mailboxManager.list(session))
      .containsOnlyElementsOf(DefaultMailboxes.defaultMailboxesAsPath(USERNAME))
  }
}
