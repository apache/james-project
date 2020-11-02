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

package org.apache.james.jmap.core

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail._
import org.apache.james.mailbox.model.MailboxPath
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object MailboxValidationTest {
  private val mailboxName: MailboxName = "name"
  private val user: Username = Username.of("user")
  private val mailboxPath: MailboxPath = MailboxPath.forUser(user, mailboxName)
  private val pathDelimiter: Char = '.'
  private val unreadEmails: UnsignedInt = 1L
  private val unreadThreads: UnsignedInt = 2L
  private val totalEmails: UnsignedInt = 3L
  private val totalThreads: UnsignedInt = 4L
}

class MailboxValidationTest extends AnyWordSpec with Matchers {
  import MailboxValidationTest._

  "MailboxValidation" should {
    "succeed" in {
      val expectedResult: Either[Exception, MailboxValidation] = scala.Right(MailboxValidation(
        mailboxName = mailboxName,
        unreadEmails = UnreadEmails(unreadEmails),
        unreadThreads = UnreadThreads(unreadThreads),
        totalEmails = TotalEmails(totalEmails),
        totalThreads = TotalThreads(totalThreads)))

      MailboxValidation.validate(
        mailboxPath,
        pathDelimiter,
        unreadEmails.value,
        unreadThreads.value,
        totalEmails.value,
        totalThreads.value) must be(expectedResult)
    }

    "fail when mailboxPath is invalid" in {
      MailboxValidation.validate(
        MailboxPath.forUser(user, ""),
        pathDelimiter,
        unreadEmails.value,
        unreadThreads.value,
        totalEmails.value,
        totalThreads.value) mustBe a[Left[_, _]]
    }

    "fail when unreadEmails is invalid" in {
      MailboxValidation.validate(
        mailboxPath,
        pathDelimiter,
        -1L,
        unreadThreads.value,
        totalEmails.value,
        totalThreads.value) mustBe a[Left[_, _]]
    }

    "fail when unreadThreads is invalid" in {
      MailboxValidation.validate(
        mailboxPath,
        pathDelimiter,
        unreadEmails.value,
        -1L,
        totalEmails.value,
        totalThreads.value) mustBe a[Left[_, _]]
    }

    "fail when totalEmails is invalid" in {
      MailboxValidation.validate(
        mailboxPath,
        pathDelimiter,
        unreadEmails.value,
        unreadThreads.value,
        -1L,
        totalThreads.value) mustBe a[Left[_, _]]
    }

    "fail when totalThreads is invalid" in {
      MailboxValidation.validate(
        mailboxPath,
        pathDelimiter,
        unreadEmails.value,
        unreadThreads.value,
        totalEmails.value,
        -1L) mustBe a[Left[_, _]]
    }
  }
}
