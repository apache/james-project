/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you May not use this file except in compliance   *
 * with the License.  You May obtain a copy of the License at   *
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

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.TestId
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.compat.java8.OptionConverters._

class MailboxTest extends AnyWordSpec with Matchers {
  "sortOrder" should  {
    "be comparable" in {
      SortOrder.apply(4L).compare(SortOrder.apply(3L)) must equal(1L)
      SortOrder.apply(4L).compare(SortOrder.apply(4L)) must equal(0L)
      SortOrder.apply(4L).compare(SortOrder.apply(5L)) must equal(-1L)
    }
  }

  "namespace" should  {
    "return None when personal" in {
      MailboxNamespace.personal.owner.isEmpty must be(true)
    }
    "return owner when delegated" in {
      val username = Username.of("bob")
      MailboxNamespace.delegated(username).owner must be(Some(username))
    }
  }

  "mailbox hasRole" should  {
    "return false when None " in {
      Mailbox(
        id = TestId.of(42L),
        name = "Inbox",
        parentId = None,
        role = None,
        SortOrder.apply(3L),
        TotalEmails(3L),
        UnreadEmails(4L),
        TotalThreads(5L),
        UnreadThreads(6L),
        MailboxRights(MayReadItems(true),
          MayAddItems(true),
          MayRemoveItems(true),
          MaySetSeen(true),
          MaySetKeywords(true),
          MayCreateChild(true),
          MayRename(true),
          MayDelete(true),
          MaySubmit(true)),
        IsSubscribed(true),
        MailboxNamespace.personal,
        Rights.EMPTY,
        Quotas(Map()))
        .hasRole(Role.INBOX) must be(false)
    }
    "return false when different" in {
      Mailbox(
        id = TestId.of(42L),
        name = "Inbox",
        parentId = None,
        role = Some(Role.OUTBOX),
        SortOrder.apply(3L),
        TotalEmails(3L),
        UnreadEmails(4L),
        TotalThreads(5L),
        UnreadThreads(6L),
        MailboxRights(MayReadItems(true),
          MayAddItems(true),
          MayRemoveItems(true),
          MaySetSeen(true),
          MaySetKeywords(true),
          MayCreateChild(true),
          MayRename(true),
          MayDelete(true),
          MaySubmit(true)),
        IsSubscribed(true),
        MailboxNamespace.personal,
        Rights.EMPTY,
        Quotas(Map()))
        .hasRole(Role.INBOX) must be(false)
    }
    "return true when equals" in {
      Mailbox(
        id = TestId.of(42L),
        name = "Inbox",
        parentId = None,
        role = Some(Role.INBOX),
        SortOrder.apply(3L),
        TotalEmails(3L),
        UnreadEmails(4L),
        TotalThreads(5L),
        UnreadThreads(6L),
        MailboxRights(MayReadItems(true),
          MayAddItems(true),
          MayRemoveItems(true),
          MaySetSeen(true),
          MaySetKeywords(true),
          MayCreateChild(true),
          MayRename(true),
          MayDelete(true),
          MaySubmit(true)),
        IsSubscribed(true),
        MailboxNamespace.personal,
        Rights.EMPTY,
        Quotas(Map())).hasRole(Role.INBOX) must be(true)
    }
  }

  "mailbox hasSystemRole" should  {
    "return false when None" in {
      Mailbox(
        id = TestId.of(42L),
        name = "Inbox",
        parentId = None,
        role = None,
        SortOrder.apply(3L),
        TotalEmails(3L),
        UnreadEmails(4L),
        TotalThreads(5L),
        UnreadThreads(6L),
        MailboxRights(MayReadItems(true),
          MayAddItems(true),
          MayRemoveItems(true),
          MaySetSeen(true),
          MaySetKeywords(true),
          MayCreateChild(true),
          MayRename(true),
          MayDelete(true),
          MaySubmit(true)),
        IsSubscribed(true),
        MailboxNamespace.personal,
        Rights.EMPTY,
        Quotas(Map()))
        .hasSystemRole must be(false)
    }
    "return false when not system" in {
      Mailbox(
        id = TestId.of(42L),
        name = "Inbox",
        parentId = None,
        Role.from("any").asScala,
        SortOrder.apply(3L),
        TotalEmails(3L),
        UnreadEmails(4L),
        TotalThreads(5L),
        UnreadThreads(6L),
        myRights = MailboxRights(MayReadItems(true),
          MayAddItems(true),
          MayRemoveItems(true),
          MaySetSeen(true),
          MaySetKeywords(true),
          MayCreateChild(true),
          MayRename(true),
          MayDelete(true),
          MaySubmit(true)),
        IsSubscribed(true),
        MailboxNamespace.personal,
        Rights.EMPTY,
        Quotas(Map())).hasSystemRole must be(false)
    }
    "return true when system" in {
      Mailbox(
        id = TestId.of(42L),
        name = "Inbox",
        parentId = None,
        role = Some(Role.INBOX),
        SortOrder.apply(3L),
        TotalEmails(3L),
        UnreadEmails(4L),
        TotalThreads(5L),
        UnreadThreads(6L),
        MailboxRights(MayReadItems(true),
          MayAddItems(true),
          MayRemoveItems(true),
          MaySetSeen(true),
          MaySetKeywords(true),
          MayCreateChild(true),
          MayRename(true),
          MayDelete(true),
          MaySubmit(true)),
        IsSubscribed(true),
        MailboxNamespace.personal,
        Rights.EMPTY,
        Quotas(Map())).hasSystemRole must be(true)
    }
  }
}
