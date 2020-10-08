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
import javax.inject.Inject
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{EmailSetSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{DestroyIds, EmailSetRequest, EmailSetResponse}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{Capabilities, Invocation, State}
import org.apache.james.mailbox.model.DeleteResult
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

class EmailSetMethod @Inject()(serializer: EmailSetSerializer,
                               messageIdManager: MessageIdManager,
                               val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier) extends Method {

  override val methodName: MethodName = MethodName("Email/set")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = {
    asEmailSetRequest(invocation.invocation.arguments)
        .flatMap(request => {
          for {
            destroyed <- destroy(request, mailboxSession)
          } yield {
            InvocationWithContext(
              invocation = Invocation(
                methodName = invocation.invocation.methodName,
                arguments = Arguments(serializer.serialize(EmailSetResponse(
                  accountId = request.accountId,
                  newState = State.INSTANCE,
                  destroyed = destroyed))),
                methodCallId = invocation.invocation.methodCallId),
              processingContext = invocation.processingContext
            )
          }
        })
  }

  private def asEmailSetRequest(arguments: Arguments): SMono[EmailSetRequest] = {
    serializer.deserialize(arguments.value) match {
      case JsSuccess(request, _) => SMono.just(request)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
  }

  private def destroy(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[Option[DestroyIds]] =
    emailSetRequest.destroy
      .map(destroyId => deleteMessages(destroyId, mailboxSession))
      .getOrElse(SMono.just(None))

  private def deleteMessages(destroyIds: DestroyIds, mailboxSession: MailboxSession): SMono[Option[DestroyIds]] = {
    SMono.fromCallable[DeleteResult](() => messageIdManager.delete(destroyIds.value.asJava, mailboxSession))
      .subscribeOn(Schedulers.elastic)
      .map(deleteResult => Some(DestroyIds(deleteResult.getDestroyed.asScala.toSeq)))
  }
}
