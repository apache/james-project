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

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.MailboxSetRequest.{MailboxCreationId, UnparsedMailboxId}
import org.apache.james.jmap.mail.{InvalidPropertyException, InvalidUpdateException, IsSubscribed, MailboxCreationRequest, MailboxCreationResponse, MailboxPatchObject, MailboxRights, MailboxSetError, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, NameUpdate, PatchUpdateValidationException, Properties, RemoveEmailsOnDestroy, ServerSetPropertyException, SetErrorDescription, SharedWithResetUpdate, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, UnsupportedPropertyUpdatedException}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{ClientId, Id, Invocation, ServerId, State}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.jmap.utils.quotas.QuotaLoaderWithPreloadedDefaultFactory
import org.apache.james.mailbox.MailboxManager.RenameOption
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{FetchGroup, MailboxId, MailboxPath, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager, Role, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

case class MailboxHasMailException(mailboxId: MailboxId) extends Exception
case class SystemMailboxChangeException(mailboxId: MailboxId) extends Exception
case class MailboxHasChildException(mailboxId: MailboxId) extends Exception
case class MailboxCreationParseException(mailboxSetError: MailboxSetError) extends Exception

sealed trait CreationResult {
  def mailboxCreationId: MailboxCreationId
}
case class CreationSuccess(mailboxCreationId: MailboxCreationId, mailboxCreationResponse: MailboxCreationResponse) extends CreationResult
case class CreationFailure(mailboxCreationId: MailboxCreationId, exception: Exception) extends CreationResult {
  def asMailboxSetError: MailboxSetError = exception match {
    case e: MailboxNotFoundException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("parentId"))))
    case e: MailboxExistsException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("name"))))
    case e: MailboxNameException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("name"))))
    case e: MailboxCreationParseException => e.mailboxSetError
    case _: InsufficientRightsException => MailboxSetError.forbidden(Some(SetErrorDescription("Insufficient rights")), Some(Properties(List("parentId"))))
    case _ => MailboxSetError.serverFail(Some(SetErrorDescription(exception.getMessage)), None)
  }
}
case class CreationResults(created: Seq[CreationResult]) {
  def retrieveCreated: Map[MailboxCreationId, MailboxCreationResponse] = created
    .flatMap(result => result match {
      case success: CreationSuccess => Some(success.mailboxCreationId, success.mailboxCreationResponse)
      case _ => None
    })
    .toMap
    .map(creation => (creation._1, creation._2))

  def retrieveErrors: Map[MailboxCreationId, MailboxSetError] = created
    .flatMap(result => result match {
      case failure: CreationFailure => Some(failure.mailboxCreationId, failure.asMailboxSetError)
      case _ => None
    })
    .toMap
}

sealed trait DeletionResult
case class DeletionSuccess(mailboxId: MailboxId) extends DeletionResult
case class DeletionFailure(mailboxId: UnparsedMailboxId, exception: Throwable) extends DeletionResult {
  def asMailboxSetError: MailboxSetError = exception match {
    case e: MailboxNotFoundException => MailboxSetError.notFound(Some(SetErrorDescription(e.getMessage)))
    case e: MailboxHasMailException => MailboxSetError.mailboxHasEmail(Some(SetErrorDescription(s"${e.mailboxId.serialize} is not empty")))
    case e: MailboxHasChildException => MailboxSetError.mailboxHasChild(Some(SetErrorDescription(s"${e.mailboxId.serialize} has child mailboxes")))
    case e: SystemMailboxChangeException => MailboxSetError.invalidArgument(Some(SetErrorDescription("System mailboxes cannot be destroyed")), None)
    case e: IllegalArgumentException => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"${mailboxId} is not a mailboxId: ${e.getMessage}")), None)
    case _ => MailboxSetError.serverFail(Some(SetErrorDescription(exception.getMessage)), None)
  }
}
case class DeletionResults(results: Seq[DeletionResult]) {
  def destroyed: Seq[MailboxId] =
    results.flatMap(result => result match {
      case success: DeletionSuccess => Some(success)
      case _ => None
    }).map(_.mailboxId)

  def retrieveErrors: Map[UnparsedMailboxId, MailboxSetError] =
    results.flatMap(result => result match {
      case failure: DeletionFailure => Some(failure.mailboxId, failure.asMailboxSetError)
      case _ => None
    })
    .toMap
}

