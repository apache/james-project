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

import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime

import eu.timepit.refined.auto._
import org.apache.james.jmap.api.model.{EmailerName, Preview}
import org.apache.james.jmap.core.{AccountId, Properties, UTCDate, UuidState}
import org.apache.james.jmap.mail._
import org.apache.james.mailbox.model.TestMessageId
import org.apache.james.mime4j.dom.Message
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, JsValue, Json}

class EmailGetSerializerTest extends AnyWordSpec with Matchers {
  private val accountId = AccountId(Fixture.id)
  private val blobId = BlobId.of("blob").toOption.get
  private val message = Message.Builder.of.setBody("body", StandardCharsets.UTF_8).build
  private val headers = EmailHeaders(List(), MessageIdsHeaderValue(None), MessageIdsHeaderValue(None),
    MessageIdsHeaderValue(None), None, None, None, None, None, None, None, None)
  private val metadata = EmailMetadata(TestMessageId.of(1), blobId, ThreadId("thread"), Keywords(Set()),
    MailboxIds(List()), 4L, UTCDate(ZonedDateTime.parse("2020-01-01T00:00:00Z")))
  private val bodyMetadata = EmailBodyMetadata(HasAttachment(false), Preview.from("body"))
  private val leaf = EmailBodyPart(PartId(1), Some(blobId), List(), 4L, None, Type("text/plain"),
    None, None, None, None, None, None, message, Map())
  private val root = leaf.copy(blobId = None, `type` = Type("multipart/mixed"), subParts = Some(List(leaf)))
  private val body = EmailBody(root, List(leaf), List(leaf), List(leaf), Map())
  private val nullableHeaders = Seq("from", "sender", "to", "cc", "bcc", "replyTo", "subject", "sentAt")
  private val nullableBodyProperties = Seq("name", "charset", "disposition", "cid", "language", "location")

  private def serialize(view: EmailView, properties: Properties, bodyProperties: Properties = EmailBodyPart.defaultProperties): JsValue =
    (EmailGetSerializer.serialize(EmailGetResponse(accountId, UuidState.INSTANCE, List(view), EmailNotFound(Set())),
      properties, bodyProperties) \ "list")(0)

  "Email/get serialization" should {
    "include missing headers as null across all header-bearing views" in {
      val views = List(
        EmailHeaderView(metadata, headers, Map()),
        EmailFastView(metadata, headers, bodyMetadata, Map()),
        EmailFastViewWithAttachments(metadata, headers, AttachmentsMetadata(List()), bodyMetadata, Map()),
        EmailFullView(metadata, headers, body, bodyMetadata, Map()))
      views.foreach { view =>
        withClue(s"view=${view.getClass.getSimpleName}: ") {
          serialize(view, Properties.toProperties(nullableHeaders.toSet)) should equal(
            JsObject(nullableHeaders.map(_ -> JsNull)))
          serialize(view, Properties("id")) should equal(Json.obj("id" -> metadata.id.serialize()))
        }
      }
    }

    "preserve empty values and serialize nameless addresses" in {
      val header = headers.copy(subject = Some(Subject("")), to = Some(UncheckedAddressesHeaderValue(List())),
        from = Some(UncheckedAddressesHeaderValue(List(UncheckedEmailAddress(None, UncheckedEmail("bob@example.com")),
          UncheckedEmailAddress(Some(EmailerName("Bob")), UncheckedEmail("named@example.com"))))))
      serialize(EmailHeaderView(metadata, header, Map()), Properties("subject", "to", "from")) should equal(
        Json.parse("""{"subject":"", "to":[], "from":[{"name":null,"email":"bob@example.com"},
          {"name":"Bob","email":"named@example.com"}]}"""))
    }

    "include nullable body properties and filter nested parts" in {
      val view = EmailFullView(metadata, headers, body, bodyMetadata, Map())
      val properties = Properties("bodyStructure", "textBody", "htmlBody", "attachments")
      val result = serialize(view, properties, EmailBodyPart.allowedProperties)
      (result \ "bodyStructure" \ "blobId").toOption should equal(Some(JsNull))
      val parts = Seq("bodyStructure.subParts[0]" -> (result \ "bodyStructure" \ "subParts")(0)) ++
        Seq("textBody", "htmlBody", "attachments").map(p => s"$p[0]" -> (result \ p)(0))
      parts.foreach { case (path, part) =>
        withClue(s"part=$path: ") {
          nullableBodyProperties.foreach { property =>
            withClue(s"property=$property: ") {
              (part \ property).toOption should equal(Some(JsNull))
            }
          }
          withClue("property=blobId: ") {
            (part \ "blobId").toOption should equal(Some(JsString("blob")))
          }
          withClue("property=subParts: ") {
            (part \ "subParts").toOption should equal(None)
          }
        }
      }
      val filtered = serialize(view, properties, Properties("name", "subParts"))
      filtered should equal(Json.parse("""{
        "bodyStructure":{"name":null,"subParts":[{"name":null}]},
        "textBody":[{"name":null}], "htmlBody":[{"name":null}], "attachments":[{"name":null}]}"""))
      serialize(view, properties, Properties("type")) should equal(Json.parse("""{
        "bodyStructure":{"type":"multipart/mixed"}, "textBody":[{"type":"text/plain"}],
        "htmlBody":[{"type":"text/plain"}], "attachments":[{"type":"text/plain"}]}"""))
    }

    "preserve present and empty body values" in {
      val part = leaf.copy(name = Some(Name.of("file.txt")), charset = Some(Charset("UTF-8")),
        disposition = Some(Disposition("inline")), language = Some(Languages(List())), location = Some(Location("")))
      val view = EmailFullView(metadata, headers, body.copy(textBody = List(part)), bodyMetadata, Map())
      val result = (serialize(view, Properties("textBody")) \ "textBody")(0)
      (result \ "name").as[String] should equal("file.txt")
      (result \ "charset").as[String] should equal("UTF-8")
      (result \ "disposition").as[String] should equal("inline")
      (result \ "language").as[JsArray] should equal(Json.arr())
      (result \ "location").as[String] should equal("")
    }

    "apply the same null and filtering rules to Email/parse" in {
      val view = EmailParseView(EmailParseMetadata(blobId, 4L), headers, body, bodyMetadata, Map())
      val response = EmailParseResponse(accountId, Map(blobId -> view), None, None)
      val result = EmailGetSerializer.serializeEmailParseResponse(response,
        Properties("subject", "bodyStructure"), Properties("name", "subParts"))
      (result \ "parsed" \ "blob").as[JsObject] should equal(Json.parse("""{
        "subject":null, "bodyStructure":{"name":null,"subParts":[{"name":null}]}}"""))
      (result \ "notFound").toOption should equal(None)
    }
  }
}
