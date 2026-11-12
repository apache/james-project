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
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{InvalidPatchException, InvalidPropertyException, InvalidUpdateException, MailboxPatchObject, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, NameUpdate, ParentIdUpdate, ServerSetPropertyException, UnparsedMailboxId, UnsupportedPropertyUpdatedException, ValidatedMailboxPatchObject}
import org.apache.james.jmap.method.MailboxSetUpdatePerformer.{MailboxUpdateFailure, MailboxUpdateResult, MailboxUpdateResults, MailboxUpdateSuccess}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.MailboxManager.{MailboxSearchFetchType, RenameOption}
import org.apache.james.mailbox.exception.{DifferentDomainException, InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.search.{MailboxQuery, PrefixedWildcard}
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath}
import org.apache.james.mailbox.{DefaultMailboxes, MailboxManager, MailboxSession, MessageManager, Role, SubscriptionManager}
import org.apache.james.util.{AuditTrail, ReactorUtils}
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object MailboxSetUpdatePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[MailboxSetUpdatePerformer])

  /**
   * @return the commands giving back to the given entries their rights in previousAcl, leaving the other entries alone
   */
  private[method] def revertCommands(previousAcl: MailboxACL, entryKeys: Set[MailboxACL.EntryKey]): Seq[MailboxACL.ACLCommand] =
    entryKeys.toSeq.map(entryKey => MailboxACL.command()
      .key(entryKey)
      .rights(Option(previousAcl.getEntries.get(entryKey)).getOrElse(MailboxACL.NO_RIGHTS))
      .asReplacement())

  sealed trait MailboxUpdateResult
  case class MailboxUpdateSuccess(mailboxId: MailboxId) extends MailboxUpdateResult
  case class MailboxUpdateFailure(mailboxId: UnparsedMailboxId, exception: Throwable, patch: Option[ValidatedMailboxPatchObject]) extends MailboxUpdateResult {
    def filter(acceptableProperties: Properties): Option[Properties] = Some(patch
      .map(_.updatedProperties.intersect(acceptableProperties))
      .getOrElse(acceptableProperties))

    def asMailboxSetError: SetError = exception match {
      case e: MailboxNotFoundException =>
        LOGGER.info("Mailbox/set update failed: {}", e.getMessage)
        SetError.notFound(SetErrorDescription(e.getMessage))
      case e: MailboxNameException =>
        LOGGER.info("Invalid mailbox name: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), filter(Properties("name", "parentId")))
      case e: MailboxExistsException =>
        LOGGER.info("Mailbox already exists: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), filter(Properties("name", "parentId")))
      case e: UnsupportedPropertyUpdatedException =>
        LOGGER.info("Unsupported properties in Mailbox/set update: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(s"${e.property} property do not exist thus cannot be updated"), Some(Properties(e.property)))
      case e: InvalidUpdateException =>
        LOGGER.info("Mailbox/set update failed for target '{}' and property '{}': {}", mailboxId.id.value, e.property.value, e.cause)
        SetError.invalidArguments(SetErrorDescription(s"${e.cause}"), Some(Properties(e.property)))
      case e: ServerSetPropertyException =>
        LOGGER.info("Unsupported server-set properties in Mailbox/set update: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription("Can not modify server-set properties"), Some(Properties(e.property)))
      case e: InvalidPropertyException =>
        LOGGER.info("Invalid properties in Mailbox/set update: {}", e.getMessage)
        SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
      case e: InvalidPatchException =>
        LOGGER.info("Unsupported patch in Mailbox/set update: {}", e.getMessage)
        SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
      case e: SystemMailboxChangeException =>
        LOGGER.info("Attempt to update a system mailbox was rejected: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription("Invalid change to a system mailbox"), filter(Properties("name", "parentId")))
      case e: LoopInMailboxGraphException =>
        LOGGER.info("Attempt to create a loop in mailbox graph was rejected: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription("A mailbox parentId property can not be set to itself or one of its child"), Some(Properties("parentId")))
      case e: InsufficientRightsException =>
        LOGGER.info("Attempt to set a mailbox while having insufficient rights was rejected: {}", e.getMessage)
        SetError.forbidden(SetErrorDescription("Invalid change to a delegated mailbox"))
      case e: IllegalArgumentException =>
        LOGGER.info("Mailbox/set update failed for target '{}': {}", mailboxId.id.value, e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
      case e: DifferentDomainException =>
        LOGGER.info("Invalid arguments in Mailbox/set update", e)
        SetError.invalidArguments(SetErrorDescription("Invalid arguments in Mailbox/set update: different domains"), None)
      case e =>
        LOGGER.error("Failed to update mailbox", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
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
                      capabilities: Set[CapabilityIdentifier],
                      processingContext: ProcessingContext = ProcessingContext(Map.empty, Map.empty)): SMono[MailboxUpdateResults] =
    SFlux.fromIterable(MailboxSetReferenceResolver.updates(mailboxSetRequest, processingContext, mailboxIdFactory))
      .flatMap({
        case (original, resolved) =>
          resolved.fold(
            e => SMono.just(MailboxUpdateFailure(original, e, None)),
            { case (mailboxId, patch) => updateMailbox(mailboxSession, mailboxId, original, patch, capabilities) })
            .onErrorResume(e => SMono.just(MailboxUpdateFailure(original, e, None)))
      }, maxConcurrency = 5)
      .collectSeq()
      .map(MailboxUpdateResults)

  private def updateMailbox(mailboxSession: MailboxSession,
                            mailboxId: MailboxId,
                            unparsedMailboxId: UnparsedMailboxId,
                            patch: MailboxPatchObject,
                            capabilities: Set[CapabilityIdentifier]): SMono[MailboxUpdateResult] = {
    patch.validate(mailboxIdFactory, serializer, capabilities, mailboxSession)
      .fold(e => SMono.error(e), validatedPatch => {
        val supportSharedMailbox: Boolean = capabilities.contains(JAMES_SHARES)
        // An update is a unit: validate the new path before writing anything, and revert the rights changes if the
        // update still fails
        getMessageManager(supportSharedMailbox, mailboxId, mailboxSession)
          .flatMap(messageManager => computeNewMailboxPath(mailboxId, validatedPatch, mailboxSession, messageManager, supportSharedMailbox)
            .flatMap {
              case Left(e) => SMono.just[MailboxUpdateResult](MailboxUpdateFailure(unparsedMailboxId, e, Some(validatedPatch)))
              case scala.Right(newPath) => updateMailboxRights(mailboxId, validatedPatch, mailboxSession)
                .flatMap(rightsRevert => updateSubscription(mailboxId, validatedPatch, mailboxSession, messageManager)
                  .`then`(updateMailboxPath(mailboxId, unparsedMailboxId, validatedPatch, newPath, mailboxSession, messageManager))
                  .flatMap(result => restoreRightsOnFailure(mailboxId, rightsRevert, result, mailboxSession)))
            })
      })
  }

  private def computeNewMailboxPath(mailboxId: MailboxId,
                                    validatedPatch: ValidatedMailboxPatchObject,
                                    mailboxSession: MailboxSession,
                                    mailbox: MessageManager,
                                    supportSharedMailbox: Boolean): SMono[Either[Exception, Option[MailboxPath]]] =
    if (validatedPatch.shouldUpdateMailboxPath) {
      SMono.fromCallable[Either[Exception, Option[MailboxPath]]](() => {
        try {
          if (isASystemMailbox(mailbox) && !DefaultMailboxes.INBOX.equalsIgnoreCase(mailbox.getMailboxPath.getName)) {
            throw SystemMailboxChangeException(mailboxId)
          }
          if (validatedPatch.parentIdUpdate.flatMap(_.newId).contains(mailboxId)) {
            throw LoopInMailboxGraphException(mailboxId)
          }
          val oldPath = mailbox.getMailboxPath
          val newPath = applyParentIdUpdate(mailboxId, validatedPatch.parentIdUpdate, mailboxSession, supportSharedMailbox)
            .andThen(applyNameUpdate(validatedPatch.nameUpdate, mailboxSession))
            .apply(oldPath)
          if (oldPath.equals(newPath)) {
            scala.Right(None)
          } else {
            val delimiter = mailboxSession.getPathDelimiter
            val sanitizedPath = newPath.sanitize(delimiter)
            sanitizedPath.assertAcceptable(delimiter)
            if (SMono(mailboxManager.mailboxExists(sanitizedPath, mailboxSession)).block()) {
              throw new MailboxExistsException(sanitizedPath.asString())
            }
            scala.Right(Some(newPath))
          }
        } catch {
          case e: Exception => Left(e)
        }
      }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
    } else {
      SMono.just(scala.Right(None))
    }

  private def restoreRightsOnFailure(mailboxId: MailboxId, rightsRevert: Seq[MailboxACL.ACLCommand], result: MailboxUpdateResult,
                                     mailboxSession: MailboxSession): SMono[MailboxUpdateResult] =
    result match {
      case _: MailboxUpdateFailure if rightsRevert.nonEmpty =>
        SMono.fromCallable(() => revertRights(mailboxId, rightsRevert, mailboxSession))
          .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
          .`then`(SMono.just(result))
      case _ => SMono.just(result)
    }

  private def revertRights(mailboxId: MailboxId, rightsRevert: Seq[MailboxACL.ACLCommand], mailboxSession: MailboxSession): Unit =
    rightsRevert.foreach(command => try {
      mailboxManager.applyRightsCommand(mailboxId, command, mailboxSession)
    } catch {
      case e: Exception => MailboxSetUpdatePerformer.LOGGER.error("Failed to restore the rights of {} for {} after a failed update",
        mailboxId.serialize(), command.getEntryKey.serialize(), e)
    })

  private def updateSubscription(mailboxId: MailboxId, validatedPatch: ValidatedMailboxPatchObject, mailboxSession: MailboxSession, messageManager: MessageManager): SMono[MailboxUpdateResult] = {
    validatedPatch.isSubscribedUpdate.map(isSubscribedUpdate => {
      SMono.fromCallable(() => {
        val isOwner = messageManager.getMailboxPath.belongsTo(mailboxSession)
        val shouldSubscribe = isSubscribedUpdate.isSubscribed.map(_.value).getOrElse(isOwner)

        if (shouldSubscribe) {
          subscriptionManager.subscribe(mailboxSession, messageManager.getMailboxPath)
        } else {
          subscriptionManager.unsubscribe(mailboxSession, messageManager.getMailboxPath)
        }
      }).`then`(SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId)))
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
    })
      .getOrElse(SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId)))
  }

  private def updateMailboxPath(mailboxId: MailboxId,
                                unparsedMailboxId: UnparsedMailboxId,
                                validatedPatch: ValidatedMailboxPatchObject,
                                newPath: Option[MailboxPath],
                                mailboxSession: MailboxSession,
                                mailbox: MessageManager): SMono[MailboxUpdateResult] = {
    if (validatedPatch.shouldUpdateMailboxPath) {
      SMono.fromCallable[MailboxUpdateResult](() => {
          try {
            newPath.foreach(path => mailboxManager.renameMailbox(mailboxId,
              path,
              RenameOption.RENAME_SUBSCRIPTIONS,
              mailboxSession))
            MailboxUpdateSuccess(mailboxId)
          } catch {
            case e: Exception => MailboxUpdateFailure(unparsedMailboxId, e, Some(validatedPatch))
          }
        }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
        .flatMap(updateResult => createInboxIfNeeded(mailbox.getMailboxPath, mailboxSession)
          .`then`(SMono.just(updateResult)))
    } else {
      SMono.just[MailboxUpdateResult](MailboxUpdateSuccess(mailboxId))
    }
  }

  private def applyParentIdUpdate(mailboxId: MailboxId, maybeParentIdUpdate: Option[ParentIdUpdate],
                                  mailboxSession: MailboxSession, supportSharedMailbox: Boolean): MailboxPath => MailboxPath =
    maybeParentIdUpdate.map(parentIdUpdate => applyParentIdUpdate(mailboxId, parentIdUpdate, mailboxSession, supportSharedMailbox))
      .getOrElse(x => x)

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

  private def applyParentIdUpdate(mailboxId: MailboxId, parentIdUpdate: ParentIdUpdate,
                                  mailboxSession: MailboxSession, supportSharedMailbox: Boolean): MailboxPath => MailboxPath = {
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
          if (!parentPath.belongsTo(mailboxSession) && !supportSharedMailbox) throw new MailboxNotFoundException(id)
          parentPath.child(currentName, mailboxSession.getPathDelimiter)
        })
        .getOrElse(MailboxPath.forUser(originalPath.getUser, currentName))
    }
  }

  private def getMessageManager(supportSharedMailbox: Boolean, mailboxId: MailboxId, mailboxSession: MailboxSession): SMono[MessageManager] =
    SMono(mailboxManager.getMailboxReactive(mailboxId, mailboxSession))
      .filterWhen(MailboxSetMethod.assertCapabilityIfSharedMailbox(mailboxSession, mailboxId, supportSharedMailbox))

  /**
   * Applies the sharedWith changes. Partial updates go through applyRightsCommand, applied atomically by the mappers, so
   * that the entries this patch does not touch are left alone even if they are concurrently modified.
   *
   * If one of the changes is rejected (eg a sharee of another domain), the changes already applied are reverted
   * before failing.
   *
   * @return the commands reverting the changes of this patch: they only touch the entries of this patch
   */
  private def updateMailboxRights(mailboxId: MailboxId,
                                  validatedPatch: ValidatedMailboxPatchObject,
                                  mailboxSession: MailboxSession): SMono[Seq[MailboxACL.ACLCommand]] = {
    if (validatedPatch.rightsReset.isEmpty && validatedPatch.rightsPartialUpdates.isEmpty) {
      return SMono.just(Seq())
    }

    SMono.fromCallable(() => {
      val previousAcl: MailboxACL = mailboxManager.listRights(mailboxId, mailboxSession)
      var applied: Seq[MailboxACL.ACLCommand] = Seq()
      try {
        validatedPatch.rightsReset.foreach(sharedWithResetUpdate => {
          val resetAcl: MailboxACL = sharedWithResetUpdate.rights.toMailboxAcl.asJava
          mailboxManager.setRights(mailboxId, resetAcl, mailboxSession)
          applied = MailboxSetUpdatePerformer.revertCommands(previousAcl,
            previousAcl.getEntries.keySet.asScala.toSet ++ resetAcl.getEntries.keySet.asScala) ++ applied
        })
        validatedPatch.rightsPartialUpdates.foreach(partialUpdate => {
          mailboxManager.applyRightsCommand(mailboxId, partialUpdate.asACLCommand(), mailboxSession)
          applied = MailboxSetUpdatePerformer.revertCommands(previousAcl, Set(partialUpdate.entryKey)) ++ applied
        })
        applied
      } catch {
        case e: Exception =>
          revertRights(mailboxId, applied, mailboxSession)
          throw e
      }
    }).flatMap(rightsRevert => SFlux.fromIterable(validatedPatch.rightsPartialUpdates)
        .concatMap(partialUpdate => SMono(ReactorUtils.logAsMono(() => AuditTrail.entry
          .username(() => mailboxSession.getUser.asString())
          .protocol("JMAP")
          .action("Mailbox/set update")
          .parameters(() => ImmutableMap.of("loggedInUser", mailboxSession.getLoggedInUser.toScala.map(_.asString()).getOrElse(""),
            "delegator", mailboxSession.getUser.asString(),
            "delegatee", partialUpdate.entryKey.getName,
            "mailboxId", mailboxId.serialize(),
            "rights", partialUpdate.rights.asJava.serialize()))
          .log("JMAP mailbox shared."))))
        .`then`()
        .`then`(SMono.just(rightsRevert)))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
  }

  private def createInboxIfNeeded(existingPath: MailboxPath, session: MailboxSession): SMono[Unit] = {
    if (!existingPath.getName.equalsIgnoreCase(DefaultMailboxes.INBOX)) {
      return SMono.empty
    }
    SMono(mailboxManager.mailboxExists(existingPath, session)).flatMap(exists => createInbox(exists, existingPath, session))
  }

  private def createInbox(exists: Boolean, existingPath: MailboxPath, session: MailboxSession): SMono[Unit] = {
    if (exists) {
      return SMono.empty
    }
    SMono(mailboxManager.createMailboxReactive(existingPath, session))
      .map(_ => ())
  }

  private def isASystemMailbox(mailbox: MessageManager): Boolean =
    Role.from(mailbox.getMailboxPath.getName).isPresent
}
