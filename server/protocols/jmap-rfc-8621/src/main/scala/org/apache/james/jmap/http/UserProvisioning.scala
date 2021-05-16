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

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD
import org.apache.james.user.api.{AlreadyExistInUsersRepositoryException, UsersRepository, UsersRepositoryException}
import reactor.core.scala.publisher.SMono

class UserProvisioning @Inject() (usersRepository: UsersRepository, metricFactory: MetricFactory) {

  def provisionUser(session: MailboxSession): SMono[Unit] =
    if (session != null && !usersRepository.isReadOnly) {
      SMono.fromCallable(() => createAccountIfNeeded(session))
        .`then`
    } else {
      SMono.empty
    }

  private def createAccountIfNeeded(session: MailboxSession): Unit = {
    val timeMetric = metricFactory.timer("JMAP-RFC-8621-user-provisioning")
    try {
      val username = session.getUser
      if (needsAccountCreation(username)) {
        createAccount(username)
      }
    } catch {
      case exception: AlreadyExistInUsersRepositoryException => // Ignore
      case exception: UsersRepositoryException => throw new RuntimeException(exception)
    } finally {
      timeMetric.stopAndPublish
    }
  }

  @throws[UsersRepositoryException]
  private def createAccount(username: Username): Unit = usersRepository.addUser(username, generatePassword)

  @throws[UsersRepositoryException]
  private def needsAccountCreation(username: Username): Boolean = !usersRepository.contains(username)

  private def generatePassword: String = UUID.randomUUID.toString
}
