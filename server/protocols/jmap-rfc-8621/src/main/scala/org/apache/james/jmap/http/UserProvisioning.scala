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

package org.apache.james.jmap.http

import java.util.UUID

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.JMAPConfiguration
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.user.api.{AlreadyExistInUsersRepositoryException, UsersRepository}
import org.apache.james.util.ReactorUtils
import reactor.core.scala.publisher.SMono

class UserProvisioning @Inject() (usersRepository: UsersRepository, metricFactory: MetricFactory, jmapConfiguration: JMAPConfiguration = JMAPConfiguration.DEFAULT) {
  def provisionUser(session: MailboxSession): SMono[Unit] =
    if (session != null && !usersRepository.isReadOnly && jmapConfiguration.isUserProvisioningEnabled) {
      createAccountIfNeeded(session)
    } else {
      SMono.empty
    }

  private def createAccountIfNeeded(session: MailboxSession): SMono[Unit] =
    SMono(metricFactory.decoratePublisherWithTimerMetric("JMAP-RFC-8621-user-provisioning",
      needsAccountCreation(session.getUser)
        .filter(b => b)
        .flatMap(_ => createAccount(session.getUser))
        .onErrorResume {
          case _: AlreadyExistInUsersRepositoryException => SMono.empty[Unit]
          case e => SMono.error[Unit](e)
        }))

  private def createAccount(username: Username): SMono[Unit] =
    SMono.fromCallable(() => usersRepository.addUser(username, generatePassword))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def needsAccountCreation(username: Username): SMono[Boolean] =
    SMono(usersRepository.containsReactive(username)).map(b => !b)

  private def generatePassword: String = UUID.randomUUID.toString
}