sealed trait UpdateResult
case class UpdateSuccess(mailboxId: MailboxId) extends UpdateResult
case class UpdateFailure(mailboxId: UnparsedMailboxId, exception: Throwable) extends UpdateResult {
  def asMailboxSetError: MailboxSetError = exception match {
    case e: MailboxNotFoundException => MailboxSetError.notFound(Some(SetErrorDescription(e.getMessage)))
    case e: MailboxNameException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("/name"))))
    case e: MailboxExistsException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("/name"))))
    case e: UnsupportedPropertyUpdatedException => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"${e.property} property do not exist thus cannot be updated")), Some(Properties(List(e.property))))
    case e: InvalidUpdateException => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"${e.cause}")), Some(Properties(List(e.property))))
    case e: ServerSetPropertyException => MailboxSetError.invalidArgument(Some(SetErrorDescription("Can not modify server-set properties")), Some(Properties(List(e.property))))
    case e: InvalidPropertyException => MailboxSetError.invalidPatch(Some(SetErrorDescription(s"${e.cause}")))
    case e: SystemMailboxChangeException => MailboxSetError.invalidArgument(Some(SetErrorDescription("Invalid change to a system mailbox")), Some(Properties(List("/name"))))
    case e: InsufficientRightsException => MailboxSetError.invalidArgument(Some(SetErrorDescription("Invalid change to a delegated mailbox")), None)
    case _ => MailboxSetError.serverFail(Some(SetErrorDescription(exception.getMessage)), None)
  }
}
case class UpdateResults(results: Seq[UpdateResult]) {
  def updated: Map[MailboxId, MailboxUpdateResponse] =
    results.flatMap(result => result match {
      case success: UpdateSuccess => Some((success.mailboxId, MailboxSetResponse.empty))
      case _ => None
    }).toMap
  def notUpdated: Map[UnparsedMailboxId, MailboxSetError] = results.flatMap(result => result match {
    case failure: UpdateFailure => Some(failure.mailboxId, failure.asMailboxSetError)
    case _ => None
  }).toMap
}

