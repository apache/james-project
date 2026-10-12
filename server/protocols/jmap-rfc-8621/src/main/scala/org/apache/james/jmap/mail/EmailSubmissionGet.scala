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

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{AccountId, CanCalculateChanges, Properties, QueryState, UuidState}
import org.apache.james.jmap.method.{GetRequest, WithAccountId}
import play.api.libs.json.JsObject

/**
 * EmailSubmission storage is not implemented (JAMES-3543): EmailSubmission/get, /changes and /query
 * operate over an empty set, so that clients relying on the advertised submission capability get
 * standard answers instead of `unknownMethod`.
 */
object EmailSubmissionGet {
  val allProperties: Properties = Properties("id", "identityId", "emailId", "threadId", "envelope", "sendAt",
    "undoStatus", "deliveryStatus", "dsnBlobIds", "mdnBlobIds")
}

case class EmailSubmissionGetRequest(accountId: AccountId,
                                     ids: Option[List[UnparsedEmailSubmissionId]],
                                     properties: Option[Properties]) extends WithAccountId with GetRequest {
  override def idCount: Option[Int] = ids.map(_.size)

  def unknownProperties: Option[Properties] =
    properties.map(_ -- EmailSubmissionGet.allProperties).filter(!_.isEmpty())
}

case class EmailSubmissionGetResponse(accountId: AccountId,
                                      state: UuidState,
                                      list: List[JsObject],
                                      notFound: List[UnparsedEmailSubmissionId])

case class EmailSubmissionChangesRequest(accountId: AccountId,
                                         sinceState: UuidState,
                                         maxChanges: Option[UnsignedInt]) extends WithAccountId

case class EmailSubmissionChangesResponse(accountId: AccountId,
                                          oldState: UuidState,
                                          newState: UuidState,
                                          hasMoreChanges: HasMoreChanges,
                                          created: List[EmailSubmissionId],
                                          updated: List[EmailSubmissionId],
                                          destroyed: List[EmailSubmissionId])

case class EmailSubmissionQueryRequest(accountId: AccountId,
                                       calculateTotal: Option[Boolean]) extends WithAccountId

case class EmailSubmissionQueryResponse(accountId: AccountId,
                                        queryState: QueryState,
                                        canCalculateChanges: CanCalculateChanges,
                                        position: Position,
                                        ids: List[EmailSubmissionId],
                                        total: Option[UnsignedInt])
