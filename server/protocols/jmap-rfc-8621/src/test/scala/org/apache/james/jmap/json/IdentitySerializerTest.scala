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
import org.apache.james.core.MailAddress
import org.apache.james.jmap.api.model._
import org.apache.james.jmap.core.{AccountId, Properties, UuidState}
import org.apache.james.jmap.mail.{IdentityGet, IdentityGetResponse}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, JsObject, Json}

class IdentitySerializerTest extends AnyWordSpec with Matchers {
  private val identity = Identity(IdentityId.generate, name = IdentityName(""), email = new MailAddress("bob@example.com"),
    replyTo = None, bcc = None, textSignature = TextSignature(""), htmlSignature = HtmlSignature(""), mayDelete = MayDeleteIdentity(true))

  private def serialize(value: Identity, properties: Properties): JsObject =
    (IdentitySerializer.serialize(IdentityGetResponse(AccountId(Fixture.id), UuidState.INSTANCE, List(value), None),
      properties, Set()) \ "list")(0).as[JsObject]

  "Identity/get serialization" should {
    "write nullable properties and filter unrequested ones" in {
      val result = serialize(identity, IdentityGet.allProperties)
      (result \ "replyTo").toOption should equal(Some(JsNull))
      (result \ "bcc").toOption should equal(Some(JsNull))
      (result \ "name").as[String] should equal("")
      serialize(identity, Properties("name")) should equal(Json.obj("id" -> identity.id.serialize, "name" -> ""))
    }

    "preserve empty lists and include null names in nested addresses" in {
      val value = identity.copy(replyTo = Some(List()), bcc = Some(List(EmailAddress(None, new MailAddress("copy@example.com")))))
      serialize(value, Properties("replyTo", "bcc")) should equal(Json.obj(
        "id" -> identity.id.serialize, "replyTo" -> Json.arr(),
        "bcc" -> Json.arr(Json.obj("name" -> JsNull, "email" -> "copy@example.com"))))
    }

    "continue to accept both absent and null names in creation requests" in {
      Seq("""{"email":"copy@example.com"}""", """{"name":null,"email":"copy@example.com"}""").foreach { address =>
        withClue(s"address=$address: ") {
          val request = IdentitySerializer.deserializeIdentityCreationRequest(
            Json.parse(s"""{"email":"bob@example.com","replyTo":[$address]}""")).get
          request.replyTo.get.head.name should equal(None)
        }
      }
    }
  }
}
