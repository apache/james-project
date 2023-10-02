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
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{InvalidPatchException, InvalidPropertyException, InvalidUpdateException, MailboxGet, MailboxPatchObject, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, NameUpdate, ParentIdUpdate, ServerSetPropertyException, UnparsedMailboxId, UnsupportedPropertyUpdatedException, ValidatedMailboxPatchObject}
import org.apache.james.jmap.method.MailboxSetUpdatePerformer.{MailboxUpdateFailure, MailboxUpdateResult, MailboxUpdateResults, MailboxUpdateSuccess}
import org.apache.james.mailbox.MailboxManager.{MailboxSearchFetchType, RenameOption}
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.search.{MailboxQuery, PrefixedWildcard}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager, Role, SubscriptionManager}
import org.apache.james.util.{AuditTrail, ReactorUtils}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object MailboxSetUpdatePerformer {

  sealed trait MailboxUpdateResult
  case class MailboxUpdateSuccess(mailboxId: MailboxId) extends MailboxUpdateResult
  case class MailboxUpdateFailure(mailboxId: UnparsedMailboxId, exception: Throwable, patch: Option[ValidatedMailboxPatchObject]) extends MailboxUpdateResult {
    def filter(acceptableProperties: Properties): Option[Properties] = Some(patch
      .map(_.updatedProperties.intersect(acceptableProperties))
      .getOrElse(acceptableProperties))

    def asMailboxSetError: SetError = exception match {
      case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
      case e: MailboxNameException => SetError.invalidArguments(SetErrorDescription(e.getMessage), filter(Properties("name", "parentId")))
      case e: MailboxExistsException => SetError.invalidArguments(SetErrorDescription(e.getMessage), filter(Properties("name", "parentId")))
      case e: UnsupportedPropertyUpdatedException => SetError.invalidArguments(SetErrorDescription(s"${e.property} property do not exist thus cannot be updated"), Some(Properties(e.property)))
      case e: InvalidUpdateException => SetError.invalidArguments(SetErrorDescription(s"${e.cause}"), Some(Properties(e.property)))
      case e: ServerSetPropertyException => SetError.invalidArguments(SetErrorDescription("Can not modify server-set properties"), Some(Properties(e.property)))
      case e: InvalidPropertyException => SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
      case e: InvalidPatchException => SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
      case e: SystemMailboxChangeException => SetError.invalidArguments(SetErrorDescription("Invalid change to a system mailbox"), filter(Properties("name", "parentId")))
      case e: LoopInMailboxGraphException => SetError.invalidArguments(SetErrorDescription("A mailbox parentId property can not be set to itself or one of its child"), Some(Properties("parentId")))
      case e: InsufficientRightsException => SetError.invalidArguments(SetErrorDescription("Invalid change to a delegated mailbox"))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class MailboxUpdateResults(results: Seq[MailboxUpdateResult]) {
    def updated: Map[MailboxId, MailboxUpdateResponse] =
      results.flatMap(result => result match {
        case success: MailboxUpdateSuccess => Some((success.mailboxId, MailboxSetResponse.empty))
        case _ => None
      }).toMap
    def notUpdated: Map[UnparsedMailboxId, SetError] = results.flatMap(result => result match {
      case failure: MailboxUpdateFailure => Some(failure.mailboxId, failure.asMailboxSetError)
      case _ => None
    }).toMap
  }
}

class MailboxSetUpdatePerformer @Inject()(serializer: MailboxSerializer,
                                          mailboxManager: MailboxManager,
                                          subscriptionManager: SubscriptionManager,
                                          mailboxIdFactory: MailboxId.Factory) {

  def updateMailboxes(mailboxSession: MailboxSession,
                              mailboxSetRequest: MailboxSetRequest,
                              capabilities: Set[CapabilityIdentifier]): SMono[MailboxUpdateResults] = {
    SFlux.fromIterable(mailboxSetRequest.update.getOrElse(Seq()))
      .flatMap({
        case (unparsedMailboxId: UnparsedMailboxId, patch: MailboxPatchObject) =>
          MailboxGet.parse(mailboxIdFactory)(unparsedMailboxId)
            .fold(
              e => SMono.just(MailboxUpdateFailure(unparsedMailboxId, e, None)),
              mailboxId => updateMailbox(mailboxSession, mailboxId, unparsedMailboxId, patch, capabilities))
            .onErrorResume(e => SMono.just(MailboxUpdateFailure(unparsedMailboxId, e, None)))
      }, maxConcurrency = 5)
      .collectSeq()
      .map(MailboxUpdateResults)
  }

  private def updateMailbox(mailboxSession: MailboxSession,
                            mailboxId: MailboxId,
                            unparsedMailboxId: UnparsedMailboxId,
                            patch: MailboxPatchObject,
                            capabilities: Set[CapabilityIdentifier]): SMono[MailboxUpdateResult] = {
    patch.validate(mailboxIdFactory, serializer, capabilities, mailboxSession)
      .fold(e => SMono.error(e), validatedPatch =>
        updateMailboxRights(mailboxId, validatedPatch, mailboxSession)
          .`then`(updateSubscription(mailboxId, validatedPatch, mailboxSession))
          .`then`(updateMailboxPath(mailboxId, unparsedMailboxId, validatedPatch, mailboxSession)))
  }

  private def updateSubscription(mailboxId: MailboxId, validatedPatch: ValidatedMailboxPatchObject, mailboxSession: MailboxSession): SMono[MailboxUpdateResult] = {
    validatedPatch.isSubscribedUpdate.map(isSubscribedUpdate => {
      SMono.fromCallable(() => {
        val mailbox = mailboxManager.getMailbox(mailboxId, mailboxSession)
        val isOwner = mailbox.getMailboxPath.belongsTo(mailboxSession)
        val shouldSubscribe = isSubscribedUpdate.isSubscribed.map(_.value).getOrElse(isOwner)

        if (shouldSubscribe) {
          subscriptionManager.subscribe(mailboxSession, mailbox.getMailboxPath)
        } else {
          subscriptionManager.unsubscribe(mailboxSession, mailbox.getMailboxPath)
        }
      }).`then`(SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId)))
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
    })
      .getOrElse(SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId)))
  }

  private def updateMailboxPath(mailboxId: MailboxId,
                                unparsedMailboxId: UnparsedMailboxId,
                                validatedPatch: ValidatedMailboxPatchObject,
                                mailboxSession: MailboxSession): SMono[MailboxUpdateResult] = {
    if (validatedPatch.shouldUpdateMailboxPath) {
      SMono.fromCallable[MailboxUpdateResult](() => {
        try {
          val mailbox = mailboxManager.getMailbox(mailboxId, mailboxSession)
          if (isASystemMailbox(mailbox)) {
            throw SystemMailboxChangeException(mailboxId)
          }
          if (validatedPatch.parentIdUpdate.flatMap(_.newId).contains(mailboxId)) {
            throw LoopInMailboxGraphException(mailboxId)
          }
          val oldPath = mailbox.getMailboxPath
          val newPath = applyParentIdUpdate(mailboxId, validatedPatch.parentIdUpdate, mailboxSession)
            .andThen(applyNameUpdate(validatedPatch.nameUpdate, mailboxSession))
            .apply(oldPath)
          if (!oldPath.equals(newPath)) {
            mailboxManager.renameMailbox(mailboxId,
              newPath,
              RenameOption.RENAME_SUBSCRIPTIONS,
              mailboxSession)
          }
          MailboxUpdateSuccess(mailboxId)
        } catch {
          case e: Exception => MailboxUpdateFailure(unparsedMailboxId, e, Some(validatedPatch))
        }
      })
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
    } else {
      SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId))
    }
  }

  private def applyParentIdUpdate(mailboxId: MailboxId, maybeParentIdUpdate: Option[ParentIdUpdate], mailboxSession: MailboxSession): MailboxPath => MailboxPath = {
    maybeParentIdUpdate.map(parentIdUpdate => applyParentIdUpdate(mailboxId, parentIdUpdate, mailboxSession))
      .getOrElse(x => x)
  }

  private def applyNameUpdate(maybeNameUpdate: Option[NameUpdate], mailboxSession: MailboxSession): MailboxPath => MailboxPath = {
    originalPath => maybeNameUpdate.map(nameUpdate => {
      val originalParentPath: Option[MailboxPath] = originalPath.getHierarchyLevels(mailboxSession.getPathDelimiter)
        .asScala
        .reverse
        .drop(1)
        .headOption
      originalParentPath.map(_.child(nameUpdate.newName, mailboxSession.getPathDelimiter))
        .getOrElse(MailboxPath.forUser(mailboxSession.getUser, nameUpdate.newName))
    }).getOrElse(originalPath)
  }

  private def applyParentIdUpdate(mailboxId: MailboxId, parentIdUpdate: ParentIdUpdate, mailboxSession: MailboxSession): MailboxPath => MailboxPath = {
    originalPath => {
      val currentName = originalPath.getName(mailboxSession.getPathDelimiter)
      parentIdUpdate.newId
        .map(id => {
          val createsALoop = SFlux.fromPublisher(
            mailboxManager.search(MailboxQuery.builder()
              .userAndNamespaceFrom(originalPath)
              .expression(new PrefixedWildcard(originalPath.getName + mailboxSession.getPathDelimiter))
              .build(),
              MailboxSearchFetchType.Minimal,
              mailboxSession))
            .filter(child => child.getId.equals(id))
            .hasElements
            .block()

          if (createsALoop) {
            throw LoopInMailboxGraphException(mailboxId)
          }
          val parentPath = mailboxManager.getMailbox(id, mailboxSession).getMailboxPath
          parentPath.child(currentName, mailboxSession.getPathDelimiter)
        })
        .getOrElse(MailboxPath.forUser(originalPath.getUser, currentName))
    }
  }

  private def updateMailboxRights(mailboxId: MailboxId,
                                  validatedPatch: ValidatedMailboxPatchObject,
                                  mailboxSession: MailboxSession): SMono[MailboxUpdateResult] = {

    val resetOperation: SMono[Unit] = validatedPatch.rightsReset.map(sharedWithResetUpdate => {
      SMono.fromCallable(() => {
        mailboxManager.setRights(mailboxId, sharedWithResetUpdate.rights.toMailboxAcl.asJava, mailboxSession)
      }).`then`()
    }).getOrElse(SMono.empty)

    val partialUpdatesOperation: SMono[Unit] = SFlux.fromIterable(validatedPatch.rightsPartialUpdates)
      .flatMap(partialUpdate => SMono.fromCallable(() => mailboxManager.applyRightsCommand(mailboxId, partialUpdate.asACLCommand(), mailboxSession))
        .doOnSuccess(_ => AuditTrail.entry
          .username(() => mailboxSession.getUser.asString())
          .protocol("JMAP")
          .action("Mailbox/set update")
          .parameters(() => ImmutableMap.of("loggedInUser", mailboxSession.getLoggedInUser.toScala.map(_.asString()).getOrElse(""),
            "delegator", mailboxSession.getUser.asString(),
            "delegatee", partialUpdate.username.asString(),
            "mailboxId", mailboxId.serialize(),
            "rights", partialUpdate.rights.asJava.serialize()))
          .log("JMAP mailbox shared.")),
        maxConcurrency = 5)
      .`then`()

    SFlux.merge(Seq(resetOperation, partialUpdatesOperation))
      .`then`()
      .`then`(SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId)))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  }

  private def isASystemMailbox(mailbox: MessageManager): Boolean = Role.from(mailbox.getMailboxPath.getName).isPresent

}
