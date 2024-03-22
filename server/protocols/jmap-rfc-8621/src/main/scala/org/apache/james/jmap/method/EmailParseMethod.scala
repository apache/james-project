/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import java.io.InputStream

import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation._
import org.apache.james.jmap.core.{Invocation, JmapRfc8621Configuration, SessionTranslator}
import org.apache.james.jmap.json.EmailGetSerializer
import org.apache.james.jmap.mail.{BlobId, BlobUnParsableException, Email, EmailBody, EmailBodyMetadata, EmailBodyPart, EmailFullViewFactory, EmailHeaders, EmailParseMetadata, EmailParseRequest, EmailParseResponse, EmailParseResults, EmailParseView, HasAttachment}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.html.HtmlTextExtractor
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

class EmailParseMethod @Inject()(val blobResolvers: BlobResolvers,
                                 val configuration: JmapRfc8621Configuration,
                                 val zoneIdProvider: ZoneIdProvider,
                                 val metricFactory: MetricFactory,
                                 val sessionSupplier: SessionSupplier,
                                 val sessionTranslator: SessionTranslator,
                                 val previewFactory: Preview.Factory,
                                 val htmlTextExtractor: HtmlTextExtractor) extends MethodRequiringAccountId[EmailParseRequest] {
  override val methodName: MethodName = MethodName("Email/parse")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MAIL, JMAP_CORE)

  def doProcess(capabilities: Set[CapabilityIdentifier],
                invocation: InvocationWithContext,
                mailboxSession: MailboxSession,
                request: EmailParseRequest): SMono[InvocationWithContext] =
    computeResponseInvocation(request, invocation.invocation, mailboxSession)
      .map(InvocationWithContext(_, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, EmailParseRequest] =
    EmailGetSerializer.deserializeEmailParseRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  def computeResponseInvocation(request: EmailParseRequest,
                                invocation: Invocation,
                                mailboxSession: MailboxSession): SMono[Invocation] =
    Email.validateProperties(request.properties)
      .flatMap(properties => Email.validateBodyProperties(request.bodyProperties).map((properties, _)))
      .fold(
        e => SMono.error(e), {
          case (properties, bodyProperties) => computeResponse(request, mailboxSession)
            .map(response => Invocation(
              methodName = methodName,
              arguments = Arguments(EmailGetSerializer.serializeEmailParseResponse(response, properties, bodyProperties).as[JsObject]),
              methodCallId = invocation.methodCallId))
        })

  private def computeResponse(request: EmailParseRequest,
                              mailboxSession: MailboxSession): SMono[EmailParseResponse] = {
    val validations: Seq[Either[EmailParseResults, BlobId]] = request.blobIds.value
      .map(id => BlobId.of(id)
        .toEither
        .left
        .map(_ => EmailParseResults.notFound(id)))
    val parsedIds: Seq[BlobId] = validations.flatMap(_.toOption)
    val invalid: Seq[EmailParseResults] = validations.map(_.left).flatMap(_.toOption)

    val concurrency = 2
    val parsed: SFlux[EmailParseResults] = SFlux.fromIterable(parsedIds)
      .flatMap(blobId => toParseResults(request, blobId, mailboxSession), concurrency, concurrency)

    SFlux.merge(Seq(parsed, SFlux.fromIterable(invalid)))
      .reduce(EmailParseResults.empty())(EmailParseResults.merge)
      .map(_.asResponse(request.accountId))
  }

  private def toParseResults(request: EmailParseRequest, blobId: BlobId, mailboxSession: MailboxSession): SMono[EmailParseResults] =
    blobResolvers.resolve(blobId, mailboxSession)
      .flatMap(blob => parse(request, blob.blobId, blob.content, blob.size.get))
      .onErrorResume {
        case e: BlobNotFoundException => SMono.just(EmailParseResults.notFound(e.blobId))
        case e: BlobUnParsableException => SMono.just(EmailParseResults.notParsed(e.blobId))
        case _ => SMono.just(EmailParseResults.notParsed(blobId))
      }

  private def parse(request: EmailParseRequest, blobId: BlobId, blobContent: => InputStream, size: Size): SMono[EmailParseResults] = {
    val result: SMono[EmailParseView] = for {
      mime4JMessage <- SMono.fromTry(Email.parseStreamAsMime4JMessage(blobContent))
      bodyStructure <- SMono.fromTry(EmailBodyPart.of(request.bodyProperties, zoneIdProvider.get(), blobId, mime4JMessage))
      bodyValues <- SMono.fromTry(EmailFullViewFactory.extractBodyValuesForParse(htmlTextExtractor)(bodyStructure, request))
      preview <- SMono.fromTry(Try(previewFactory.fromMime4JMessage(mime4JMessage)))
    } yield {
      EmailParseView(
        metadata = EmailParseMetadata(blobId = blobId, size = size),
        header = EmailHeaders.from(zoneIdProvider.get())(mime4JMessage),
        specificHeaders = EmailHeaders.extractSpecificHeaders(request.properties)(zoneIdProvider.get(), mime4JMessage.getHeader),
        bodyMetadata = EmailBodyMetadata(hasAttachment = HasAttachment(bodyStructure.attachments.nonEmpty),
          preview = preview),
        body = EmailBody(
          bodyStructure = bodyStructure,
          textBody = bodyStructure.textBody,
          htmlBody = bodyStructure.htmlBody,
          attachments = bodyStructure.attachments,
          bodyValues = bodyValues))
    }
    result.map(EmailParseResults.parsed(blobId, _))
  }
}