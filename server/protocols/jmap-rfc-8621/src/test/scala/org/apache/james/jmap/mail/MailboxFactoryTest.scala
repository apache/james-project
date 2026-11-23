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
 * ***************************************************************/

package org.apache.james.jmap.mail

import org.apache.james.core.Username
import org.apache.james.mailbox.acl.UnionMailboxACLResolver
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, Rfc4314Rights => JavaRfc4314Rights}
import org.apache.james.mailbox.model.MailboxMetaData.{Children, Selectability}
import org.apache.james.mailbox.model.{MailboxCounters, MailboxMetaData, MailboxPath, TestId, UidValidity, MailboxACL => JavaMailboxACL, Mailbox => JavaMailbox}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MailboxSessionUtil}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactor.core.scala.publisher.SMono

class MailboxFactoryTest extends AnyWordSpec with Matchers {
  private val BOB = Username.of("bob")
  private val ALICE = Username.of("alice")
  private val bobSession = MailboxSessionUtil.create(BOB)
  private val mailboxManager: MailboxManager = {
    val resolver = new UnionMailboxACLResolver()
    val manager = mock(classOf[MailboxManager])
    when(manager.myRights(any(classOf[JavaMailbox]), any(classOf[MailboxSession])))
      .thenAnswer(invocation => {
        val mailbox = invocation.getArgument[JavaMailbox](0)
        resolver.resolveRights(invocation.getArgument[MailboxSession](1).getUser, mailbox.getACL, mailbox.getUser)
      })
    manager
  }
  private val testee = new MailboxFactory(mailboxManager, new DefaultNamespaceFactory, SortOrderProvider.DEFAULT)
  private val ownerQuotas = Quotas.from(QuotaId(QuotaRoot("#private&alice", None)), Quota.from(Map()))

  private def delegatedMailbox(rights: String): Mailbox =
    delegatedMailbox(new JavaMailboxACL(java.util.Map.of(EntryKey.createUserEntryKey(BOB), JavaRfc4314Rights.fromSerializedRfc4314Rights(rights))))

  private def delegatedMailbox(acl: JavaMailboxACL): Mailbox = {
    val id = TestId.of(42L)
    val mailbox = new JavaMailbox(MailboxPath.forUser(ALICE, "shared"), UidValidity.of(1L), id)
    mailbox.setACL(acl)
    val metaData = new MailboxMetaData(mailbox, '.', Children.HAS_NO_CHILDREN, Selectability.NONE, acl, MailboxCounters.empty(id))

    testee.create(metaData, bobSession, Subscriptions(Set()), Map(), _ => SMono.just(ownerQuotas))
      .block()
  }

  "mayRemoveItems" should {
    "be false for a delegated mailbox without the expunge right" in {
      delegatedMailbox("lrt").myRights.mayRemoveItems must be(MayRemoveItems(false))
    }
    "be true for a delegated mailbox with both the delete and expunge rights" in {
      delegatedMailbox("lrte").myRights.mayRemoveItems must be(MayRemoveItems(true))
    }
  }

  "quotas" should {
    "be empty for a delegated mailbox without the read right" in {
      delegatedMailbox("l").quotas must be(Quotas.from(Map[QuotaId, Quota]()))
    }
    "be empty for a delegated mailbox whose read right is negated" in {
      val acl = new JavaMailboxACL(java.util.Map.of(
        EntryKey.createUserEntryKey(BOB), JavaRfc4314Rights.fromSerializedRfc4314Rights("lr"),
        EntryKey.createUserEntryKey(BOB, JavaMailboxACL.NEGATIVE_KEY), JavaRfc4314Rights.fromSerializedRfc4314Rights("r")))

      delegatedMailbox(acl).quotas must be(Quotas.from(Map[QuotaId, Quota]()))
    }
    "be the owner ones for a delegated mailbox with the read right" in {
      delegatedMailbox("lr").quotas must be(ownerQuotas)
    }
  }
}
