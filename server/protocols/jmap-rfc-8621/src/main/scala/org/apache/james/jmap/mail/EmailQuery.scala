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

import com.google.common.hash.Hashing
import org.apache.james.jmap.model.AccountId
import org.apache.james.mailbox.model.{MailboxId, MessageId}

case class EmailQueryRequest(accountId: AccountId, inMailbox: Option[MailboxId])

case class Position(value: Int) extends AnyVal
object Position{
  val zero: Position = Position(0)
}
case class QueryState(value: String) extends AnyVal

object QueryState {
  def forIds(ids: Seq[MessageId]): QueryState = QueryState(
    Hashing.murmur3_32()
      .hashUnencodedChars(ids.map(_.serialize()).mkString(" "))
      .toString)
}

case class EmailQueryResponse(accountId: AccountId,
                              queryState: QueryState,
                              canCalculateChanges: Boolean,
                              ids: Seq[MessageId],
                              position: Position)
