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

package org.apache.james.jmap.mail

import org.apache.james.mailbox.model.MailboxACL
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SharedWithAnyoneValidatorTest extends AnyWordSpec with Matchers {
  "isValidPatch" should {
    "accept the Post right for anyone" in {
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.ANYONE_KEY, Seq(Right.Post)) shouldBe true
    }

    "reject other rights for anyone" in {
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.ANYONE_KEY, Seq(Right.Lookup)) shouldBe false
    }

    "accept the Post right for authenticated" in {
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.AUTHENTICATED_KEY, Seq(Right.Post)) shouldBe true
    }

    "reject other rights for authenticated" in {
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.AUTHENTICATED_KEY, Seq(Right.Lookup)) shouldBe false
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.AUTHENTICATED_KEY, Seq(Right.Read, Right.Lookup)) shouldBe false
    }

    "accept any right for the negative authenticated key, that only removes rights" in {
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.AUTHENTICATED_NEGATIVE_KEY, Seq(Right.Lookup)) shouldBe true
    }

    "accept any right for a user" in {
      SharedWithAnyoneValidator.isValidPatch(MailboxACL.EntryKey.createUserEntryKey("bob@domain.tld"), Seq(Right.Lookup, Right.Read)) shouldBe true
    }
  }

  "invalidPatchMessage" should {
    "name the rejected identifier" in {
      SharedWithAnyoneValidator.invalidPatchMessage(Map(
        MailboxACL.EntryKey.createUserEntryKey("bob@domain.tld") -> Seq(Right.Lookup),
        MailboxACL.AUTHENTICATED_KEY -> Seq(Right.Lookup))) shouldBe "only the `Post` right can be granted to the identifier `authenticated`"
    }

    "keep the message of the anyone identifier" in {
      SharedWithAnyoneValidator.invalidPatchMessage(MailboxACL.ANYONE_KEY) shouldBe "only the `Post` right can be granted to the identifier `anyone`"
    }
  }
}
