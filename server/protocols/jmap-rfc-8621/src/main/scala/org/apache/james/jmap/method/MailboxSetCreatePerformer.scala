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

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Properties, ServerId, SetError}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{IsSubscribed, MailboxCreationId, MailboxCreationRequest, MailboxCreationResponse, MailboxRights, MailboxSetRequest, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads}
import org.apache.james.jmap.method.MailboxSetCreatePerformer.{MailboxCreationFailure, MailboxCreationResult, MailboxCreationResults, MailboxCreationSuccess}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.jmap.utils.quotas.QuotaLoaderWithPreloadedDefaultFactory
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

object MailboxSetCreatePerformer {
  sealed trait MailboxCreationResult {
    def mailboxCreationId: MailboxCreationId
  }
  case class MailboxCreationSuccess(mailboxCreationId: MailboxCreationId, mailboxCreationResponse: MailboxCreationResponse) extends MailboxCreationResult
  case class MailboxCreationFailure(mailboxCreationId: MailboxCreationId, exception: Exception) extends MailboxCreationResult {
    def asMailboxSetError: SetError = exception match {
      case e: MailboxNotFoundException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("parentId")))
      case e: MailboxExistsException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("name")))
      case e: MailboxNameException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("name")))
      case e: MailboxCreationParseException => e.setError
      case _: InsufficientRightsException => SetError.forbidden(SetErrorDescription("Insufficient rights"), Some(Properties("parentId")))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class MailboxCreationResults(created: Seq[MailboxCreationResult]) {
    def retrieveCreated: Map[MailboxCreationId, MailboxCreationResponse] = created
      .flatMap(result => result match {
        case success: MailboxCreationSuccess => Some(success.mailboxCreationId, success.mailboxCreationResponse)
        case _ => None
      })
      .toMap
      .map(creation => (creation._1, creation._2))

    def retrieveErrors: Map[MailboxCreationId, SetError] = created
      .flatMap(result => result match {
        case failure: MailboxCreationFailure => Some(failure.mailboxCreationId, failure.asMailboxSetError)
        case _ => None
      })
      .toMap
  }
}

class MailboxSetCreatePerformer @Inject()(serializer: MailboxSerializer,
                                          mailboxManager: MailboxManager,
                                          subscriptionManager: SubscriptionManager,
                                          mailboxIdFactory: MailboxId.Factory,
                                          quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier) {



  def createMailboxes(mailboxSession: MailboxSession,
                              mailboxSetRequest: MailboxSetRequest,
                              processingContext: ProcessingContext): SMono[(MailboxCreationResults, ProcessingContext)] = {
    SFlux.fromIterable(mailboxSetRequest.create
      .getOrElse(Map.empty)
      .view)
      .fold((MailboxCreationResults(Nil), processingContext)){
        (acc : (MailboxCreationResults, ProcessingContext), elem: (MailboxCreationId, JsObject)) => {
          val (mailboxCreationId, jsObject) = elem
          val (creationResult, updatedProcessingContext) = createMailbox(mailboxSession, mailboxCreationId, jsObject, acc._2)
          (MailboxCreationResults(acc._1.created :+ creationResult), updatedProcessingContext)
        }
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
  }

  private def createMailbox(mailboxSession: MailboxSession,
                            mailboxCreationId: MailboxCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): (MailboxCreationResult, ProcessingContext) = {
    parseCreate(jsObject)
      .flatMap(mailboxCreationRequest => resolvePath(mailboxSession, mailboxCreationRequest)
        .flatMap(path => createMailbox(mailboxSession = mailboxSession,
          path = path,
          mailboxCreationRequest = mailboxCreationRequest)))
      .flatMap(creationResponse => recordCreationIdInProcessingContext(mailboxCreationId, processingContext, creationResponse.id)
        .map(context => (creationResponse, context)))
      .fold(e => (MailboxCreationFailure(mailboxCreationId, e), processingContext),
        creationResponseWithUpdatedContext => {
          (MailboxCreationSuccess(mailboxCreationId, creationResponseWithUpdatedContext._1), creationResponseWithUpdatedContext._2)
        })
  }

  private def parseCreate(jsObject: JsObject): Either[MailboxCreationParseException, MailboxCreationRequest] =
    MailboxCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.mailboxCreationRequest) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(MailboxCreationParseException(mailboxSetError(errors)))
      })

  private def resolvePath(mailboxSession: MailboxSession,
                          mailboxCreationRequest: MailboxCreationRequest): Either[Exception, MailboxPath] = {
    if (mailboxCreationRequest.name.value.contains(mailboxSession.getPathDelimiter)) {
      return Left(new MailboxNameException(s"The mailbox '${mailboxCreationRequest.name.value}' contains an illegal character: '${mailboxSession.getPathDelimiter}'"))
    }
    mailboxCreationRequest.parentId
      .map(maybeParentId => for {
        parentId <- Try(mailboxIdFactory.fromString(maybeParentId.id))
          .toEither
          .left
          .map(e => new IllegalArgumentException(e.getMessage, e))
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

  private def recordCreationIdInProcessingContext(mailboxCreationId: MailboxCreationId,
                                                  processingContext: ProcessingContext,
                                                  mailboxId: MailboxId): Either[IllegalArgumentException, ProcessingContext] =
    for {
      serverAssignedId <- Id.validate(mailboxId.serialize())
    } yield {
      processingContext.recordCreatedId(ClientId(mailboxCreationId.id), ServerId(serverAssignedId))
    }

  private def mailboxSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError = standardError(errors)

  private def createMailbox(mailboxSession: MailboxSession,
                            path: MailboxPath,
                            mailboxCreationRequest: MailboxCreationRequest): Either[Exception, MailboxCreationResponse] = {
    try {
      //can safely do a get as the Optional is empty only if the mailbox name is empty which is forbidden by the type constraint on MailboxName
      val mailboxId = mailboxManager.createMailbox(path, mailboxSession).get()

      val defaultSubscribed = IsSubscribed(true)
      if (mailboxCreationRequest.isSubscribed.getOrElse(defaultSubscribed).value) {
        subscriptionManager.subscribe(mailboxSession, path)
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

}
