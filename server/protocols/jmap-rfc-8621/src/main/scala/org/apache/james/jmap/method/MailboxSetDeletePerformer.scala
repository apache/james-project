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

import javax.inject.Inject
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.MailboxGet.UnparsedMailboxId
import org.apache.james.jmap.mail.{MailboxGet, MailboxSetError, MailboxSetRequest, RemoveEmailsOnDestroy}
import org.apache.james.jmap.method.MailboxSetDeletePerformer.{MailboxDeletionFailure, MailboxDeletionResult, MailboxDeletionResults, MailboxDeletionSuccess}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{FetchGroup, MailboxId, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager, Role, SubscriptionManager}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

object MailboxSetDeletePerformer {
  sealed trait MailboxDeletionResult
  case class MailboxDeletionSuccess(mailboxId: MailboxId) extends MailboxDeletionResult
  case class MailboxDeletionFailure(mailboxId: UnparsedMailboxId, exception: Throwable) extends MailboxDeletionResult {
    def asMailboxSetError: SetError = exception match {
      case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
      case e: MailboxHasMailException => MailboxSetError.mailboxHasEmail(SetErrorDescription(s"${e.mailboxId.serialize} is not empty"))
      case e: MailboxHasChildException => MailboxSetError.mailboxHasChild(SetErrorDescription(s"${e.mailboxId.serialize} has child mailboxes"))
      case e: SystemMailboxChangeException => SetError.invalidArguments(SetErrorDescription("System mailboxes cannot be destroyed"))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${mailboxId} is not a mailboxId: ${e.getMessage}"))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
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

  def deleteMailboxes(mailboxSession: MailboxSession, mailboxSetRequest: MailboxSetRequest): SMono[MailboxDeletionResults] = {
    SFlux.fromIterable(mailboxSetRequest.destroy.getOrElse(Seq()))
      .flatMap(id => delete(mailboxSession, id, mailboxSetRequest.onDestroyRemoveEmails.getOrElse(RemoveEmailsOnDestroy(false)))
        .onErrorRecover(e => MailboxDeletionFailure(id, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(MailboxDeletionResults)
  }

  private def delete(mailboxSession: MailboxSession, id: UnparsedMailboxId, onDestroy: RemoveEmailsOnDestroy): SMono[MailboxDeletionResult] = {
    MailboxGet.parse(mailboxIdFactory)(id)
      .fold(e => SMono.raiseError(e),
        id => SMono.fromCallable(() => doDelete(mailboxSession, id, onDestroy))
          .subscribeOn(Schedulers.elastic())
          .`then`(SMono.just[MailboxDeletionResult](MailboxDeletionSuccess(id))))

  }

  private def doDelete(mailboxSession: MailboxSession, id: MailboxId, onDestroy: RemoveEmailsOnDestroy): Unit = {
    val mailbox = mailboxManager.getMailbox(id, mailboxSession)

    if (isASystemMailbox(mailbox)) {
      throw SystemMailboxChangeException(id)
    }

    if (mailboxManager.hasChildren(mailbox.getMailboxPath, mailboxSession)) {
      throw MailboxHasChildException(id)
    }

    if (onDestroy.value) {
      val deletedMailbox = mailboxManager.deleteMailbox(id, mailboxSession)
      subscriptionManager.unsubscribe(mailboxSession, deletedMailbox.getName)
    } else {
      if (mailbox.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).hasNext) {
        throw MailboxHasMailException(id)
      }

      val deletedMailbox = mailboxManager.deleteMailbox(id, mailboxSession)
      subscriptionManager.unsubscribe(mailboxSession, deletedMailbox.getName)
    }
  }

  private def isASystemMailbox(mailbox: MessageManager): Boolean = Role.from(mailbox.getMailboxPath.getName).isPresent
}
