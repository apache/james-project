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

import org.apache.james.jmap.core.{AccountId, Id}
import org.apache.james.mailbox.model.TestId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}

object MailboxSetSerializationTest {
  private val ACCOUNT_ID: AccountId = AccountId(Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get)
  private val SERIALIZER: MailboxSerializer = new MailboxSerializer(new TestId.Factory)
}

class MailboxSetSerializationTest extends AnyWordSpec with Matchers {
  import MailboxSetSerializationTest._

  private def setRequest(update: Option[String] = None,
                         destroy: Option[String] = None,
                         create: Option[JsObject] = None): JsObject = {
    val fields = Seq(
      Some("accountId" -> Json.toJson(ACCOUNT_ID.id.value)),
      create.map(value => "create" -> value),
      update.map(reference => "update" -> Json.obj(reference -> Json.obj("name" -> "newName"))),
      destroy.map(reference => "destroy" -> Json.arr(reference))).flatten
    JsObject(fields)
  }

  "Deserialize Mailbox/set" should {
    "accept a 255 characters creation reference in update and destroy" in {
      val reference = "#" + ("a" * 255)
      val result = SERIALIZER.deserializeMailboxSetRequest(setRequest(update = Some(reference), destroy = Some(reference)))
      result shouldBe a[JsSuccess[_]]
    }

    "accept a 255 characters creation reference as a creation parentId" in {
      val reference = "#" + ("a" * 255)
      val result = SERIALIZER.deserializeMailboxSetRequest(setRequest(create = Some(Json.obj(
        "child" -> Json.obj("name" -> "child", "parentId" -> reference)))))
      result shouldBe a[JsSuccess[_]]
    }

    "reject a creation reference longer than 255 characters" in {
      val reference = "#" + ("a" * 256)
      val result = SERIALIZER.deserializeMailboxSetRequest(setRequest(update = Some(reference), destroy = Some(reference)))
      result shouldBe a[JsError]
    }

    "reject a plain id longer than 255 characters" in {
      val id = "a" * 256
      val result = SERIALIZER.deserializeMailboxSetRequest(setRequest(update = Some(id), destroy = Some(id)))
      result shouldBe a[JsError]
    }
  }
}
