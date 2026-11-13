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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.json.EmailSetSerializerTest.SERIALIZER
import org.apache.james.jmap.mail.EmailCreationRequest
import org.apache.james.jmap.routes.{BlobResolver, BlobResolvers}
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor
import org.apache.james.mailbox.MailboxSessionUtil
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.{DefaultMessageBuilder, DefaultMessageWriter}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class EmailCreationRequestTest extends AnyWordSpec with Matchers {
  private def toMessage(json: String): Message = {
    val request: EmailCreationRequest = SERIALIZER.deserializeCreationRequest(Json.parse(json)).get
    request.validateRequest.isRight shouldBe true

    request.toMime4JMessage(new BlobResolvers(Set[BlobResolver]()), new JsoupHtmlTextExtractor(),
        MailboxSessionUtil.create(Username.of("bob@domain.tld")))
      .block()
  }

  private def rewrite(message: Message): Message =
    new DefaultMessageBuilder().parseMessage(new ByteArrayInputStream(DefaultMessageWriter.asBytes(message)))

  "toMime4JMessage" should {
    "accept an empty raw header value" in {
      val message = toMessage("""{"mailboxIds":{"1":true},"header:X-Empty:asRaw":""}""")

      message.getHeader.getField("X-Empty").getBody shouldBe ""
    }

    "drop the white space following the colon of a raw value" in {
      val message = toMessage("""{"mailboxIds":{"1":true},"header:X-Foo:asRaw":" abc"}""")

      message.getHeader.getField("X-Foo").getBody shouldBe "abc"
    }

    "keep the first character of a raw value without leading space" in {
      val message = toMessage("""{"mailboxIds":{"1":true},"header:X-Foo:asRaw":"abc"}""")

      message.getHeader.getField("X-Foo").getBody shouldBe "abc"
    }

    "accept an empty raw header value on a body part" in {
      val message = toMessage(
        """{"mailboxIds":{"1":true},
          |"bodyValues":{"a":{"value":"hello"}},
          |"textBody":[{"partId":"a","type":"text/plain","header:X-Empty:asRaw":""}]}""".stripMargin)

      message should not be null
    }

    "save a draft whose address is only '@'" in {
      val message = toMessage("""{"mailboxIds":{"1":true},"keywords":{"$draft":true},"to":[{"email":"@"}]}""")

      message.getTo should not be null
    }

    "save a draft whose address has several '@'" in {
      val message = toMessage("""{"mailboxIds":{"1":true},"keywords":{"$draft":true},"to":[{"email":"a@@b"}]}""")

      message.getTo.flatten().get(0).getDomain shouldBe "b"
    }

    "keep a quoted local part containing '@'" in {
      val message = toMessage("""{"mailboxIds":{"1":true},"from":[{"email":"\"a@b\"@d"}]}""")

      message.getFrom.get(0).getDomain shouldBe "d"
      new MailAddress(message.getFrom.get(0).getAddress) shouldBe new MailAddress("\"a@b\"@d")
    }

    "not end the header block on an address ending with CR LF" in {
      val message = toMessage(
        """{"mailboxIds":{"1":true},"to":[{"email":"to@example.com\r\n"}],
          |"replyTo":[{"email":"reply@example.com"}]}""".stripMargin)

      val reparsed = rewrite(message)
      reparsed.getTo.flatten().get(0).getAddress shouldBe "to@example.com"
      reparsed.getReplyTo.flatten().get(0).getAddress shouldBe "reply@example.com"
    }

    "not end the header block on a draft address ending with CR LF" in {
      val message = toMessage(
        """{"mailboxIds":{"1":true},"keywords":{"$draft":true},"bcc":[{"email":"bcc@example.com\r\n"}],
          |"replyTo":[{"email":"reply@example.com"}]}""".stripMargin)

      val reparsed = rewrite(message)
      reparsed.getBcc.flatten().get(0).getAddress shouldBe "bcc@example.com"
      reparsed.getReplyTo.flatten().get(0).getAddress shouldBe "reply@example.com"
    }
  }

  "deserializeCreationRequest" should {
    "reject an address holding CR LF" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"to":[{"email":"to@exa\r\nBcc: x@example.com"}]}""")).isError shouldBe true
    }

    "reject a draft address holding CR LF" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"keywords":{"$draft":true},"to":[{"email":"to\r\n@example.com"}]}""")).isError shouldBe true
    }

    "reject a draft address holding a control character" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"keywords":{"$draft":true},"to":[{"email":"to@example.com\u007f"}]}""")).isError shouldBe true
    }
  }
}
