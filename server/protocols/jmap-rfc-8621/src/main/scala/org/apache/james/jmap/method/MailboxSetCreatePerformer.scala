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
import jakarta.inject.Inject
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, IdOrCreationReference, Properties, ServerId, SetError}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{IsSubscribed, MailboxCreationId, MailboxCreationRequest, MailboxCreationResponse, MailboxRights, MailboxSetRequest, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, UnparsedMailboxId}
import org.apache.james.jmap.method.MailboxSetCreatePerformer.{MailboxCreationFailure, MailboxCreationResults, MailboxCreationSuccess}
import org.apache.james.jmap.routes.{CreateProcessor, ProcessingContext, SessionSupplier}
import org.apache.james.jmap.utils.quotas.QuotaLoaderWithPreloadedDefaultFactory
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.SMono

import scala.util.Try

object MailboxSetCreatePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[MailboxSetCreatePerformer])
  sealed trait MailboxCreationResult {
    def mailboxCreationId: MailboxCreationId
  }
  case class MailboxCreationSuccess(mailboxCreationId: MailboxCreationId, mailboxCreationResponse: MailboxCreationResponse) extends MailboxCreationResult
  case class MailboxCreationFailure(mailboxCreationId: MailboxCreationId, exception: Exception) extends MailboxCreationResult {
    def asMailboxSetError: SetError = exception match {
      case e: MailboxNotFoundException =>
        LOGGER.info("Can't create mailbox: Mailbox not found: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("parentId")))
      case e: MailboxExistsException =>
        LOGGER.info("Mailbox already exists: {}", e.getMailboxName)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("name")))
      case e: MailboxNameException =>
        LOGGER.info("Invalid mailbox name: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("name")))
      case e: MailboxCreationParseException =>
        LOGGER.info("Failed to parse mailbox creation request", e)
        e.setError
      case e: InsufficientRightsException =>
        LOGGER.info("Insufficient rights to create a mailbox", e)
        SetError.forbidden(SetErrorDescription("Insufficient rights"), Some(Properties("parentId")))
      case e =>
        LOGGER.error("Failed to create mailbox", e)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
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
                      processingContext: ProcessingContext,
                      supportSharedMailbox: Boolean): SMono[(MailboxCreationResults, ProcessingContext)] = {
    val createsByClientId = mailboxSetRequest.create.getOrElse(Map.empty).map { case (id, json) =>
      ClientId(id.id) -> json
    }

    CreateProcessor.process(createsByClientId, processingContext)(
      dependencies = (json: JsObject) => parentReference(json).toSet,
      create = (json: JsObject, context: ProcessingContext) => SMono.fromCallable(() =>
        createMailbox(mailboxSession, json, context, supportSharedMailbox)),
      invalidDependency = (_: JsObject) => invalidParentReference)
      .map { case (results, context) => (toMailboxCreationResults(results), context) }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
  }

  private def toMailboxCreationResults(results: Map[ClientId, Either[Exception, MailboxCreationResponse]]): MailboxCreationResults = {
    val creations = results.toSeq.map { case (id, result) =>
      val mailboxCreationId = MailboxCreationId(id.value)
      result match {
        case Left(error) => MailboxCreationFailure(mailboxCreationId, error)
        case Right(response) => MailboxCreationSuccess(mailboxCreationId, response)
      }
    }
    MailboxCreationResults(creations)
  }

  private def parentReference(json: JsObject): Option[ClientId] =
    (json \ "parentId").asOpt[IdOrCreationReference]
      .flatMap(_.creationId)

  private def invalidParentReference: MailboxCreationParseException =
    MailboxCreationParseException(SetError.invalidProperties(
      SetErrorDescription("parentId refers to an unknown, failed or cyclic creation"), Some(Properties("parentId"))))

  private def createMailbox(mailboxSession: MailboxSession,
                            jsObject: JsObject,
                            processingContext: ProcessingContext,
                            supportSharedMailbox: Boolean): Either[Exception, (MailboxCreationResponse, ServerId)] =
    for {
      request <- parseCreate(jsObject)
      resolvedRequest <- resolveParent(request, processingContext)
      path <- resolvePath(mailboxSession, resolvedRequest, supportSharedMailbox)
      response <- createMailbox(mailboxSession, path, resolvedRequest)
      id <- Id.validate(response.id.serialize())
    } yield (response, ServerId(id))

  private def resolveParent(request: MailboxCreationRequest, context: ProcessingContext): Either[Exception, MailboxCreationRequest] =
    request.parentId.flatMap(_.id.creationId) match {
      case None => Right(request)
      case Some(parentCreationId) =>
        context.retrieveServerId(parentCreationId)
          .toRight(invalidParentReference)
          .map(parentId => request.copy(parentId = Some(UnparsedMailboxId(parentId.value))))
    }

  private def parseCreate(jsObject: JsObject): Either[MailboxCreationParseException, MailboxCreationRequest] =
    MailboxCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.mailboxCreationRequest) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(MailboxCreationParseException(mailboxSetError(errors)))
      })

  private def resolvePath(mailboxSession: MailboxSession,
                          mailboxCreationRequest: MailboxCreationRequest,
                          supportSharedMailbox: Boolean): Either[Exception, MailboxPath] = {
    if (mailboxCreationRequest.name.value.contains(mailboxSession.getPathDelimiter)) {
      return Left(new MailboxNameException(s"The mailbox '${mailboxCreationRequest.name.value}' contains an illegal character: '${mailboxSession.getPathDelimiter}'"))
    }
    mailboxCreationRequest.parentId
      .map(maybeParentId => for {
        parentId <- Try(mailboxIdFactory.fromString(maybeParentId.id.value))
          .toEither
          .left
          .map(e => new IllegalArgumentException(e.getMessage, e))
        parentPathPreAssert <- retrievePath(parentId, mailboxSession)
        parentPath <- validateCapabilityIfSharedMailbox(mailboxSession, parentId, supportSharedMailbox).apply(parentPathPreAssert)
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

  private def validateCapabilityIfSharedMailbox(mailboxSession: MailboxSession, id: MailboxId, supportSharedMailbox: Boolean): MailboxPath => Either[Exception, MailboxPath] =
    mailboxPath => if (!mailboxPath.belongsTo(mailboxSession) && !supportSharedMailbox) {
      Left(new MailboxNotFoundException(id))
    } else {
      Right(mailboxPath)
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
