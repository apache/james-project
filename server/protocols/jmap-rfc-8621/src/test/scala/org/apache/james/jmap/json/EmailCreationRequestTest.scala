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

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import org.apache.commons.io.input.CountingInputStream
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.Size.sanitizeSize
import org.apache.james.jmap.api.model.{Upload, UploadId}
import org.apache.james.jmap.json.EmailSetSerializerTest.SERIALIZER
import org.apache.james.jmap.mail.{AttachmentsTooLargeException, BlobId, EmailCreationLimits, EmailCreationRequest, TooManyAttachmentsException}
import org.apache.james.jmap.routes.{Applicable, BlobResolutionResult, BlobResolver, BlobResolvers, UploadedBlob}
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor
import org.apache.james.mailbox.model.ContentType
import org.apache.james.mailbox.{MailboxSession, MailboxSessionUtil}
import org.apache.james.mime4j.dom.{Message, Multipart}
import org.apache.james.mime4j.message.{DefaultMessageBuilder, DefaultMessageWriter}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import reactor.core.scala.publisher.SMono

import scala.util.Try

object EmailCreationRequestTest {
  private val LIMITS: EmailCreationLimits = EmailCreationLimits(maxAttachments = 256, maxSizeAttachments = 20_000_000L)

  /**
   * Resolves "uploads-<n>" to an uploaded blob of n bytes, counting resolutions, content reads and bytes read.
   * When declaredSize is set the blob advertises it instead of its real size.
   */
  class CountingBlobResolver(declaredSize: Option[Long] = None) extends BlobResolver {
    val resolutions: AtomicInteger = new AtomicInteger(0)
    val contentReads: AtomicInteger = new AtomicInteger(0)
    val bytesRead: AtomicInteger = new AtomicInteger(0)

    override def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[BlobResolutionResult] = {
      resolutions.incrementAndGet()
      val size: Int = blobId.value.value.stripPrefix("uploads-").toInt
      val upload = Upload(UploadId.random(), sanitizeSize(declaredSize.getOrElse(size.toLong)), ContentType.of("text/plain"), Instant.now(),
        () => content(size))
      SMono.just(Applicable(SMono.just(UploadedBlob(blobId, upload))))
    }

    private def content(size: Int): InputStream = {
      contentReads.incrementAndGet()
      new CountingInputStream(new ByteArrayInputStream(Array.fill[Byte](size)('a'.toByte))) {
        override def afterRead(n: Int): Unit = {
          super.afterRead(n)
          if (n > 0) {
            bytesRead.addAndGet(n)
          }
        }
      }
    }
  }
}

class EmailCreationRequestTest extends AnyWordSpec with Matchers {
  import EmailCreationRequestTest._

  private def toMessage(json: String): Message = toMessage(json, new BlobResolvers(Set[BlobResolver]()), LIMITS).get

  private def toMessage(json: String, blobResolvers: BlobResolvers, limits: EmailCreationLimits): Try[Message] = {
    val request: EmailCreationRequest = SERIALIZER.deserializeCreationRequest(Json.parse(json)).get
    request.validateRequest.isRight shouldBe true

    Try(request.toMime4JMessage(blobResolvers, new JsoupHtmlTextExtractor(),
        MailboxSessionUtil.create(Username.of("bob@domain.tld")), limits)
      .block())
  }

  private def withAttachments(blobIds: Seq[String]): String =
    s"""{"mailboxIds":{"1":true},"attachments":[${blobIds.map(id => s"""{"blobId":"$id","type":"text/plain"}""").mkString(",")}]}"""

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

