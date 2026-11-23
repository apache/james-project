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
package org.apache.james.jmap.method


import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.{ErrorCode, Invocation}
import org.apache.james.jmap.method.CoreEchoMethod.MAX_ECHOED_SIZE
import org.apache.james.jmap.routes.{IdPaths, JsonSize}
import org.apache.james.mailbox.MailboxSession
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

object CoreEchoMethod {
  val MAX_ECHOED_SIZE: Long = 1024 * 1024
}

class CoreEchoMethod extends Method with IdArguments {
  override val methodName = MethodName("Core/echo")
  /** Arguments are returned unchanged (RFC 8620 §4): result references are resolved, creation references are not. */
  override val idPaths: IdPaths = IdPaths.NONE

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] =
    if (JsonSize.upTo(invocation.invocation.arguments.value, MAX_ECHOED_SIZE) > MAX_ECHOED_SIZE) {
      SMono.just(InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments,
        s"Core/echo arguments are limited to $MAX_ECHOED_SIZE bytes", invocation.invocation.methodCallId), invocation.processingContext))
    } else {
      SMono.just(invocation)
    }

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)
}