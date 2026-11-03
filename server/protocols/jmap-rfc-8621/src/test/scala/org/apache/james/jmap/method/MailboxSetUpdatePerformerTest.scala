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

package org.apache.james.jmap.method

import org.apache.james.mailbox.model.MailboxACL
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, Rfc4314Rights}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class MailboxSetUpdatePerformerTest extends AnyWordSpec with Matchers {
  private val bob: EntryKey = EntryKey.createUserEntryKey("bob@domain.tld")
  private val alice: EntryKey = EntryKey.createUserEntryKey("alice@domain.tld")
  private val cedric: EntryKey = EntryKey.createUserEntryKey("cedric@domain.tld")
  private val readRights: Rfc4314Rights = Rfc4314Rights.fromSerializedRfc4314Rights("lr")
  private val previousAcl: MailboxACL = new MailboxACL(Map(bob -> readRights, alice -> readRights).asJava)

  "revertCommands" should {
    "only touch the given entries" in {
      // Another entry changed concurrently (alice revoked) must not be put back by the revert
      MailboxSetUpdatePerformer.revertCommands(previousAcl, Set(bob)).map(_.getEntryKey) shouldBe Seq(bob)
    }

    "give back the previous rights of a modified entry" in {
      val concurrentlyModified: MailboxACL = new MailboxACL(Map(bob -> Rfc4314Rights.fromSerializedRfc4314Rights("lrw")).asJava)

      MailboxSetUpdatePerformer.revertCommands(previousAcl, Set(bob))
        .foldLeft(concurrentlyModified)((acl, command) => acl.apply(command)) shouldBe new MailboxACL(Map(bob -> readRights).asJava)
    }

    "remove an entry that did not exist before" in {
      val withCedric: MailboxACL = new MailboxACL(Map(cedric -> readRights).asJava)

      MailboxSetUpdatePerformer.revertCommands(previousAcl, Set(cedric))
        .foldLeft(withCedric)((acl, command) => acl.apply(command)) shouldBe MailboxACL.EMPTY
    }
  }
}
