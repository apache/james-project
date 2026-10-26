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
import org.apache.james.jmap.core.{ClientId, Id, Properties, ServerId, SetError}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{IsSubscribed, MailboxCreationId, MailboxCreationRequest, MailboxCreationResponse, MailboxRights, MailboxSetRequest, SortOrder, TotalEmails, TotalThreads, UnparsedMailboxId, UnreadEmails, UnreadThreads}
import org.apache.james.jmap.method.MailboxSetCreatePerformer.{MailboxCreationFailure, MailboxCreationResult, MailboxCreationResults, MailboxCreationSuccess, ParsedMailboxCreation, invalidParentId, orderCreations}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.jmap.utils.quotas.QuotaLoaderWithPreloadedDefaultFactory
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.SMono

import scala.collection.mutable
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
        SetError.invalidProperties(SetErrorDescription(e.getMessage), Some(Properties("parentId")))
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
  private case class ParsedMailboxCreation(request: MailboxCreationRequest, parentCreationId: Option[MailboxCreationId])

  private[method] case class CreationOrder(ordered: Seq[MailboxCreationId], unresolved: Set[MailboxCreationId])

  private[method] def orderCreations(dependencies: Map[MailboxCreationId, Option[MailboxCreationId]]): CreationOrder = {
    val children = dependencies.toSeq.flatMap { case (id, parent) =>
      parent.filter(dependencies.contains).map(_ -> id)
    }.groupMap(_._1)(_._2)
    val waiting = mutable.Set.from(children.valuesIterator.flatten)
    val ready = mutable.Queue.from(dependencies.keysIterator.filterNot(waiting.contains))
    val ordered = Vector.newBuilder[MailboxCreationId]
    while (ready.nonEmpty) {
      val id = ready.dequeue()
      ordered += id
      children.getOrElse(id, Seq.empty).foreach { child =>
        waiting.remove(child)
        ready.enqueue(child)
      }
    }
    CreationOrder(ordered.result(), waiting.toSet)
  }

  private def invalidParentId(description: String): MailboxCreationParseException =
    MailboxCreationParseException(SetError.invalidProperties(SetErrorDescription(description), Some(Properties("parentId"))))

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
                      supportSharedMailbox: Boolean): SMono[(MailboxCreationResults, ProcessingContext)] =
    SMono.fromCallable(() => {
      val creations = mailboxSetRequest.create.getOrElse(Map.empty)
        .map { case (id, json) => id -> parseCreate(json) }
      val dependencies = creations.map { case (id, creation) =>
        val parent = creation.toOption.flatMap(_.parentCreationId)
          .filter(parent => processingContext.retrieveServerId(ClientId(parent.id)).isEmpty)
        id -> parent
      }
      val order = orderCreations(dependencies)
      val (results, context) = order.ordered.foldLeft((Vector.empty[MailboxCreationResult], processingContext)) {
        case ((results, context), id) =>
          val (result, updatedContext) = createMailbox(mailboxSession, id, creations(id), context, supportSharedMailbox)
          (results :+ result, updatedContext)
      }
      val unresolved = order.unresolved.toSeq.map(id => MailboxCreationFailure(id,
        invalidParentId("parentId refers to a cyclic mailbox creation dependency")))
      (MailboxCreationResults(results ++ unresolved), context)
    })
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def createMailbox(mailboxSession: MailboxSession,
                            mailboxCreationId: MailboxCreationId,
                            creationRequest: Either[MailboxCreationParseException, ParsedMailboxCreation],
                            processingContext: ProcessingContext,
                            supportSharedMailbox: Boolean): (MailboxCreationResult, ProcessingContext) = {
    val creation = for {
      parsed <- creationRequest
      path <- resolvePath(mailboxSession, parsed, processingContext, supportSharedMailbox)
      response <- createMailbox(mailboxSession, path, parsed.request)
      context <- recordCreationIdInProcessingContext(mailboxCreationId, processingContext, response.id)
    } yield (response, context)

    creation.fold(e => (MailboxCreationFailure(mailboxCreationId, e), processingContext),
      { case (response, context) => (MailboxCreationSuccess(mailboxCreationId, response), context) })
  }

  private def parseCreate(jsObject: JsObject): Either[MailboxCreationParseException, ParsedMailboxCreation] =
    MailboxCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => (validJsObject \ "parentId").asOpt[String].filter(_.startsWith("#")) match {
        case Some(reference) =>
          for {
            id <- Id.validate(reference.substring(1)).left.map(_ => invalidParentId("Invalid parentId"))
            request <- parseCreationRequest(validJsObject - "parentId")
          } yield ParsedMailboxCreation(request, Some(MailboxCreationId(id)))
        case None => parseCreationRequest(validJsObject).map(request => ParsedMailboxCreation(request, None))
      })

  private def parseCreationRequest(jsObject: JsObject): Either[MailboxCreationParseException, MailboxCreationRequest] =
    Json.fromJson(jsObject)(serializer.mailboxCreationRequest) match {
      case JsSuccess(request, _) => Right(request)
      case JsError(errors) if errors.forall(_._1 == (JsPath \ "parentId")) => Left(invalidParentId("Invalid parentId"))
      case JsError(errors) => Left(MailboxCreationParseException(mailboxSetError(errors)))
    }

  private def resolveParentId(creation: ParsedMailboxCreation,
                              processingContext: ProcessingContext): Either[MailboxCreationParseException, Option[UnparsedMailboxId]] =
    creation.parentCreationId match {
      case Some(parent) => processingContext.retrieveServerId(ClientId(parent.id))
        .toRight(invalidParentId(s"Unable to resolve parentId: #${parent.id.value}"))
        .map(id => Some(UnparsedMailboxId(id.value)))
      case None => Right(creation.request.parentId)
    }

  private def resolvePath(mailboxSession: MailboxSession,
                          creation: ParsedMailboxCreation,
                          processingContext: ProcessingContext,
                          supportSharedMailbox: Boolean): Either[Exception, MailboxPath] = {
    val request = creation.request
    if (request.name.value.contains(mailboxSession.getPathDelimiter)) {
      return Left(new MailboxNameException(s"The mailbox '${request.name.value}' contains an illegal character: '${mailboxSession.getPathDelimiter}'"))
    }
    resolveParentId(creation, processingContext).flatMap(parentId => parentId
      .map(maybeParentId => for {
        parentId <- Try(mailboxIdFactory.fromString(maybeParentId.id))
          .toEither
          .left
          .map(_ => invalidParentId(s"Invalid parentId: ${maybeParentId.id.value}"))
        parentPathPreAssert <- retrievePath(parentId, mailboxSession)
        parentPath <- validateCapabilityIfSharedMailbox(mailboxSession, parentId, supportSharedMailbox).apply(parentPathPreAssert)
      } yield {
        parentPath.child(request.name, mailboxSession.getPathDelimiter)
      })
      .getOrElse(Right(MailboxPath.forUser(mailboxSession.getUser, request.name))))
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
