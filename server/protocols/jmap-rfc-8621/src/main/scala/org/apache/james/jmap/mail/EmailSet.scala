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
package org.apache.james.jmap.mail

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.mail.EmailSet.UnparsedMessageId
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, SetError}
import org.apache.james.mailbox.model.MessageId

import scala.util.Try

object EmailSet {
  type UnparsedMessageIdConstraint = NonEmpty
  type UnparsedMessageId = String Refined UnparsedMessageIdConstraint

  def asUnparsed(messageId: MessageId): UnparsedMessageId = refined.refineV[UnparsedMessageIdConstraint](messageId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => value
  }

  def parse(messageIdFactory: MessageId.Factory)(unparsed: UnparsedMessageId): Try[MessageId] =
    Try(messageIdFactory.fromString(unparsed.value))
}

case class DestroyIds(value: Seq[UnparsedMessageId])

case class EmailSetRequest(accountId: AccountId,
                           destroy: Option[DestroyIds]) extends WithAccountId

case class EmailSetResponse(accountId: AccountId,
                            newState: State,
                            destroyed: Option[DestroyIds],
                            notDestroyed: Option[Map[UnparsedMessageId, SetError]])


