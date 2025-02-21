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

import com.google.common.collect.ImmutableMap
import jakarta.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.{MailboxGet, MailboxSetError, MailboxSetRequest, RemoveEmailsOnDestroy, UnparsedMailboxId}
import org.apache.james.jmap.method.MailboxSetDeletePerformer.{MailboxDeletionFailure, MailboxDeletionResult, MailboxDeletionResults, MailboxDeletionSuccess}
import org.apache.james.jmap.method.MailboxSetMethod.assertCapabilityIfSharedMailbox
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{FetchGroup, MailboxId, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager, Role, SubscriptionManager}
import org.apache.james.util.{AuditTrail, ReactorUtils}
import org.slf4j.LoggerFactory
import reactor.core.publisher.SynchronousSink
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._

object MailboxSetDeletePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[MailboxSetDeletePerformer])
  sealed trait MailboxDeletionResult
  case class MailboxDeletionSuccess(mailboxId: MailboxId) extends MailboxDeletionResult
  case class MailboxDeletionFailure(mailboxId: UnparsedMailboxId, exception: Throwable) extends MailboxDeletionResult {
    def asMailboxSetError: SetError = exception match {
      case e: MailboxNotFoundException =>
        LOGGER.info("Attempt to delete a non existing mailbox: {}", e.getMessage)
        SetError.notFound(SetErrorDescription(e.getMessage))
      case e: MailboxHasMailException =>
        LOGGER.info("Attempt to delete a mailbox with mails")
        MailboxSetError.mailboxHasEmail(SetErrorDescription(s"${e.mailboxId.serialize} is not empty"))
      case e: MailboxHasChildException =>
        LOGGER.info("Attempt to delete a mailbox with children")
        MailboxSetError.mailboxHasChild(SetErrorDescription(s"${e.mailboxId.serialize} has child mailboxes"))
      case e: SystemMailboxChangeException =>
        LOGGER.info("Attempt to delete a system folder")
        SetError.invalidArguments(SetErrorDescription("System mailboxes cannot be destroyed"))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in Mailbox/set delete", e)
        SetError.invalidArguments(SetErrorDescription(s"${mailboxId.id} is not a mailboxId: ${e.getMessage}"))
      case e: InsufficientRightsException =>
        LOGGER.info("Attempt to delete a mailbox without sufficient rights")
        SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e =>
        LOGGER.error("Failed to delete mailbox", e)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class MailboxDeletionResults(results: Seq[MailboxDeletionResult]) {
    def destroyed: Seq[MailboxId] =
      results.flatMap(result => result match {
        case success: MailboxDeletionSuccess => Some(success)
        case _ => None
      }).map(_.mailboxId)

    def retrieveErrors: Map[UnparsedMailboxId, SetError] =
      results.flatMap(result => result match {
        case failure: MailboxDeletionFailure => Some(failure.mailboxId, failure.asMailboxSetError)
        case _ => None
      })
        .toMap
  }
}

class MailboxSetDeletePerformer @Inject()(mailboxManager: MailboxManager,
                                          subscriptionManager: SubscriptionManager,
                                          mailboxIdFactory: MailboxId.Factory) {

  def deleteMailboxes(mailboxSession: MailboxSession, mailboxSetRequest: MailboxSetRequest, supportSharedMailbox: Boolean): SMono[MailboxDeletionResults] =
    SFlux.fromIterable(mailboxSetRequest.destroy.getOrElse(Seq()).toSet)
      .flatMap(id => delete(mailboxSession, id, mailboxSetRequest.onDestroyRemoveEmails.getOrElse(RemoveEmailsOnDestroy(false)), supportSharedMailbox)
        .onErrorRecover(e => MailboxDeletionFailure(id, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(MailboxDeletionResults)
      .doOnSuccess(auditTrail(mailboxSession, _))

  private def delete(mailboxSession: MailboxSession, id: UnparsedMailboxId, onDestroy: RemoveEmailsOnDestroy, supportSharedMailbox: Boolean): SMono[MailboxDeletionResult] =
    MailboxGet.parse(mailboxIdFactory)(id)
      .fold(e => SMono.error(e),
        id => doDelete(mailboxSession, id, onDestroy, supportSharedMailbox)
          .`then`(SMono.just[MailboxDeletionResult](MailboxDeletionSuccess(id))))

  private def doDelete(mailboxSession: MailboxSession, id: MailboxId, onDestroy: RemoveEmailsOnDestroy, supportSharedMailbox: Boolean): SMono[Unit] =
    SMono(mailboxManager.getMailboxReactive(id, mailboxSession))
      .filterWhen(assertCapabilityIfSharedMailbox(mailboxSession, id, supportSharedMailbox))
      .flatMap((mailbox: MessageManager) => {
        SMono(mailboxManager.hasChildrenReactive(mailbox.getMailboxPath, mailboxSession))
          .flatMap(hasChildren => {
            if (isASystemMailbox(mailbox)) {
              throw SystemMailboxChangeException(id)
            }

            if (hasChildren) {
              throw MailboxHasChildException(id)
            }

            if (onDestroy.value) {
              SMono(mailboxManager.deleteMailboxReactive(id, mailboxSession))
                .flatMap(deletedMailbox => SMono(subscriptionManager.unsubscribeReactive(deletedMailbox.generateAssociatedPath(), mailboxSession)))
                .`then`()
            } else {
              SMono(mailbox.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession)).hasElement
                .handle((hasElement: Boolean, sink: SynchronousSink[Unit]) => {
                  if (hasElement) {
                    sink.error(MailboxHasMailException(id))
                  }
                })
                .`then`(SMono(mailboxManager.deleteMailboxReactive(id, mailboxSession))
                  .flatMap(deletedMailbox => SMono(subscriptionManager.unsubscribeReactive(deletedMailbox.generateAssociatedPath(), mailboxSession)))
                  .`then`())
            }
          })
      })

  private def auditTrail(mailboxSession: MailboxSession, mailboxDeletionResults: MailboxDeletionResults): Unit = {
    if (mailboxDeletionResults.destroyed.nonEmpty) {
      ReactorUtils.logAsMono(() => AuditTrail.entry
        .username(() => mailboxSession.getUser.asString())
        .protocol("JMAP")
        .action("Mailbox/set destroy")
        .parameters(() => ImmutableMap.of("loggedInUser", mailboxSession.getLoggedInUser.toScala.map(_.asString()).getOrElse(""),
          "mailboxIds", StringUtils.join(mailboxDeletionResults.destroyed)))
        .log("JMAP mailbox delete succeeded."))
    }
  }

  private def isASystemMailbox(mailbox: MessageManager): Boolean = Role.from(mailbox.getMailboxPath.getName).isPresent
}
