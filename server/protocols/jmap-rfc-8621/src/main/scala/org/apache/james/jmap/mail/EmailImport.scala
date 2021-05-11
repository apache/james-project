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
 * ***************************************************************/

package org.apache.james.jmap.mail

import org.apache.james.jmap.core.{AccountId, SetError, UTCDate, UuidState}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.model.MailboxId

case class EmailImportRequest(accountId: AccountId,
                              emails: Map[EmailCreationId, EmailImport]) extends WithAccountId

case class EmailImport(blobId: BlobId,
                       mailboxIds: MailboxIds,
                       keywords: Keywords,
                       receivedAt: UTCDate) {
  def validate: Either[IllegalArgumentException, ValidatedEmailImport] = mailboxIds match {
    case MailboxIds(List(mailboxId)) => scala.Right(ValidatedEmailImport(blobId, mailboxId, keywords, receivedAt))
    case _ => Left(new IllegalArgumentException("Email/import so far only supports a single mailboxId"))
  }
}

case class ValidatedEmailImport(blobId: BlobId,
                                mailboxId: MailboxId,
                                keywords: Keywords,
                                receivedAt: UTCDate)

case class EmailImportResponse(accountId: AccountId,
                               oldState: UuidState,
                               newState: UuidState,
                               created: Option[Map[EmailCreationId, EmailCreationResponse]],
                               notCreated: Option[Map[EmailCreationId, SetError]])

