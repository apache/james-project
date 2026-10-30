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
      .left.map(_ => new MailboxNotFoundException(id.id.value))
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
    val targets = request.update.toSeq.flatMap(_.toSeq).map { case (id, patch) => (id, target(id, context, mailboxIdFactory), patch) }
    val counts = targets.flatMap(_._2.toOption).groupMapReduce(identity)(_ => 1)(_ + _)
    targets.map { case (original, resolved, originalPatch) =>
      original -> resolved.flatMap(id =>
        if (counts(id) > 1) Left(new IllegalArgumentException("Multiple update keys resolve to the same mailbox"))
        else patch(originalPatch, context).map(id -> _))
    }
  }

  def deletions(request: MailboxSetRequest, context: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Seq[(Seq[UnparsedMailboxId], Either[Throwable, MailboxId])] = {
    val targets = request.destroy.toSeq.flatten.distinct.map(id => id -> target(id, context, mailboxIdFactory))
    val unresolved = targets.collect { case (original, Left(error)) => Seq(original) -> Left(error) }
    val resolved = targets.collect { case (original, Right(id)) => id -> original }
      .groupMap(_._1)(_._2).toSeq.map { case (id, originals) => originals -> Right(id) }
    unresolved ++ resolved
  }
}
