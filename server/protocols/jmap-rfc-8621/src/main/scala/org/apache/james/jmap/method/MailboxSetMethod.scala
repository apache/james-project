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
import org.apache.james.jmap.mail.MailboxSetRequest.MailboxCreationId
import org.apache.james.jmap.mail.{IsSubscribed, MailboxCreationRequest, MailboxCreationResponse, MailboxRights, MailboxSetError, MailboxSetRequest, MailboxSetResponse, SetErrorDescription, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{Invocation, State}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json._
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.collection.immutable

class MailboxSetMethod @Inject() (serializer: Serializer,
                                  mailboxManager: MailboxManager,
                                  metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("Mailbox/set")


  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asMailboxSetRequest(invocation.arguments)
        .flatMap(mailboxSetRequest => {
          val (unparsableCreateRequests, createRequests) =  parseCreateRequests(mailboxSetRequest)
          for {
            created <- createMailboxes(mailboxSession, createRequests)
          } yield createResponse(invocation, mailboxSetRequest, unparsableCreateRequests, created)
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
      case (path, Seq()) => MailboxSetError("invalidArguments", Some(SetErrorDescription(s"'$path' property in mailbox object is not valid")), None)
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => MailboxSetError("invalidArguments", Some(SetErrorDescription(s"Missing '$path' property in mailbox object")), None)
      case (path, _) => MailboxSetError("invalidArguments", Some(SetErrorDescription(s"Unknown error on property '$path'")), None)
    }

  private def createMailboxes(mailboxSession: MailboxSession, createRequests: immutable.Iterable[(MailboxCreationId, MailboxCreationRequest)]): SMono[Map[MailboxCreationId, MailboxId]] = {
    SFlux.fromIterable(createRequests).flatMap {
      case (mailboxCreationId: MailboxCreationId, mailboxCreationRequest: MailboxCreationRequest) => {
        SMono.fromCallable(() => {
          createMailbox(mailboxSession, mailboxCreationId, mailboxCreationRequest)
        }).subscribeOn(Schedulers.elastic())
      }
    }.collectMap(_._1, _._2)
  }

  private def createMailbox(mailboxSession: MailboxSession, mailboxCreationId: MailboxCreationId, mailboxCreationRequest: MailboxCreationRequest) = {
    val path:MailboxPath = if (mailboxCreationRequest.parentId.isEmpty) {
      MailboxPath.forUser(mailboxSession.getUser, mailboxCreationRequest.name)
    } else {
      val parentId: MailboxId = mailboxCreationRequest.parentId.get
      val parentPath: MailboxPath = mailboxManager.getMailbox(parentId, mailboxSession).getMailboxPath
       parentPath.child(mailboxCreationRequest.name, mailboxSession.getPathDelimiter)
    }
    //can safely do a get as the Optional is empty only if the mailbox name is empty which is forbidden by the type constraint on MailboxName
    (mailboxCreationId, mailboxManager.createMailbox(path, mailboxSession).get())
  }

  private def createResponse(invocation: Invocation, mailboxSetRequest: MailboxSetRequest, createErrors: immutable.Iterable[(MailboxCreationId, MailboxSetError)], created: Map[MailboxCreationId, MailboxId]): Invocation = {
    Invocation(methodName, Arguments(serializer.serialize(MailboxSetResponse(
      mailboxSetRequest.accountId,
      oldState = None,
      newState = State.INSTANCE,
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
      notCreated = Some(createErrors.toMap).filter(_.nonEmpty),
      updated = None,
      notUpdated = None,
      destroyed = None,
      notDestroyed = None
    )).as[JsObject]), invocation.methodCallId)
  }

  private def asMailboxSetRequest(arguments: Arguments): SMono[MailboxSetRequest] = {
    serializer.deserializeMailboxSetRequest(arguments.value) match {
      case JsSuccess(mailboxSetRequest, _) => SMono.just(mailboxSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }
}
