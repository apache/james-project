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
import org.apache.james.jmap.mail.{IsSubscribed, MailboxCreationRequest, MailboxCreationResponse, MailboxRights, MailboxSetError, MailboxSetRequest, MailboxSetResponse, Properties, SetErrorDescription, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{ClientId, Id, Invocation, ServerId, State}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{FetchGroup, Mailbox, MailboxId, MailboxPath, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json._
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.collection.immutable

case class MailboxHasMailException(mailboxId: MailboxId) extends Exception
case class MailboxHasChildException(mailboxId: MailboxId) extends Exception

sealed trait CreationResult {
  def mailboxCreationId: MailboxCreationId
}
case class CreationSuccess(mailboxCreationId: MailboxCreationId, mailboxId: MailboxId) extends CreationResult
case class CreationFailure(mailboxCreationId: MailboxCreationId, exception: Exception) extends CreationResult {
  def asMailboxSetError: MailboxSetError = exception match {
    case e: MailboxNotFoundException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("parentId"))))
    case e: MailboxExistsException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("name"))))
    case e: MailboxNameException => MailboxSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)), Some(Properties(List("name"))))
    case _: InsufficientRightsException => MailboxSetError.forbidden(Some(SetErrorDescription("Insufficient rights")), Some(Properties(List("parentId"))))
    case _ => MailboxSetError.serverFail(Some(SetErrorDescription(exception.getMessage)), None)
  }
}
case class CreationResults(created: Seq[CreationResult]) {
  def retrieveCreated: Map[MailboxCreationId, MailboxId] = created
    .flatMap(result => result match {
      case success: CreationSuccess => Some(success.mailboxCreationId, success.mailboxId)
      case _ => None
    })
    .toMap

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
    case e: IllegalArgumentException => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"${mailboxId} is not a mailboxId: ${e.getMessage}")), None)
    case _ => MailboxSetError.serverFail(Some(SetErrorDescription(exception.getMessage)), None)
  }
}
case class DeletionResults(results: Seq[DeletionResult]) {
  def destroyed: Seq[DeletionSuccess] =
    results.flatMap(result => result match {
      case success: DeletionSuccess => Some(success)
      case _ => None
    })

  def retrieveErrors: Map[UnparsedMailboxId, MailboxSetError] =
    results.flatMap(result => result match {
      case failure: DeletionFailure => Some(failure.mailboxId, failure.asMailboxSetError)
      case _ => None
    })
    .toMap
}

