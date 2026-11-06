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

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.IdOrCreationReference
import org.apache.james.jmap.mail.{InvalidUpdateException, MailboxGet, MailboxPatchObject, MailboxSetRequest, UnparsedMailboxId}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.JsString

/** Resolve only Mailbox/set reference positions, retaining original keys for errors.
  * Compare parsed MailboxIds: different ID strings may denote the same mailbox.
  * Keep operations in sequences: resolving aliases into a Map would silently drop patches.
  */
object MailboxSetReferenceResolver {
  def target(id: UnparsedMailboxId, context: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Either[Throwable, MailboxId] =
    context.resolveId(id.id)
      .left.map(cause => new MailboxNotFoundException(s"Unable to resolve mailbox reference '${id.id.value}': ${cause.getMessage}"))
      .flatMap(serverId => MailboxGet.parseString(mailboxIdFactory)(serverId.value.value).toEither)

  def patch(patch: MailboxPatchObject, context: ProcessingContext): Either[Exception, MailboxPatchObject] =
    patch.value.get("parentId") match {
      case Some(JsString(value)) if value.startsWith("#") =>
        IdOrCreationReference.validate(value).flatMap(context.resolveId)
          .map(id => MailboxPatchObject(patch.value.updated("parentId", JsString(id.value.value))))
          .left.map(e => InvalidUpdateException("parentId", e.getMessage))
      case _ => Right(patch)
    }

  def updates(request: MailboxSetRequest, context: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Seq[(UnparsedMailboxId, Either[Throwable, (MailboxId, MailboxPatchObject)])] = {
    val resolvedTargets = request.update.toSeq.flatMap(_.toSeq).map { case (original, originalPatch) =>
      (original, target(original, context, mailboxIdFactory), originalPatch)
    }
    val updateCountByMailboxId = resolvedTargets.flatMap(_._2.toOption).groupMapReduce(identity)(_ => 1)(_ + _)

    resolvedTargets.map { case (original, resolvedTarget, originalPatch) =>
      val resolvedUpdate = resolvedTarget.flatMap(mailboxId => {
        val hasMultipleUpdates = updateCountByMailboxId(mailboxId) > 1
        if (hasMultipleUpdates) {
          Left(new IllegalArgumentException("Multiple update keys resolve to the same mailbox"))
        } else {
          patch(originalPatch, context).map(resolvedPatch => mailboxId -> resolvedPatch)
        }
      })
      original -> resolvedUpdate
    }
  }

  def deletions(request: MailboxSetRequest, context: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Seq[(Seq[UnparsedMailboxId], Either[Throwable, MailboxId])] = {
    val resolvedTargets = request.destroy.toSeq.flatten.distinct.map(original => original -> target(original, context, mailboxIdFactory))
    val unresolvedDeletions = resolvedTargets.collect { case (original, Left(error)) => Seq(original) -> Left(error) }
    val originalsByMailboxId = resolvedTargets.collect { case (original, Right(mailboxId)) => mailboxId -> original }
      .groupMap(_._1)(_._2)
    val uniqueDeletions = originalsByMailboxId.toSeq.map { case (mailboxId, originals) => originals -> Right(mailboxId) }
    unresolvedDeletions ++ uniqueDeletions
  }
}
