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

import org.apache.james.jmap.core.{AccountId, Properties}
import org.apache.james.jmap.mail.EmailGetRequest.MaxBodyValueBytes
import org.apache.james.jmap.mail.MDNParse.UnparsedBlobId
import org.apache.james.jmap.method.WithAccountId


case class EmailParseRequest(accountId: AccountId,
                           blobIds: BlobIds,
                           fetchAllBodyValues: Option[FetchAllBodyValues],
                           fetchTextBodyValues: Option[FetchTextBodyValues],
                           fetchHTMLBodyValues: Option[FetchHTMLBodyValues],
                           maxBodyValueBytes: Option[MaxBodyValueBytes],
                           properties: Option[Properties],
                           bodyProperties: Option[Properties]) extends WithAccountId

object EmailParseResults {
  def notFound(blobId: UnparsedBlobId): EmailParseResults = EmailParseResults(None, Some(EmailParseNotFound(Set(blobId))), None)

  def notFound(blobId: BlobId): EmailParseResults = EmailParseResults(None, Some(EmailParseNotFound(Set(blobId.value))), None)

  def notParsed(blobId: BlobId): EmailParseResults = EmailParseResults(None, None, Some(EmailParseNotParsable(Set(blobId.value))))

  def parsed(blobId: BlobId, emailView: EmailParseView): EmailParseResults = EmailParseResults(Some(Map(blobId -> emailView)), None, None)

  def empty(): EmailParseResults = EmailParseResults(None, None, None)

  def merge(response1: EmailParseResults, response2: EmailParseResults): EmailParseResults = EmailParseResults(
    parsed = (response1.parsed ++ response2.parsed).reduceOption((parsed1, parsed2) => parsed1 ++ parsed2),
    notFound = (response1.notFound ++ response2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
    notParsable = (response1.notParsable ++ response2.notParsable).reduceOption((notParsable1, notParsable2) => notParsable1.merge(notParsable2)))
}

case class EmailParseResults(parsed: Option[Map[BlobId, EmailParseView]],
                           notFound: Option[EmailParseNotFound],
                           notParsable: Option[EmailParseNotParsable]) {
  def asResponse(accountId: AccountId): EmailParseResponse = EmailParseResponse(accountId, parsed.getOrElse(Map()), notFound, notParsable)
}

case class EmailParseResponse(accountId: AccountId,
                            parsed: Map[BlobId, EmailParseView],
                            notFound: Option[EmailParseNotFound],
                            notParsable: Option[EmailParseNotParsable])

case class EmailParseNotFound(value: Set[UnparsedBlobId]) {
  def merge(other: EmailParseNotFound): EmailParseNotFound = EmailParseNotFound(this.value ++ other.value)
}

case class EmailParseNotParsable(value: Set[UnparsedBlobId]) {
  def merge(other: EmailParseNotParsable): EmailParseNotParsable = EmailParseNotParsable(this.value ++ other.value)
}