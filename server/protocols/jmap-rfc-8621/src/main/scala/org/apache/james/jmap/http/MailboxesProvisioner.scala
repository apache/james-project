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

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxManager.CreateOption
import org.apache.james.mailbox.exception.{MailboxException, MailboxExistsException}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.mailbox.{DefaultMailboxes, MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class MailboxesProvisioner @Inject() (mailboxManager: MailboxManager,
                                      metricFactory: MetricFactory) {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[MailboxesProvisioner])

  def createMailboxesIfNeeded(session: MailboxSession): SMono[Unit] =
    SMono(metricFactory.decoratePublisherWithTimerMetric("JMAP-RFC-8621-mailboxes-provisioning",
      createDefaultMailboxes(session.getUser)))


  private def createDefaultMailboxes(username: Username): SMono[Unit] = {
    val session: MailboxSession = mailboxManager.createSystemSession(username)

    SFlux.fromIterable(DefaultMailboxes.DEFAULT_MAILBOXES.asScala)
      .map(toMailboxPath(session))
      .filterWhen(mailboxPath => mailboxDoesntExist(mailboxPath, session))
      .concatMap(mailboxPath => createMailbox(mailboxPath, session))
      .`then`
  }

  private def mailboxDoesntExist(mailboxPath: MailboxPath, session: MailboxSession): SMono[Boolean] = {
    try {
      SMono(mailboxManager.mailboxExists(mailboxPath, session))
        .map(exist => !exist)
    } catch {
      case exception: MailboxException => SMono.error(exception)
    }
  }

  private def toMailboxPath(session: MailboxSession): String => MailboxPath =
    (mailbox: String) => MailboxPath.forUser(session.getUser, mailbox)

  private def createMailbox(mailboxPath: MailboxPath, session: MailboxSession): SMono[Unit] = {
    SMono(mailboxManager.createMailboxReactive(mailboxPath, CreateOption.CREATE_SUBSCRIPTION, session))
      .onErrorResume {
        case _: MailboxExistsException => LOGGER.info("Mailbox {} have been created concurrently", mailboxPath)
          SMono.empty
        case e => SMono.error(e)
      }
      .`then`()
  }
}