    "accept a folded raw header value" in {
      val message = toMessage(
        """{"mailboxIds":{"1":true},"header:X-A:asRaw":" b\r\n c",
          |"replyTo":[{"email":"reply@example.com"}]}""".stripMargin)

      val reparsed = rewrite(message)
      reparsed.getHeader.getField("X-A").getBody shouldBe "b c"
      reparsed.getReplyTo.flatten().get(0).getAddress shouldBe "reply@example.com"
    }
  }

  "toMime4JMessage attachment limits" should {
    "accept attachments whose total size equals the limit" in {
      val resolver = new CountingBlobResolver()

      val message = toMessage(withAttachments(Seq.fill(5)("uploads-20")), new BlobResolvers(Set[BlobResolver](resolver)),
        LIMITS.copy(maxSizeAttachments = 100L))

      message.get.getBody.asInstanceOf[Multipart].getBodyParts.size() shouldBe 6
      resolver.contentReads.get() shouldBe 5
    }

    "reject a blob referenced many times once the total size exceeds the limit, without reading the remaining references" in {
      val resolver = new CountingBlobResolver()

      val result = toMessage(withAttachments(Seq.fill(10)("uploads-40")), new BlobResolvers(Set[BlobResolver](resolver)),
        LIMITS.copy(maxSizeAttachments = 100L))

      result.failed.get shouldBe AttachmentsTooLargeException(100L)
      resolver.contentReads.get() shouldBe 2
      resolver.resolutions.get() shouldBe 3
    }

    "reject a single attachment bigger than the limit without reading it" in {
      val resolver = new CountingBlobResolver()

      val result = toMessage(withAttachments(Seq("uploads-101")), new BlobResolvers(Set[BlobResolver](resolver)),
        LIMITS.copy(maxSizeAttachments = 100L))

      result.failed.get shouldBe AttachmentsTooLargeException(100L)
      resolver.contentReads.get() shouldBe 0
    }

    "read at most the limit plus one byte of a blob under-advertising its size" in {
      val resolver = new CountingBlobResolver(declaredSize = Some(1L))

      val result = toMessage(withAttachments(Seq("uploads-10000")), new BlobResolvers(Set[BlobResolver](resolver)),
        LIMITS.copy(maxSizeAttachments = 100L))

      result.failed.get shouldBe AttachmentsTooLargeException(100L)
      resolver.bytesRead.get() should be <= 101
    }

    "count a blob based text body in the total size" in {
      val resolver = new CountingBlobResolver()

      val result = toMessage(
        """{"mailboxIds":{"1":true},
          |"textBody":[{"blobId":"uploads-60","type":"text/plain"}],
          |"attachments":[{"blobId":"uploads-60","type":"text/plain"}]}""".stripMargin,
        new BlobResolvers(Set[BlobResolver](resolver)),
        LIMITS.copy(maxSizeAttachments = 100L))

      result.failed.get shouldBe AttachmentsTooLargeException(100L)
      resolver.contentReads.get() shouldBe 1
    }

    "accept as many attachments as the count limit" in {
      val resolver = new CountingBlobResolver()

      val message = toMessage(withAttachments(Seq.fill(256)("uploads-1")), new BlobResolvers(Set[BlobResolver](resolver)), LIMITS)

      message.get.getBody.asInstanceOf[Multipart].getBodyParts.size() shouldBe 257
    }

    "reject more attachments than the count limit without resolving any blob" in {
      val resolver = new CountingBlobResolver()

      val result = toMessage(withAttachments(Seq.fill(257)("uploads-1")), new BlobResolvers(Set[BlobResolver](resolver)), LIMITS)

      result.failed.get shouldBe TooManyAttachmentsException(257, 256)
      resolver.resolutions.get() shouldBe 0
      resolver.contentReads.get() shouldBe 0
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

    "reject a raw header value ending with CR LF on a body part" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"bodyValues":{"a":{"value":"hi"}},
          |"textBody":[{"partId":"a","type":"text/plain","header:X-A":"b\r\n"}]}""".stripMargin)).isError shouldBe true
    }

    "reject a raw header value ending with CR LF" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asRaw":"b\r\n"}""")).isError shouldBe true
    }

    "reject a text header value ending with CR LF" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asText":"b\r\n"}""")).isError shouldBe true
    }

    "reject a header value holding CR LF not followed by white space" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asText":"b\r\nBcc: x@example.com"}""")).isError shouldBe true
    }

    "reject a header value holding a lone LF" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asRaw":"b\n c"}""")).isError shouldBe true
    }

    "reject a header value holding a lone CR" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asRaw":"b\r c"}""")).isError shouldBe true
    }

    "reject a header value holding a white space only continuation line" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asRaw":"b\r\n \r\n c"}""")).isError shouldBe true
    }

    "reject an :all header value ending with CR LF" in {
      SERIALIZER.deserializeCreationRequest(Json.parse(
        """{"mailboxIds":{"1":true},"header:X-A:asText:all":["a", "b\r\n"]}""")).isError shouldBe true
    }
  }
}
