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

package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.json.MailboxSerializationTest.MAILBOX
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail.{IsSubscribed, Mailbox, MailboxNamespace, MailboxRights, MayAddItems, MayCreateChild, MayDelete, MayReadItems, MayRemoveItems, MayRename, MaySetKeywords, MaySetSeen, MaySubmit, PersonalNamespace, Quota, QuotaId, QuotaRoot, Quotas, Right, Rights, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, Value}
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.{MailboxId, TestId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

object MailboxSerializationTest {
  private val MAILBOX_ID: MailboxId = TestId.of(2)
  private val MAILBOX_NAME: MailboxName = "inbox"
  private val PARENT_ID: Option[MailboxId] = Option.apply(TestId.of(1))
  private val ROLE: Option[Role] = Option.apply(Role.INBOX)
  private val SORT_ORDER: SortOrder = SortOrder(10L)
  private val TOTAL_EMAILS: TotalEmails = TotalEmails(1234L)
  private val UNREAD_EMAILS: UnreadEmails = UnreadEmails(123L)
  private val TOTAL_THREADS: TotalThreads = TotalThreads(58L)
  private val UNREAD_THREADS: UnreadThreads = UnreadThreads(22L)
  private val IS_SUBSCRIBED: IsSubscribed = IsSubscribed(true)
  private val NAMESPACE: MailboxNamespace = PersonalNamespace()

  private val MY_RIGHTS: MailboxRights = MailboxRights(
    mayAddItems = MayAddItems(true),
    mayReadItems = MayReadItems(false),
    mayRemoveItems = MayRemoveItems(false),
    maySetSeen = MaySetSeen(true),
    maySetKeywords = MaySetKeywords(false),
    mayCreateChild = MayCreateChild(true),
    mayRename = MayRename(true),
    mayDelete = MayDelete(false),
    maySubmit = MaySubmit(false)
  )

  private val RIGHTS: Rights = Rights.of(Username.of("bob"), Seq(Right.Expunge, Right.Lookup))
    .append(Username.of("alice"), Seq(Right.Read, Right.Write))

  private val QUOTAS = Quotas(Map(
    QuotaId(QuotaRoot("quotaRoot", None)) -> Quota(Map(
      Quotas.Message -> Value(18L, Some(42L)),
      Quotas.Storage -> Value(12L, None))),
    QuotaId(QuotaRoot("quotaRoot2@localhost", Some(Domain.LOCALHOST))) -> Quota(Map(
      Quotas.Message -> Value(14L, Some(43L)),
      Quotas.Storage -> Value(19L, None)))))

  val MAILBOX: Mailbox = Mailbox(
    id = MAILBOX_ID,
    name = MAILBOX_NAME,
    parentId = PARENT_ID,
    role = ROLE,
    sortOrder = SORT_ORDER,
    totalEmails = TOTAL_EMAILS,
    unreadEmails = UNREAD_EMAILS,
    totalThreads = TOTAL_THREADS,
    unreadThreads = UNREAD_THREADS,
    myRights = MY_RIGHTS,
    isSubscribed = IS_SUBSCRIBED,
    namespace = NAMESPACE,
    rights = RIGHTS,
    quotas = QUOTAS
  )
}

class MailboxSerializationTest extends AnyWordSpec with Matchers {
  "Serialize Mailbox" should {
    "succeed " in {

      val expectedJson: String =
        """{
          |  "id":"2",
          |  "name":"inbox",
          |  "parentId":"1",
          |  "role":"inbox",
          |  "sortOrder":10,
          |  "totalEmails":1234,
          |  "unreadEmails":123,
          |  "totalThreads":58,
          |  "unreadThreads":22,
          |  "myRights":{
          |    "mayReadItems":false,
          |    "mayAddItems":true,
          |    "mayRemoveItems":false,
          |    "maySetSeen":true,
          |    "maySetKeywords":false,
          |    "mayCreateChild":true,
          |    "mayRename":true,
          |    "mayDelete":false,
          |    "maySubmit":false
          |  },
          |  "isSubscribed":true,
          |  "namespace":"Personal",
          |  "rights":{
          |    "bob":["e","l"],
          |    "alice":["r","w"]
          |  },
          |  "quotas":{
          |    "quotaRoot":{
          |      "Message":{"used":18,"max":42},
          |      "Storage":{"used":12}
          |    },
          |    "quotaRoot2@localhost":{
          |      "Message":{"used":14,"max":43},
          |      "Storage":{"used":19}
          |    }
          |  }
          |}""".stripMargin

      val serializer = new MailboxSerializer(new TestId.Factory)
      assertThatJson(Json.stringify(serializer.serialize(MAILBOX))).isEqualTo(expectedJson)
    }
  }
}
