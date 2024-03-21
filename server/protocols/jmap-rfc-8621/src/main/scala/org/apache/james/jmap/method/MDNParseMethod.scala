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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL, JMAP_MDN}
import org.apache.james.jmap.core.Invocation._
import org.apache.james.jmap.core.{Invocation, JmapRfc8621Configuration, SessionTranslator}
import org.apache.james.jmap.json.MDNSerializer
import org.apache.james.jmap.mail.{BlobId, BlobUnParsableException, MDNParseRequest, MDNParseResponse, MDNParseResults, MDNParsed}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.model.{MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.mdn.MDN
import org.apache.james.mdn.fields.OriginalMessageId
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageBuilder
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._
import scala.util.{Try, Using}

class MDNParseMethod @Inject()(serializer: MDNSerializer,
                               val configuration: JmapRfc8621Configuration,
                               val blobResolvers: BlobResolvers,
                               val metricFactory: MetricFactory,
                               val mdnEmailIdResolver: MDNEmailIdResolver,
                               val sessionSupplier: SessionSupplier,
                               val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[MDNParseRequest] {
  override val methodName: MethodName = MethodName("MDN/parse")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MDN, JMAP_MAIL, JMAP_CORE)

  def doProcess(capabilities: Set[CapabilityIdentifier],
                invocation: InvocationWithContext,
                mailboxSession: MailboxSession,
                request: MDNParseRequest): SMono[InvocationWithContext] =
    computeResponseInvocation(request, invocation.invocation, mailboxSession)
      .map(InvocationWithContext(_, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, MDNParseRequest] =
    serializer.deserializeMDNParseRequest(invocation.arguments.value)
      .asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  def computeResponseInvocation(request: MDNParseRequest,
                                invocation: Invocation,
                                mailboxSession: MailboxSession): SMono[Invocation] =
    computeResponse(request, mailboxSession)
      .map(response => Invocation(
        methodName,
        Arguments(serializer.serializeMDNParseResponse(response).as[JsObject]),
        invocation.methodCallId))

  private def computeResponse(request: MDNParseRequest,
                              mailboxSession: MailboxSession): SMono[MDNParseResponse] = {
    val validations: Seq[Either[MDNParseResults, BlobId]] = request.blobIds.value
      .map(id => BlobId.of(id)
        .toEither
        .left
        .map(_ => MDNParseResults.notFound(id)))
    val parsedIds: Seq[BlobId] = validations.flatMap(_.toOption)
    val invalid: Seq[MDNParseResults] = validations.map(_.left).flatMap(_.toOption)

    val concurrency = 2
    val parsed: SFlux[MDNParseResults] = SFlux.fromIterable(parsedIds)
      .flatMap(blobId => toParseResults(blobId, mailboxSession), concurrency, concurrency)

    SFlux.merge(Seq(parsed, SFlux.fromIterable(invalid)))
      .reduce(MDNParseResults.empty())(MDNParseResults.merge)
      .map(_.asResponse(request.accountId))
  }

  private def toParseResults(blobId: BlobId, mailboxSession: MailboxSession): SMono[MDNParseResults] =
    blobResolvers.resolve(blobId, mailboxSession)
      .flatMap(blob => Using(blob.content) {
        parse(blob.blobId, _)
      }.fold(e => SMono.error[(MDN, Message)](e), result => result))
      .flatMap {
        case (mdn, message) => buildMDNParseResults(blobId, mdn, message, mailboxSession)
      }
      .onErrorResume {
        case e: BlobNotFoundException => SMono.just(MDNParseResults.notFound(e.blobId))
        case e: BlobUnParsableException => SMono.just(MDNParseResults.notParse(e.blobId))
        case _ => SMono.just(MDNParseResults.notParse(blobId))
      }

  private def buildMDNParseResults(blobId: BlobId, mdn: MDN, message: Message, session: MailboxSession): SMono[MDNParseResults] =
    mdnEmailIdResolver.resolveForEmailId(mdn.getReport.getOriginalMessageIdField.toScala, session)
      .map(messageId => MDNParsed.fromMDN(mdn, message, messageId))
      .map(mdnParsed => MDNParseResults.parse(blobId, mdnParsed))

  private def parse(blobId: BlobId, blobContent: InputStream): SMono[(MDN, Message)] = {
    val maybeMdn: Try[(MDN, Message)] = for {
      message <- Try(new DefaultMessageBuilder().parseMessage(blobContent))
      mdn <- Try(MDN.parse(message))
    } yield {
      (mdn, message)
    }
    maybeMdn.fold(_ => SMono.error(BlobUnParsableException(blobId)), result => SMono.just(result))
  }
}

object MDNEmailIdResolver {
  val NUMBER_OF_ORIGINAL_MESSAGE_ID_VALID: Int = 1
}

case class MDNEmailIdResolver @Inject()(mailboxManager: MailboxManager) {
  import MDNEmailIdResolver.NUMBER_OF_ORIGINAL_MESSAGE_ID_VALID

  def resolveForEmailId(originalMessageId: Option[OriginalMessageId], session: MailboxSession): SMono[Option[MessageId]] =
    originalMessageId.map(originalMsg => {
      val searchByRFC822MessageId: MultimailboxesSearchQuery = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.mimeMessageID(originalMsg.getOriginalMessageId))).build
      SFlux.fromPublisher(mailboxManager.search(searchByRFC822MessageId, session, NUMBER_OF_ORIGINAL_MESSAGE_ID_VALID + 1)).collectSeq().map {
        case Seq(first) => Some(first)
        case _ => None
      }
    }).getOrElse(SMono.just(None))
}
