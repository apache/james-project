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

package org.apache.james.jmap.routes

import org.apache.james.jmap.mail.MailboxSetRequest.UnparsedMailboxId
import org.apache.james.jmap.model.{ClientId, Id, ServerId}
import org.apache.james.mailbox.model.MailboxId

import scala.collection.mutable

class ProcessingContext {
 private val creationIds: mutable.Map[ClientId, ServerId] = mutable.Map()

 def recordCreatedId(clientId: ClientId, serverId: ServerId): Unit = creationIds.put(clientId, serverId)
 private def retrieveServerId(clientId: ClientId): Option[ServerId] = creationIds.get(clientId)

 def resolveMailboxId(unparsedMailboxId: UnparsedMailboxId, mailboxIdFactory: MailboxId.Factory): Either[IllegalArgumentException, MailboxId] =
  Id.validate(unparsedMailboxId.value)
      .flatMap(id => resolveServerId(ClientId(id)))
      .flatMap(serverId => parseMailboxId(mailboxIdFactory, serverId))

 private def parseMailboxId(mailboxIdFactory: MailboxId.Factory, serverId: ServerId) =
  try {
   Right(mailboxIdFactory.fromString(serverId.value.value))
  } catch {
   case e: IllegalArgumentException => Left(e)
  }

 private def resolveServerId(id: ClientId): Either[IllegalArgumentException, ServerId] =
  id.retrieveOriginalClientId
    .map(maybePreviousClientId => maybePreviousClientId.flatMap(previousClientId => retrieveServerId(previousClientId)
      .map(serverId => Right(serverId))
      .getOrElse(Left[IllegalArgumentException, ServerId](new IllegalArgumentException(s"$id was not used in previously defined creationIds")))))
    .getOrElse(Right(ServerId(id.value)))
}
