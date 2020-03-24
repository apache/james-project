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
import org.scalatest.{MustMatchers, WordSpec}
import org.apache.james.mailbox.model.{MailboxACL => JavaMailboxACL}
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, Rfc4314Rights => JavaRfc4314Rights, Right => JavaRight}

import scala.jdk.CollectionConverters._

class RightsTest extends WordSpec with MustMatchers {
  val NEGATIVE = true
  val USERNAME: Username = Username.of("user")
  val OTHER_USERNAME: Username = Username.of("otherUser")

  "Right ofCharacter" should  {
    "recognise 'a'" in {
      Right.forChar('a') must be(Some(Right.Administer))
    }
    "recognise 'e'" in {
      Right.forChar('e') must be(Some(Right.Expunge))
    }
    "recognise 'i'" in {
      Right.forChar('i') must be(Some(Right.Insert))
    }
    "recognise 'l'" in {
      Right.forChar('l') must be(Some(Right.Lookup))
    }
    "recognise 'r'" in {
      Right.forChar('r') must be(Some(Right.Read))
    }
    "recognise 'w'" in {
      Right.forChar('w') must be(Some(Right.Write))
    }
    "recognise 't'" in {
      Right.forChar('t') must be(Some(Right.DeleteMessages))
    }
    "return empty when unknown" in {
      Right.forChar('k') must be(None)
    }
  }
  "From ACL" should  {
    "filter out group entries" in {
      val acl = new JavaMailboxACL(Map(
        EntryKey.createGroupEntryKey("group") -> JavaRfc4314Rights.fromSerializedRfc4314Rights("aet")).asJava)

      Rights.fromACL(MailboxACL.fromJava(acl)) must be(Rights.EMPTY)
    }
    "filter out negative users" in {
      val acl = new JavaMailboxACL(Map(
        EntryKey.createUserEntryKey(USERNAME, NEGATIVE) -> JavaRfc4314Rights.fromSerializedRfc4314Rights("aet")).asJava)

      Rights.fromACL(MailboxACL.fromJava(acl)) must be(Rights.EMPTY)
    }
    "accept users" in {
      val acl = new JavaMailboxACL(Map(
        EntryKey.createUserEntryKey(USERNAME) -> JavaRfc4314Rights.fromSerializedRfc4314Rights("aet")).asJava)

      Rights.fromACL(MailboxACL.fromJava(acl)) must be(Rights.of(USERNAME, Seq(Right.Administer, Right.Expunge, Right.DeleteMessages)))
    }
    "filter out unknown rights" in {
      val acl = new JavaMailboxACL(Map(
        EntryKey.createUserEntryKey(USERNAME) -> JavaRfc4314Rights.fromSerializedRfc4314Rights("aetpk")).asJava)

      Rights.fromACL(MailboxACL.fromJava(acl)) must be(Rights.of(USERNAME, Seq(Right.Administer, Right.Expunge, Right.DeleteMessages)))
    }
  }
  "To ACL" should  {
    "return empty when empty" in {
      Rights.EMPTY.toMailboxAcl.asJava must be(new JavaMailboxACL())
    }
    "return acl conversion" in {
      val user1 = Username.of("user1")
      val user2 = Username.of("user2")
      val expected = new JavaMailboxACL(Map(
          EntryKey.createUserEntryKey(user1) -> new JavaRfc4314Rights(JavaRight.Administer, JavaRight.DeleteMessages),
          EntryKey.createUserEntryKey(user2) -> new JavaRfc4314Rights(JavaRight.PerformExpunge, JavaRight.Lookup))
        .asJava)
      val jmapPojo = Rights.of(user1, Seq(Right.Administer, Right.DeleteMessages))
        .append(user2, Seq(Right.Expunge, Right.Lookup))

      jmapPojo.toMailboxAcl.asJava must be(expected)
    }
  }
  "Remove entries" should  {
    "return empty when empty" in {
      Rights.EMPTY.removeEntriesFor(USERNAME) must be(Rights.EMPTY)
    }
    "return empty when only user" in {
      Rights.of(USERNAME, Right.Lookup)
        .removeEntriesFor(USERNAME) must be(Rights.EMPTY)
    }
    "only remove specified users" in {
      val expected = Rights.of(OTHER_USERNAME, Right.Lookup)

      Rights.of(USERNAME, Right.Lookup)
        .append(OTHER_USERNAME, Right.Lookup)
        .removeEntriesFor(USERNAME) must be(expected)
    }
  }
  "mayAddItems" should  {
    "return empty when no user" in {
      Rights.EMPTY.mayAddItems(USERNAME) must be(NotApplicable)
    }
    "return false when no insert right" in {
      Rights.of(USERNAME, Seq(Right.Administer, Right.Expunge, Right.Lookup, Right.DeleteMessages, Right.Read, Right.Seen, Right.Write))
        .mayAddItems(USERNAME) must be(NotApplicable)
    }
    "return true when insert right" in {
      Rights.of(USERNAME, Right.Insert)
        .mayAddItems(USERNAME) must be(Applicable)
    }
  }
  "mayReadItems" should  {
    "return empty when no user" in {
      Rights.EMPTY.mayReadItems(USERNAME) must be(NotApplicable)
    }
    "return false when no read right" in {
      Rights.of(USERNAME, Seq(Right.Administer, Right.Expunge, Right.Lookup, Right.DeleteMessages, Right.Administer, Right.Seen, Right.Write))
        .mayReadItems(USERNAME) must be(NotApplicable)
    }
    "return true when read right" in {
      Rights.of(USERNAME, Right.Read)
        .mayReadItems(USERNAME) must be(Applicable)
    }
  }
  "mayRemoveItems" should  {
    "return empty when no user" in {
      Rights.EMPTY.mayRemoveItems(USERNAME) must be(NotApplicable)
    }
    "return false when no delete right" in {
      Rights.of(USERNAME, Seq(Right.Administer, Right.Expunge, Right.Lookup, Right.Read, Right.Administer, Right.Seen, Right.Write))
        .mayRemoveItems(USERNAME) must be(NotApplicable)
    }
    "return true when delete right" in {
      Rights.of(USERNAME, Right.DeleteMessages)
        .mayRemoveItems(USERNAME) must be(Applicable)
    }
  }
  "mayRename" should  {
    "return unsupported" in {
      Rights.EMPTY.mayRename(USERNAME) must be(Unsupported)
    }
  }
  "mayDelete" should  {
    "return unsupported" in {
      Rights.EMPTY.mayDelete(USERNAME) must be(Unsupported)
    }
  }
  "mayCreateChild" should  {
    "return unsupported" in {
      Rights.EMPTY.mayCreateChild(USERNAME) must be(Unsupported)
    }
  }
}
