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

package org.apache.james.jmap.mail

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail.MailboxSetRequest.MailboxCreationId
import org.apache.james.jmap.model.AccountId
import org.apache.james.jmap.model.State.State
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.JsObject

case class MailboxSetRequest(accountId: AccountId,
                             ifInState: Option[State],
                             create: Option[Map[MailboxCreationId, JsObject]],
                             update: Option[Map[MailboxId, MailboxPatchObject]],
                             destroy: Option[Seq[MailboxId]],
                             onDestroyRemoveEmails: Option[RemoveEmailsOnDestroy])

object MailboxSetRequest {
  type MailboxCreationId = String Refined NonEmpty
}

case class RemoveEmailsOnDestroy(value: Boolean) extends AnyVal
case class MailboxCreationRequest(name: MailboxName, parentId: Option[MailboxId])

case class MailboxPatchObject(value: Map[String, JsObject])

case class MailboxSetResponse(accountId: AccountId,
                              oldState: Option[State],
                              newState: State,
                              created: Option[Map[MailboxCreationId, MailboxCreationResponse]],
                              updated: Option[Map[MailboxId, MailboxUpdateResponse]],
                              destroyed: Option[Seq[MailboxId]],
                              notCreated: Option[Map[MailboxCreationId, MailboxSetError]],
                              notUpdated: Option[Map[MailboxId, MailboxSetError]],
                              notDestroyed: Option[Map[MailboxId, MailboxSetError]])

case class MailboxSetError(`type`: SetErrorType, description: Option[SetErrorDescription], properties: Option[Properties])

case class MailboxCreationResponse(id: MailboxId,
                                   role: Option[Role],//TODO see if we need to return this, if a role is set by the server during creation
                                   totalEmails: TotalEmails,
                                   unreadEmails: UnreadEmails,
                                   totalThreads: TotalThreads,
                                   unreadThreads: UnreadThreads,
                                   myRights: MailboxRights,
                                   rights: Option[Rights],//TODO display only if RightsExtension and if some rights are set by the server during creation
                                   namespace: Option[MailboxNamespace], //TODO display only if RightsExtension
                                   quotas: Option[Quotas],//TODO display only if QuotasExtension
                                   isSubscribed: IsSubscribed
                                  )

case class MailboxUpdateResponse(value: JsObject)