class MailboxSetMethod @Inject()(serializer: Serializer,
                                 mailboxManager: MailboxManager,
                                 mailboxIdFactory: MailboxId.Factory,
                                 metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("Mailbox/set")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asMailboxSetRequest(invocation.arguments)
        .flatMap(mailboxSetRequest => {
          val (unparsableCreateRequests, createRequests) = parseCreateRequests(mailboxSetRequest)
          for {
            creationResults <- createMailboxes(mailboxSession, createRequests, processingContext)
            deletionResults <- deleteMailboxes(mailboxSession, mailboxSetRequest.destroy.getOrElse(Seq()), processingContext)
          } yield createResponse(invocation, mailboxSetRequest, unparsableCreateRequests, creationResults, deletionResults)
        }))
  }

  private def parseCreateRequests(mailboxSetRequest: MailboxSetRequest): (immutable.Iterable[(MailboxCreationId, MailboxSetError)], immutable.Iterable[(MailboxCreationId, MailboxCreationRequest)]) = {
    mailboxSetRequest.create
      .getOrElse(Map.empty)
      .view
      .mapValues(value => Json.fromJson(value)(serializer.mailboxCreationRequest))
      .toMap
      .partitionMap { case (creationId, creationRequestParseResult) =>
        creationRequestParseResult match {
          case JsSuccess(creationRequest, _) => Right((creationId, creationRequest))
          case JsError(errors) => Left(creationId, mailboxSetError(errors))
        }
      }
  }

  private def mailboxSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): MailboxSetError =
    errors.head match {
      case (path, Seq()) => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"'$path' property in mailbox object is not valid")), None)
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => MailboxSetError("invalidArguments", Some(SetErrorDescription(s"Missing '$path' property in mailbox object")), None)
      case (path, _) => MailboxSetError.invalidArgument(Some(SetErrorDescription(s"Unknown error on property '$path'")), None)
    }

  private def deleteMailboxes(mailboxSession: MailboxSession, deleteRequests: immutable.Iterable[UnparsedMailboxId], processingContext: ProcessingContext): SMono[DeletionResults] = {
    SFlux.fromIterable(deleteRequests)
      .flatMap(id => delete(mailboxSession, processingContext, id)
        .onErrorRecover(e => DeletionFailure(id, e)))
      .collectSeq()
      .map(DeletionResults)
  }

  private def delete(mailboxSession: MailboxSession, processingContext: ProcessingContext, id: UnparsedMailboxId): SMono[DeletionResult] = {
    processingContext.resolveMailboxId(id, mailboxIdFactory) match {
      case Right(mailboxId) => SMono.fromCallable(() => delete(mailboxSession, mailboxId))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[DeletionResult](DeletionSuccess(mailboxId)))
      case Left(e) => SMono.raiseError(e)
    }
  }

  private def delete(mailboxSession: MailboxSession, id: MailboxId): Mailbox = {
    val mailbox = mailboxManager.getMailbox(id, mailboxSession)
    if (mailbox.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).hasNext) {
      throw MailboxHasMailException(id)
    }
    if (mailboxManager.hasChildren(mailbox.getMailboxPath, mailboxSession)) {
      throw MailboxHasChildException(id)
    }
    mailboxManager.deleteMailbox(id, mailboxSession)
  }

  private def createMailboxes(mailboxSession: MailboxSession,
                              createRequests: immutable.Iterable[(MailboxCreationId, MailboxCreationRequest)],
                              processingContext: ProcessingContext): SMono[CreationResults] = {
    SFlux.fromIterable(createRequests).flatMap {
      case (mailboxCreationId: MailboxCreationId, mailboxCreationRequest: MailboxCreationRequest) =>
        SMono.fromCallable(() => {
          createMailbox(mailboxSession, mailboxCreationId, mailboxCreationRequest, processingContext)
        }).subscribeOn(Schedulers.elastic())
    }
      .collectSeq()
      .map(CreationResults)
  }

  private def createMailbox(mailboxSession: MailboxSession,
                            mailboxCreationId: MailboxCreationId,
                            mailboxCreationRequest: MailboxCreationRequest,
                            processingContext: ProcessingContext): CreationResult = {
    resolvePath(mailboxSession, mailboxCreationRequest)
      .map(path => createMailbox(mailboxSession, mailboxCreationId, processingContext, path))
      .fold(e => CreationFailure(mailboxCreationId, e), r => r)
  }

  private def createMailbox(mailboxSession: MailboxSession,
                            mailboxCreationId: MailboxCreationId,
                            processingContext: ProcessingContext,
                            path: MailboxPath): CreationResult = {
    try {
      //can safely do a get as the Optional is empty only if the mailbox name is empty which is forbidden by the type constraint on MailboxName
      val mailboxId = mailboxManager.createMailbox(path, mailboxSession).get()
      recordCreationIdInProcessingContext(mailboxCreationId, processingContext, mailboxId)
      CreationSuccess(mailboxCreationId, mailboxId)
    } catch {
      case error: Exception => CreationFailure(mailboxCreationId, error)
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
                          mailboxCreationRequest: MailboxCreationRequest): Either[Exception, MailboxPath] = {
    mailboxCreationRequest.parentId
      .map(parentId => for {
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

  private def createResponse(invocation: Invocation, mailboxSetRequest: MailboxSetRequest,
                             unparsableCreateRequests: immutable.Iterable[(MailboxCreationId, MailboxSetError)],
                             creationResults: CreationResults, deletionResults: DeletionResults): Invocation = {
    val created: Map[MailboxCreationId, MailboxId] = creationResults.retrieveCreated

    Invocation(methodName, Arguments(serializer.serialize(MailboxSetResponse(
      mailboxSetRequest.accountId,
      oldState = None,
      newState = State.INSTANCE,
      destroyed = Some(deletionResults.destroyed.map(_.mailboxId)).filter(_.nonEmpty),
      created = Some(created.map(creation => (creation._1, MailboxCreationResponse(
        id = creation._2,
        role = None,
        totalEmails = TotalEmails(0L),
        unreadEmails = UnreadEmails(0L),
        totalThreads = TotalThreads(0L),
        unreadThreads = UnreadThreads(0L),
        myRights = MailboxRights.FULL,
        rights = None,
        namespace = None,
        quotas = None,
        isSubscribed = IsSubscribed(true)
      )))).filter(_.nonEmpty),
      notCreated = Some(unparsableCreateRequests.toMap ++ creationResults.retrieveErrors).filter(_.nonEmpty),
      updated = None,
      notUpdated = None,
      notDestroyed = Some(deletionResults.retrieveErrors).filter(_.nonEmpty)
    )).as[JsObject]), invocation.methodCallId)
  }

  private def asMailboxSetRequest(arguments: Arguments): SMono[MailboxSetRequest] = {
    serializer.deserializeMailboxSetRequest(arguments.value) match {
      case JsSuccess(mailboxSetRequest, _) => SMono.just(mailboxSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }
}
