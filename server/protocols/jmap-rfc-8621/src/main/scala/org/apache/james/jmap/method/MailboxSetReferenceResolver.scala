/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import org.apache.james.jmap.core.{ClientId, IdOrCreationReference, ServerId}
import org.apache.james.jmap.mail.{InvalidUpdateException, MailboxGet, MailboxPatchObject, MailboxSetRequest, UnparsedMailboxId}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.JsString

object MailboxSetReferenceResolver {
  case class UnresolvedMailboxReferenceException(reference: String, reason: String)
    extends IllegalArgumentException(s"Unable to resolve mailbox reference '$reference': $reason")

  private def parse(serverId: ServerId, factory: MailboxId.Factory): Either[Throwable, MailboxId] =
    MailboxGet.parseString(factory)(serverId.value.value).toEither

  def patch(patch: MailboxPatchObject, context: ProcessingContext): Either[Exception, MailboxPatchObject] =
    patch.value.get("parentId") match {
      case Some(JsString(value)) if value.startsWith("#") =>
        IdOrCreationReference.validate(value).flatMap(context.resolveId)
          .map(id => MailboxPatchObject(patch.value.updated("parentId", JsString(id.value.value))))
          .left.map(e => InvalidUpdateException("parentId", e.getMessage))
      case _ => Right(patch)
    }

  def validateUpdateTargets(request: MailboxSetRequest, context: ProcessingContext): Either[IllegalArgumentException, Unit] = {
    val localCreationIds = request.create.toSeq.flatMap(_.keys).map(id => ClientId(id.id)).toSet
    val knownTargets = request.update.toSeq.flatMap(_.keys).flatMap { original =>
      // A successful local create may replace the incoming mapping.
      val mayBeReplaced = original.id.creationId.exists(localCreationIds.contains)
      if (mayBeReplaced) {
        None
      } else {
        context.resolveId(original.id).toOption.map(_ -> original)
      }
    }
    val conflictingTargets = knownTargets.groupMap(_._1)(_._2).values.filter(_.size > 1)
    conflictingTargets.minByOption(_.map(_.id.value).min).map(conflictError).toLeft(())
  }

  def updates(request: MailboxSetRequest, context: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Seq[(UnparsedMailboxId, Either[Throwable, (MailboxId, MailboxPatchObject)])] = {
    val targets = request.update.toSeq.flatMap(_.toSeq).map { case (original, originalPatch) =>
      (original, context.resolveId(original.id), originalPatch)
    }
    val unresolved = targets.collect {
      case (original, Left(error), _) =>
        original -> Left(UnresolvedMailboxReferenceException(original.id.value, error.getMessage))
    }
    val updatesByServerId = targets.collect {
      case (original, Right(serverId), originalPatch) => serverId -> (original, originalPatch)
    }.groupMap(_._1)(_._2)

    val resolved = updatesByServerId.toSeq.map { case (serverId, updates) =>
      val errorKey = UnparsedMailboxId(serverId.value)
      val result = if (updates.size > 1) {
        Left(conflictError(updates.map(_._1)))
      } else {
        val (_, originalPatch) = updates.head
        for {
          mailboxId <- parse(serverId, mailboxIdFactory)
          resolvedPatch <- patch(originalPatch, context)
        } yield (mailboxId, resolvedPatch)
      }
      errorKey -> result
    }
    unresolved ++ resolved
  }

  private def conflictError(originals: Seq[UnparsedMailboxId]): IllegalArgumentException = {
    val sampleKeys = originals.map(_.id.value).sorted.take(2).map(key => s"'$key'").mkString(", ")
    val remainingKeyCount = originals.size - 2
    val additionalKeys = if (remainingKeyCount > 0) s" (and $remainingKeyCount more)" else ""
    new IllegalArgumentException(s"Multiple update keys resolve to the same mailbox: $sampleKeys$additionalKeys")
  }

  def deletions(request: MailboxSetRequest, context: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Seq[(UnparsedMailboxId, Either[Throwable, MailboxId])] =
    request.destroy.toSeq.flatten.distinct.map { original =>
      context.resolveId(original.id) match {
        case Left(error) => Left(original -> UnresolvedMailboxReferenceException(original.id.value, error.getMessage))
        case Right(serverId) => Right(serverId)
      }
    }.distinct.map {
      case Left((original, error)) => original -> Left(error)
      case Right(serverId) =>
        val errorKey = UnparsedMailboxId(serverId.value)
        errorKey -> parse(serverId, mailboxIdFactory)
    }
}
