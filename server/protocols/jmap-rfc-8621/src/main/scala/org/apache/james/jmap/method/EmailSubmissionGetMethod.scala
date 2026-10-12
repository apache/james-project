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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, JmapRfc8621Configuration, SessionTranslator, UuidState}
import org.apache.james.jmap.json.EmailSubmissionSerializer
import org.apache.james.jmap.mail.{EmailSubmissionGetRequest, EmailSubmissionGetResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.SMono

/**
 * EmailSubmission storage is not implemented: no submission is ever returned.
 */
class EmailSubmissionGetMethod @Inject()(val metricFactory: MetricFactory,
                                         val configuration: JmapRfc8621Configuration,
                                         val sessionSupplier: SessionSupplier,
                                         val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[EmailSubmissionGetRequest] {
  override val methodName: MethodName = MethodName("EmailSubmission/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, EMAIL_SUBMISSION)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailSubmissionGetRequest): SMono[InvocationWithContext] =
    SMono.just(InvocationWithContext(
      invocation = request.unknownProperties
        .map(unknownProperties => Invocation.error(ErrorCode.InvalidArguments,
          s"The following properties [${unknownProperties.format()}] do not exist.",
          invocation.invocation.methodCallId))
        .getOrElse(Invocation(
          methodName = methodName,
          arguments = Arguments(EmailSubmissionSerializer.serialize(EmailSubmissionGetResponse(
            accountId = request.accountId,
            state = UuidState.INSTANCE,
            list = List(),
            notFound = request.ids.getOrElse(List())))),
          methodCallId = invocation.invocation.methodCallId)),
      processingContext = invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, EmailSubmissionGetRequest] =
    EmailSubmissionSerializer.deserializeGetRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))
}
