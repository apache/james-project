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

package org.apache.james.jmap.model

import eu.timepit.refined.auto._
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail._
import org.apache.james.jmap.model.UnsignedInt.UnsignedInt
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object MailboxValidationTest {
  private val mailboxName: MailboxName = "name"
  private val unreadEmails: UnsignedInt = 1L
  private val unreadThreads: UnsignedInt = 2L
  private val totalEmails: UnsignedInt = 3L
  private val totalThreads: UnsignedInt = 4L
}

class MailboxValidationTest extends AnyWordSpec with Matchers {
  import MailboxValidationTest._

  "MailboxValidation" should {
    "succeed" in {
      val validMailboxname: Either[IllegalArgumentException, MailboxName] = MailboxName.validate(mailboxName)
      val validUnreadEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadEmails)
      val validUnreadThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadThreads)
      val validTotalEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalEmails)
      val validTotalThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalThreads)

      val expectedResult: Either[Exception, MailboxValidation] = scala.Right(MailboxValidation(
        mailboxName = mailboxName,
        unreadEmails = UnreadEmails(unreadEmails),
        unreadThreads = UnreadThreads(unreadThreads),
        totalEmails = TotalEmails(totalEmails),
        totalThreads = TotalThreads(totalThreads)))

      MailboxValidation.validate(
        validMailboxname,
        validUnreadEmails,
        validUnreadThreads,
        validTotalEmails,
        validTotalThreads) must be(expectedResult)
    }

    "fail when mailboxName is invalid" in {
      val invalidMailboxname: Either[IllegalArgumentException, MailboxName] = MailboxName.validate("")
      val validUnreadEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadEmails)
      val validUnreadThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadThreads)
      val validTotalEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalEmails)
      val validTotalThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalThreads)

      MailboxValidation.validate(
        invalidMailboxname,
        validUnreadEmails,
        validUnreadThreads,
        validTotalEmails,
        validTotalThreads) mustBe a[Left[_, _]]
    }

    "fail when unreadEmails is invalid" in {
      val validMailboxname: Either[IllegalArgumentException, MailboxName] = MailboxName.validate(mailboxName)
      val invalidUnreadEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(-1L)
      val validUnreadThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadThreads)
      val validTotalEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalEmails)
      val validTotalThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalThreads)

      MailboxValidation.validate(
        validMailboxname,
        invalidUnreadEmails,
        validUnreadThreads,
        validTotalEmails,
        validTotalThreads) mustBe a[Left[_, _]]
    }

    "fail when unreadThreads is invalid" in {
      val validMailboxname: Either[IllegalArgumentException, MailboxName] = MailboxName.validate(mailboxName)
      val validUnreadEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadEmails)
      val invalidUnreadThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(-1L)
      val validTotalEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalEmails)
      val validTotalThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalThreads)

      MailboxValidation.validate(
        validMailboxname,
        validUnreadEmails,
        invalidUnreadThreads,
        validTotalEmails,
        validTotalThreads) mustBe a[Left[_, _]]
    }

    "fail when totalEmails is invalid" in {
      val validMailboxname: Either[IllegalArgumentException, MailboxName] = MailboxName.validate(mailboxName)
      val validUnreadEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadEmails)
      val validUnreadThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadThreads)
      val invalidTotalEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(-1L)
      val validTotalThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalThreads)

      MailboxValidation.validate(
        validMailboxname,
        validUnreadEmails,
        validUnreadThreads,
        invalidTotalEmails,
        validTotalThreads) mustBe a[Left[_, _]]
    }

    "fail when totalThreads is invalid" in {
      val validMailboxname: Either[IllegalArgumentException, MailboxName] = MailboxName.validate(mailboxName)
      val validUnreadEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadEmails)
      val validUnreadThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(unreadThreads)
      val validTotalEmails: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(totalEmails)
      val invalidTotalThreads: Either[NumberFormatException, UnsignedInt] = UnsignedInt.validate(-1L)

      MailboxValidation.validate(
        validMailboxname,
        validUnreadEmails,
        validUnreadThreads,
        validTotalEmails,
        invalidTotalThreads) mustBe a[Left[_, _]]
    }
  }
}
