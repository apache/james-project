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

package org.apache.james.jmap.mail

import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.{GetRequest, WithAccountId}
import org.apache.james.mailbox.model.MessageId

case class Thread(id: Id, emailIds: List[MessageId])

case class ThreadGetRequest(accountId: AccountId,
                            ids: List[UnparsedThreadId]) extends WithAccountId with GetRequest {
  override def idCount: Option[Int] = Some(ids.size)
}

case class ThreadGetResponse(accountId: AccountId,
                             state: UuidState,
                             list: List[Thread],
                             notFound: ThreadNotFound)

case class ThreadNotFound(value: Set[UnparsedThreadId]) {
  def merge(other: ThreadNotFound): ThreadNotFound = ThreadNotFound(this.value ++ other.value)
}

case class UnparsedThreadId(id: Id)

case class ThreadChangesRequest(accountId: AccountId,
                                sinceState: UuidState,
                                maxChanged: Option[UnsignedInt]) extends WithAccountId

case class ThreadChangesResponse(accountId: AccountId,
                                 oldState: UuidState,
                                 newState: UuidState,
                                 hasMoreChanges: HasMoreChanges,
                                 created: List[Id],
                                 updated: List[Id],
                                 destroyed: List[Id])