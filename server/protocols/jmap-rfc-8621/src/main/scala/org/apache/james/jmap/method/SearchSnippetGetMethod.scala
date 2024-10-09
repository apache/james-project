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

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{CapabilityIdentifier, Invocation, SessionTranslator}
import org.apache.james.jmap.highlight.{SearchSnippetGetRequest, SearchSnippetGetResponse}
import org.apache.james.jmap.json.SearchSnippetSerializer
import org.apache.james.jmap.mail.UnparsedEmailId
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.{AccessibleNamespace, Namespace, PersonalNamespace}
import org.apache.james.mailbox.model.SearchQuery.ConjunctionCriterion
import org.apache.james.mailbox.model.{MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mailbox.searchhighligt.{SearchHighlighter, SearchSnippet}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.util.Try

class SearchSnippetGetMethod @Inject()(serializer: SearchSnippetSerializer,
                                       searchHighlighter: SearchHighlighter,
                                       messageIdFactory: MessageId.Factory,
                                       val metricFactory: MetricFactory,
                                       val sessionSupplier: SessionSupplier,
                                       val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[SearchSnippetGetRequest] {

  override val methodName: MethodName = MethodName("SearchSnippet/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, SearchSnippetGetRequest] = serializer.deserializeSearchSnippetGetRequest(invocation.arguments.value)
    .asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: SearchSnippetGetRequest): Publisher[InvocationWithContext] =
    processRequest(mailboxSession, invocation.invocation, request, capabilities)
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))

  private def processRequest(mailboxSession: MailboxSession,
                             invocation: Invocation,
                             request: SearchSnippetGetRequest,
                             capabilities: Set[CapabilityIdentifier]): SMono[Invocation] =
    getSearchSnippet(request, mailboxSession, capabilities)
      .map(searchSnippetList => SearchSnippetGetResponse.from(request.accountId, searchSnippetList.toList, request.emailIds))
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serialize(response).as[JsObject]),
        methodCallId = invocation.methodCallId))

  private def getSearchSnippet(request: SearchSnippetGetRequest,
                               mailboxSession: MailboxSession,
                               capabilities: Set[CapabilityIdentifier]): SMono[Seq[SearchSnippet]] =
    if (request.emptyRequest) {
      SMono.just(Seq.empty)
    } else {
      SMono.fromCallable(() => request.emailIds.flatMap(asMessageId(_).toOption))
        .filter(messageIds => messageIds.nonEmpty)
        .flatMap(messageIds => buildMultiMailboxesSearchQuery(request, messageIds, capabilities, mailboxSession)
          .fold(e => SMono.error(e),
            multiMailboxesSearchQuery => SFlux(searchHighlighter.highlightSearch(messageIds.asJava, multiMailboxesSearchQuery, mailboxSession))
              .collectSeq()))
        .switchIfEmpty(SMono.just(Seq.empty))
    }

  private def asMessageId(id: UnparsedEmailId): Either[(UnparsedEmailId, IllegalArgumentException), MessageId] =
    Try(messageIdFactory.fromString(id.id.value))
      .toEither
      .left.map(error => (id, new IllegalArgumentException(s"""Can not parse UnparsedEmailId(${id.id.value}) as MessageId""", error)))

  private def buildMultiMailboxesSearchQuery(request: SearchSnippetGetRequest,
                                             messageIds: List[MessageId],
                                             capabilities: Set[CapabilityIdentifier],
                                             session: MailboxSession): Either[UnsupportedOperationException, MultimailboxesSearchQuery] = {
    val messageIdsCriterion = new ConjunctionCriterion(SearchQuery.Conjunction.OR, messageIds.map(SearchQuery.hasMessageId).asJava)

    request.tryFilterAsSearchQuery
      .map(searchQueryBuilder => searchQueryBuilder.andCriteria(messageIdsCriterion).build())
      .map(searchQuery => MultimailboxesSearchQuery.from(searchQuery)
        .inNamespace(queryNamespace(capabilities, session))
        .build())
  }

  private def queryNamespace(capabilities: Set[CapabilityIdentifier], session: MailboxSession): Namespace =
    if (capabilities(CapabilityIdentifier.JAMES_SHARES)) new AccessibleNamespace()
    else new PersonalNamespace(session)
}
