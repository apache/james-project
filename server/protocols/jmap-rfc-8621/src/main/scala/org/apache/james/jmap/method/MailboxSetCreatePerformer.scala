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
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{IsSubscribed, MailboxCreationId, MailboxCreationRequest, MailboxCreationResponse, MailboxRights, MailboxSetRequest, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads}
import org.apache.james.jmap.method.MailboxSetCreatePerformer.asSetError
import org.apache.james.jmap.routes.{CreationResults, ProcessingContext, SessionSupplier}
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
  def asSetError(exception: Throwable): SetError = exception match {
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
                      supportSharedMailbox: Boolean): SMono[CreationResults[MailboxCreationId, MailboxCreationResponse]] =
    processingContext.processCreations[MailboxCreationId, MailboxCreationResponse](
      mailboxSetRequest.create.getOrElse(Map.empty), Set("parentId"), validateCreate(mailboxSession))(
      _.id, _.id.serialize()) { (_, json) =>
      SMono.fromCallable(() => parseCreate(json)
        .flatMap(request => resolvePath(mailboxSession, request, supportSharedMailbox)
          .flatMap(path => createMailbox(mailboxSession, path, request)))
        .left.map(asSetError))
    }.subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def validateCreate(session: MailboxSession)(json: JsObject): Either[SetError, Unit] =
    MailboxCreationRequest.validateProperties(json).left.map(_.setError).flatMap { _ =>
      (json \ "name").asOpt[String].filter(_.contains(session.getPathDelimiter))
        .map(name => Left(asSetError(new MailboxNameException(
          s"The mailbox '$name' contains an illegal character: '${session.getPathDelimiter}'"))))
        .getOrElse(Right(()))
    }

  private def parseCreate(jsObject: JsObject): Either[Exception, MailboxCreationRequest] =
    Json.fromJson(jsObject)(serializer.mailboxCreationRequest) match {
      case JsSuccess(request, _) => Right(request)
      case JsError(errors) if errors.forall(_._1 == (JsPath \ "parentId")) =>
        Left(MailboxCreationParseException(SetError.invalidProperties(SetErrorDescription("Invalid parentId"), Some(Properties("parentId")))))
      case JsError(errors) => Left(MailboxCreationParseException(mailboxSetError(errors)))
    }

  private def resolvePath(mailboxSession: MailboxSession,
                          request: MailboxCreationRequest,
                          supportSharedMailbox: Boolean): Either[Exception, MailboxPath] = {
    request.parentId
      .map(maybeParentId => for {
        parentId <- Try(mailboxIdFactory.fromString(maybeParentId.id))
          .toEither
          .left
          .map(_ => MailboxCreationParseException(SetError.invalidProperties(
            SetErrorDescription(s"Invalid parentId: ${maybeParentId.id}"), Some(Properties("parentId")))))
        parentPathPreAssert <- retrievePath(parentId, mailboxSession)
        parentPath <- validateCapabilityIfSharedMailbox(mailboxSession, parentId, supportSharedMailbox).apply(parentPathPreAssert)
      } yield {
        parentPath.child(request.name, mailboxSession.getPathDelimiter)
      })
      .getOrElse(Right(MailboxPath.forUser(mailboxSession.getUser, request.name)))
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