class MailboxSetMethod @Inject()(serializer: Serializer,
                                 mailboxManager: MailboxManager,
                                 subscriptionManager: SubscriptionManager,
                                 mailboxIdFactory: MailboxId.Factory,
                                 quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                 metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("Mailbox/set")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asMailboxSetRequest(invocation.arguments)
        .flatMap(mailboxSetRequest => {
          for {
            creationResults <- createMailboxes(mailboxSession, mailboxSetRequest, processingContext)
            deletionResults <- deleteMailboxes(mailboxSession, mailboxSetRequest, processingContext)
            updateResults <- updateMailboxes(mailboxSession, mailboxSetRequest, processingContext)
          } yield createResponse(capabilities, invocation, mailboxSetRequest, creationResults, deletionResults, updateResults)
        }))
  }

  private def updateMailboxes(mailboxSession: MailboxSession,
                              mailboxSetRequest: MailboxSetRequest,
                              processingContext: ProcessingContext): SMono[UpdateResults] = {
    SFlux.fromIterable(mailboxSetRequest.update.getOrElse(Seq()))
      .flatMap({
        case (unparsedMailboxId: UnparsedMailboxId, patch: MailboxPatchObject) =>
          processingContext.resolveMailboxId(unparsedMailboxId, mailboxIdFactory).fold(
              e => SMono.just(UpdateFailure(unparsedMailboxId, e)),
              mailboxId => updateMailbox(mailboxSession, mailboxId, patch))
            .onErrorResume(e => SMono.just(UpdateFailure(unparsedMailboxId, e)))
      })
      .collectSeq()
      .map(UpdateResults)
  }

  private def updateMailbox(mailboxSession: MailboxSession,
                            maiboxId: MailboxId,
                            patch: MailboxPatchObject): SMono[UpdateResult] = {
    val updates = patch.updates(serializer)
    val maybeParseException: Option[PatchUpdateValidationException] = updates
      .flatMap(x => x match {
        case Left(e) => Some(e)
        case _ => None
      }).headOption

    val maybeNameUpdate: Option[NameUpdate] = updates
      .flatMap(x => x match {
        case Right(NameUpdate(newName)) => Some(NameUpdate(newName))
        case _ => None
      }).headOption

    val maybeSharedWithResetUpdate: Option[SharedWithResetUpdate] = updates
      .flatMap(x => x match {
        case Right(SharedWithResetUpdate(rights)) => Some(SharedWithResetUpdate(rights))
        case _ => None
      }).headOption

    maybeParseException.map(e => SMono.raiseError[UpdateResult](e))
      .getOrElse(updateMailboxPath(maiboxId, maybeNameUpdate, mailboxSession)
        .`then`(updateMailboxRights(maiboxId, maybeSharedWithResetUpdate, mailboxSession)))
  }

  private def updateMailboxPath(mailboxId: MailboxId, maybeNameUpdate: Option[NameUpdate], mailboxSession: MailboxSession): SMono[UpdateResult] = {
    maybeNameUpdate.map(nameUpdate => {
      SMono.fromCallable(() => {
        val mailbox = mailboxManager.getMailbox(mailboxId, mailboxSession)
        if (isASystemMailbox(mailbox)) {
          throw SystemMailboxChangeException(mailboxId)
        }
        mailboxManager.renameMailbox(mailboxId,
          computeMailboxPath(mailbox, nameUpdate, mailboxSession),
          RenameOption.RENAME_SUBSCRIPTIONS,
          mailboxSession)
      }).`then`(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
        .subscribeOn(Schedulers.elastic())
    })
      // No updated properties passed. Noop.
      .getOrElse(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
  }

  private def updateMailboxRights(mailboxId: MailboxId,
                                  maybeSharedWithResetUpdate: Option[SharedWithResetUpdate],
                                  mailboxSession: MailboxSession): SMono[UpdateResult] = {
   maybeSharedWithResetUpdate.map(sharedWithResetUpdate => {
      SMono.fromCallable(() => {
        mailboxManager.setRights(mailboxId, sharedWithResetUpdate.rights.toMailboxAcl.asJava, mailboxSession)
      }).`then`(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
        .subscribeOn(Schedulers.elastic())
    })
      // No updated properties passed. Noop.
      .getOrElse(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
  }

  private def computeMailboxPath(mailbox: MessageManager, nameUpdate: NameUpdate, mailboxSession: MailboxSession): MailboxPath = {
    val originalPath: MailboxPath = mailbox.getMailboxPath
    val maybeParentPath: Option[MailboxPath] = originalPath.getHierarchyLevels(mailboxSession.getPathDelimiter)
      .asScala
      .reverse
      .drop(1)
      .headOption
    maybeParentPath.map(_.child(nameUpdate.newName, mailboxSession.getPathDelimiter))
      .getOrElse(MailboxPath.forUser(mailboxSession.getUser, nameUpdate.newName))
  }

  private def deleteMailboxes(mailboxSession: MailboxSession, mailboxSetRequest: MailboxSetRequest, processingContext: ProcessingContext): SMono[DeletionResults] = {
    SFlux.fromIterable(mailboxSetRequest.destroy.getOrElse(Seq()))
      .flatMap(id => delete(mailboxSession, processingContext, id, mailboxSetRequest.onDestroyRemoveEmails.getOrElse(RemoveEmailsOnDestroy(false)))
        .onErrorRecover(e => DeletionFailure(id, e)))
      .collectSeq()
      .map(DeletionResults)
  }

  private def delete(mailboxSession: MailboxSession, processingContext: ProcessingContext, id: UnparsedMailboxId, onDestroy: RemoveEmailsOnDestroy): SMono[DeletionResult] = {
    processingContext.resolveMailboxId(id, mailboxIdFactory) match {
      case Right(mailboxId) => SMono.fromCallable(() => delete(mailboxSession, mailboxId, onDestroy))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[DeletionResult](DeletionSuccess(mailboxId)))
      case Left(e) => SMono.raiseError(e)
    }
  }

  private def delete(mailboxSession: MailboxSession, id: MailboxId, onDestroy: RemoveEmailsOnDestroy): Unit = {
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

  private def createMailboxes(mailboxSession: MailboxSession,
                              mailboxSetRequest: MailboxSetRequest,
                              processingContext: ProcessingContext): SMono[CreationResults] = {
    SFlux.fromIterable(mailboxSetRequest.create
      .getOrElse(Map.empty)
      .view)
      .flatMap {
      case (mailboxCreationId: MailboxCreationId, jsObject: JsObject) =>
        SMono.fromCallable(() => {
          createMailbox(mailboxSession, mailboxCreationId, jsObject, processingContext)
        }).subscribeOn(Schedulers.elastic())
    }
      .collectSeq()
      .map(CreationResults)
  }

  private def createMailbox(mailboxSession: MailboxSession,
                            mailboxCreationId: MailboxCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): CreationResult = {
    parseCreate(jsObject)
      .flatMap(mailboxCreationRequest => resolvePath(mailboxSession, mailboxCreationRequest, processingContext)
        .flatMap(path => createMailbox(mailboxSession = mailboxSession,
          path = path,
          mailboxCreationRequest = mailboxCreationRequest)))
      .fold(e => CreationFailure(mailboxCreationId, e),
        creationResponse => {
          recordCreationIdInProcessingContext(mailboxCreationId, processingContext, creationResponse.id)
          CreationSuccess(mailboxCreationId, creationResponse)
        })
  }

  private def parseCreate(jsObject: JsObject): Either[MailboxCreationParseException, MailboxCreationRequest] =
    Json.fromJson(jsObject)(serializer.mailboxCreationRequest) match {
      case JsSuccess(creationRequest, _) => Right(creationRequest)
      case JsError(errors) => Left(MailboxCreationParseException(mailboxSetError(errors)))
    }

  private def mailboxSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): MailboxSetError =
    errors.head match {
      case (path, Seq()) => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"'$path' property in mailbox object is not valid")), None)
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => MailboxSetError("invalidArguments", Some(SetErrorDescription(s"Missing '$path' property in mailbox object")), None)
      case (path, Seq(JsonValidationError(Seq(message)))) => MailboxSetError("invalidArguments", Some(SetErrorDescription(s"'$path' property in mailbox object is not valid: $message")), None)
      case (path, _) => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"Unknown error on property '$path'")), None)
    }

  private def createMailbox(mailboxSession: MailboxSession,
                            path: MailboxPath,
                            mailboxCreationRequest: MailboxCreationRequest): Either[Exception, MailboxCreationResponse] = {
    try {
      //can safely do a get as the Optional is empty only if the mailbox name is empty which is forbidden by the type constraint on MailboxName
      val mailboxId = mailboxManager.createMailbox(path, mailboxSession).get()

      val defaultSubscribed = IsSubscribed(true)
      if (mailboxCreationRequest.isSubscribed.getOrElse(defaultSubscribed).value) {
        subscriptionManager.subscribe(mailboxSession, path.getName)
      }

      mailboxCreationRequest.rights
          .foreach(rights => mailboxManager.setRights(mailboxId, rights.toMailboxAcl.asJava, mailboxSession))

      val quotas = quotaFactory.loadFor(mailboxSession)
        .flatMap(quotaLoader => quotaLoader.getQuotas(path))
        .block()

      Right(MailboxCreationResponse(
        id = mailboxId,
        sortOrder = SortOrder.defaultSortOrder,
        role = None,
        totalEmails = TotalEmails(0L),
        unreadEmails = UnreadEmails(0L),
        totalThreads = TotalThreads(0L),
        unreadThreads = UnreadThreads(0L),
        myRights = MailboxRights.FULL,
        quotas = Some(quotas),
        isSubscribed =  if (mailboxCreationRequest.isSubscribed.isEmpty) {
          Some(defaultSubscribed)
        } else {
          None
        }))
    } catch {
      case error: Exception => Left(error)
    }
  }

  private def recordCreationIdInProcessingContext(mailboxCreationId: MailboxCreationId,
                                                  processingContext: ProcessingContext,
                                                  mailboxId: MailboxId): Unit = {
    for {
      creationId <- Id.validate(mailboxCreationId)
      serverAssignedId <- Id.validate(mailboxId.serialize())
    } yield {
      processingContext.recordCreatedId(ClientId(creationId), ServerId(serverAssignedId))
    }
  }

  private def resolvePath(mailboxSession: MailboxSession,
                          mailboxCreationRequest: MailboxCreationRequest,
                          processingContext: ProcessingContext): Either[Exception, MailboxPath] = {
    mailboxCreationRequest.parentId
      .map(maybeParentId => for {
        parentId <- processingContext.resolveMailboxId(maybeParentId, mailboxIdFactory)
        parentPath <- retrievePath(parentId, mailboxSession)
      } yield {
        parentPath.child(mailboxCreationRequest.name, mailboxSession.getPathDelimiter)
      })
      .getOrElse(Right(MailboxPath.forUser(mailboxSession.getUser, mailboxCreationRequest.name)))
  }

  private def retrievePath(mailboxId: MailboxId, mailboxSession: MailboxSession): Either[Exception, MailboxPath] = try {
      Right(mailboxManager.getMailbox(mailboxId, mailboxSession).getMailboxPath)
    } catch {
      case e: Exception => Left(e)
    }

  private def createResponse(capabilities: Set[CapabilityIdentifier],
                             invocation: Invocation,
                             mailboxSetRequest: MailboxSetRequest,
                             creationResults: CreationResults,
                             deletionResults: DeletionResults,
                             updateResults: UpdateResults): Invocation = {
    val response = MailboxSetResponse(
      mailboxSetRequest.accountId,
      oldState = None,
      newState = State.INSTANCE,
      destroyed = Some(deletionResults.destroyed).filter(_.nonEmpty),
      created = Some(creationResults.retrieveCreated).filter(_.nonEmpty),
      notCreated = Some(creationResults.retrieveErrors).filter(_.nonEmpty),
      updated = Some(updateResults.updated).filter(_.nonEmpty),
      notUpdated = Some(updateResults.notUpdated).filter(_.nonEmpty),
      notDestroyed = Some(deletionResults.retrieveErrors).filter(_.nonEmpty))
    
    Invocation(methodName,
      Arguments(serializer.serialize(response, capabilities).as[JsObject]),
      invocation.methodCallId)
  }

  private def asMailboxSetRequest(arguments: Arguments): SMono[MailboxSetRequest] = {
    serializer.deserializeMailboxSetRequest(arguments.value) match {
      case JsSuccess(mailboxSetRequest, _) => SMono.just(mailboxSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }
}
