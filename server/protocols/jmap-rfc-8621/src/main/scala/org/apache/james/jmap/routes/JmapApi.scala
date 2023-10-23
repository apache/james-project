/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.routes

import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.{CapabilityFactory, ErrorCode, Invocation, JmapRfc8621Configuration, MissingCapabilityException, RequestObject, ResponseObject}
import org.apache.james.jmap.method.{InvocationWithContext, Method}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object JMAPApi {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[JMAPApi])
}

class JMAPApi (methods: Set[Method], defaultCapabilities: Set[CapabilityIdentifier], configuration: JmapRfc8621Configuration) {

  private val methodsByName: Map[MethodName, Method] = methods.map(method => method.methodName -> method).toMap

  @Inject
  def this(javaMethods: java.util.Set[Method], supportedCapabilities: java.util.Set[CapabilityFactory], configuration: JmapRfc8621Configuration) = {
    this(javaMethods.asScala.toSet, supportedCapabilities.asScala.map(x => x.id()).toSet, configuration)
  }

  def process(requestObject: RequestObject,
              mailboxSession: MailboxSession): SMono[ResponseObject] = {
    val processingContext: ProcessingContext = ProcessingContext(Map.empty, Map.empty)
    val unsupportedCapabilities = requestObject.using -- defaultCapabilities
    val capabilities: Set[CapabilityIdentifier] = requestObject.using

    if (unsupportedCapabilities.nonEmpty) {
      SMono.error(UnsupportedCapabilitiesException(unsupportedCapabilities))
    } else {
      processSequentiallyAndUpdateContext(requestObject, mailboxSession, processingContext, capabilities)
        .map(invocations => ResponseObject(ResponseObject.SESSION_STATE, invocations.map(_.invocation)))
    }
  }

  private def processSequentiallyAndUpdateContext(requestObject: RequestObject, mailboxSession: MailboxSession, processingContext: ProcessingContext, capabilities: Set[CapabilityIdentifier]): SMono[Seq[(InvocationWithContext)]] =
    SFlux.fromIterable(requestObject.methodCalls)
      .fold(List[SFlux[InvocationWithContext]]())((acc, elem) => {
        val lastProcessingContext: SMono[ProcessingContext] = acc.headOption
          .map(last => SMono.fromPublisher(Flux.from(last.map(_.processingContext)).last()))
          .getOrElse(SMono.just(processingContext))
        val invocation: SFlux[InvocationWithContext] = lastProcessingContext.flatMapMany(context => process(capabilities, mailboxSession, InvocationWithContext(elem, context)))
        invocation.cache() :: acc
      })
      .map(_.reverse)
      .flatMap(list => SFlux.fromIterable(list)
        .concatMap(e => e)
        .collectSeq())

  private def process(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession, invocation: InvocationWithContext) : SFlux[InvocationWithContext] =
    SFlux.fromPublisher(
      invocation.processingContext.resolveBackReferences(invocation.invocation) match {
        case Left(e) => SFlux.just[InvocationWithContext](InvocationWithContext(Invocation.error(
          errorCode = ErrorCode.InvalidResultReference,
          description = s"Failed resolving back-reference: ${e.message}",
          methodCallId = invocation.invocation.methodCallId), invocation.processingContext))
        case Right(resolvedInvocation) => processMethodWithMatchName(capabilities, InvocationWithContext(resolvedInvocation, invocation.processingContext), mailboxSession)
          .map(_.recordInvocation)
      })

  private def processMethodWithMatchName(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): SFlux[InvocationWithContext] =
    methodsByName.get(invocation.invocation.methodName)
      .map(method => validateCapabilities(capabilities, method.requiredCapabilities)
        .fold(e => SFlux.just(InvocationWithContext(Invocation.error(ErrorCode.UnknownMethod, e.description, invocation.invocation.methodCallId), invocation.processingContext)),
          _ => SFlux.fromPublisher(method.process(capabilities, invocation, mailboxSession))))
      .getOrElse(SFlux.just(InvocationWithContext(Invocation.error(ErrorCode.UnknownMethod, invocation.invocation.methodCallId), invocation.processingContext)))
      .onErrorResume(throwable => SMono.just(InvocationWithContext(Invocation.error(ErrorCode.ServerFail, throwable.getMessage, invocation.invocation.methodCallId), invocation.processingContext)))

  private def validateCapabilities(capabilities: Set[CapabilityIdentifier], requiredCapabilities: Set[CapabilityIdentifier]): Either[MissingCapabilityException, Unit] = {
    val missingCapabilities = (requiredCapabilities -- capabilities) ++ configuration.disabledCapabilities.intersect(capabilities)
    if (missingCapabilities.nonEmpty) {
      Left(MissingCapabilityException(s"Missing capability(ies): ${missingCapabilities.mkString(", ")}"))
    } else {
      Right((): Unit)
    }
  }
}
